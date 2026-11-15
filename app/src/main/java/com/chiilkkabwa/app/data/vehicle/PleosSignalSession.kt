package com.chiilkkabwa.app.data.vehicle

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import com.chiilkkabwa.app.core.signal.*

internal enum class PleosSensor { BELT, BRAKE, TURN, WIPER, LOW_BEAM, PASSENGER, TRIP_DISTANCE, TRIP_TIME }

internal fun allowPleosSignals(debug: Boolean, optIn: Boolean, source: SignalSource): Boolean =
    debug && optIn && source == SignalSource.EMULATOR

/** 앱 내부 IPC 경계. SDK API를 흉내 낸 공개 인터페이스가 아니다. */
internal interface PleosReadClient {
    fun initialize()
    fun check(sensor: PleosSensor, complete: (Boolean) -> Unit, failed: (Exception) -> Unit)
    fun read(sensor: PleosSensor, complete: (Any?) -> Unit, failed: (Exception) -> Unit)
    fun subscribe(sensor: PleosSensor, updated: (Any?) -> Unit, failed: (Exception) -> Unit): () -> Unit
    fun release()
}

/**
 * 배포 AAR는 문서의 VehicleException을 난독화해 공개 이름으로 import할 수 없다.
 * 공개 Exception/SecurityException만 사용한다. sources에서 확인한 정확한 오류명만 분류하고
 * 숫자/임의 문자열/내부 타입은 추측하지 않는다. 모든 미분류 오류도 값을 폐기한다.
 */
internal fun pleosFailureStatus(error: Exception): SignalStatus {
    val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
    if (causes.any { it is SecurityException }) return SignalStatus.PERMISSION_DENIED
    return when (causes.lastOrNull()?.message) {
        "ERROR_SECURITY_PERMISSION" -> SignalStatus.PERMISSION_DENIED
        "ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_AREA" -> SignalStatus.UNSUPPORTED
        "ERROR_SERVICE_CONNECTION_FAILURE", "ERROR_INTERNAL_SERVICE", "Service connection failed" -> SignalStatus.DISCONNECTED
        else -> SignalStatus.UNKNOWN
    }
}

/** Binder 콜백, tick, start/stop을 동일 잠금으로 직렬화한다. 세션별 토큰으로 재시작을 격리한다. */
internal class PleosSignalSession(
    private val createClient: () -> PleosReadClient,
    private val source: SignalSource,
    private val enabled: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = now,
) : VehicleSource {
    private class Channel {
        var supported = false
        var blocked = false
        var revision = 0L
        var pending: Request? = null
        var remove: (() -> Unit)? = null
    }
    private class Request(val startedAt: Long, val revision: Long) {
        var completed = false
        var expired = false
    }
    private class Session(val client: PleosReadClient, var sink: SnapshotSink?) {
        val channels = PleosSensor.entries.associateWith { Channel() }
        var closed = false
        var outstanding = 0
        var released = false
    }
    private var active: Session? = null
    // SDK 공개 API에는 취소/연결 완료 리스너가 없다. 미응답 상태에서 새 client를 누적하지 않는다.
    private var draining: Session? = null

    @Synchronized override fun start(sink: SnapshotSink) {
        stop()
        reset(sink, if (enabled) SignalStatus.UNKNOWN else SignalStatus.UNSUPPORTED)
        if (!enabled) return
        if (draining != null) {
            reset(sink, SignalStatus.DISCONNECTED)
            return
        }
        val session = try { Session(createClient(), sink) } catch (e: Exception) {
            reset(sink, pleosFailureStatus(e)); return
        }
        active = session
        try {
            session.client.initialize()
            // 호출 순서 고정. 각 capability 성공 전 해당 신호의 read/register 금지.
            PleosSensor.entries.forEach { sensor ->
                request(session, sensor, { ok, fail -> session.client.check(sensor, ok, fail) }) { supported: Boolean ->
                    val channel = session.channels.getValue(sensor)
                    channel.supported = supported
                    if (!supported) {
                        channel.blocked = true
                        failed(session, sensor, SignalStatus.UNSUPPORTED)
                    } else {
                        subscribe(session, sensor)
                        if (!channel.blocked) read(session, sensor)
                    }
                }
            }
        } catch (e: Exception) {
            reset(sink, pleosFailureStatus(e))
            close(session)
            active = null
        }
    }

    private fun subscribe(session: Session, sensor: PleosSensor) {
        val channel = session.channels.getValue(sensor)
        try {
            channel.remove = session.client.subscribe(sensor, { value ->
                synchronized(this) {
                    if (accepts(session, channel)) {
                        channel.revision++
                        observed(session, sensor, value)
                    }
                }
            }, { error ->
                synchronized(this) {
                    if (accepts(session, channel)) {
                        channel.revision++
                        channel.blocked = true
                        failed(session, sensor, pleosFailureStatus(error))
                    }
                }
            })
        } catch (e: Exception) {
            channel.blocked = true
            failed(session, sensor, pleosFailureStatus(e))
        }
    }

    private fun read(session: Session, sensor: PleosSensor) {
        request(session, sensor, { ok, fail -> session.client.read(sensor, ok, fail) }) { value: Any? ->
            observed(session, sensor, value)
        }
    }

    /** 진행 중 요청은 stop 후에도 응답 배출만 수행하고 release를 지연한다. */
    private fun <T> request(
        session: Session, sensor: PleosSensor,
        call: ((T) -> Unit, (Exception) -> Unit) -> Unit,
        complete: (T) -> Unit,
    ) {
        val channel = session.channels.getValue(sensor)
        val request = Request(elapsed(), channel.revision)
        channel.pending = request
        session.outstanding++
        fun finish(action: () -> Unit) = synchronized(this) {
            if (!request.completed) {
                request.completed = true
                if (channel.pending === request) channel.pending = null
                try {
                    if (accepts(session, channel) && !request.expired && request.revision == channel.revision) action()
                } finally {
                    session.outstanding--
                    releaseIfDrained(session)
                }
            }
        }
        val failed: (Exception) -> Unit = { error -> finish {
            channel.blocked = true
            failed(session, sensor, pleosFailureStatus(error))
        } }
        try { call({ value -> finish { complete(value) } }, failed) } catch (e: Exception) { failed(e) }
    }

    @Synchronized fun tick() {
        val session = active ?: return
        session.channels.forEach { (sensor, channel) ->
            val pending = channel.pending
            if (pending != null && !pending.expired && elapsed() - pending.startedAt >= RESPONSE_TIMEOUT_MS) {
                pending.expired = true
                channel.blocked = true
                failed(session, sensor, SignalStatus.DISCONNECTED)
            } else if (pending == null && channel.supported && !channel.blocked) {
                read(session, sensor)
            }
        }
    }

    private fun accepts(session: Session, channel: Channel) =
        active === session && !session.closed && !channel.blocked

    private fun observed(session: Session, sensor: PleosSensor, value: Any?) {
        val at = now()
        fun <T> signal(value: T?): Signal<T> =
            if (value == null) Signal.failed(SignalStatus.UNKNOWN, source, at) else Signal.ok(value, source, at)
        session.sink?.update { snapshot -> when (sensor) {
            PleosSensor.BELT -> snapshot.copy(driverBeltBuckled = signal(value as? Boolean))
            PleosSensor.BRAKE -> snapshot.copy(brakePressed = signal(value as? Boolean))
            PleosSensor.WIPER -> snapshot.copy(wiperOn = signal(pleosWiperOn(value)))
            PleosSensor.LOW_BEAM -> snapshot.copy(lowBeamOn = signal(pleosLowBeamOn(value)))
            PleosSensor.PASSENGER -> snapshot.copy(passengerSeated = signal(value as? Boolean))
            PleosSensor.TRIP_DISTANCE -> snapshot.copy(tripDistanceKm = signal(pleosTripKm(value)))
            PleosSensor.TRIP_TIME -> snapshot.copy(tripTimeSec = signal(pleosTripSeconds(value)))
            PleosSensor.TURN -> snapshot.copy(turnSignal = signal(when (value as? TurnSignalValue) {
                TurnSignalValue.OFF -> TurnSignalState.NONE
                TurnSignalValue.LEFT -> TurnSignalState.LEFT
                TurnSignalValue.RIGHT -> TurnSignalState.RIGHT
                null -> null // SDK 2.3.2에는 HAZARD enum이 없다.
            }))
        } }
    }

    private fun failed(session: Session, sensor: PleosSensor, status: SignalStatus) {
        val at = now()
        session.sink?.update { snapshot -> when (sensor) {
            PleosSensor.BELT -> snapshot.copy(driverBeltBuckled = Signal.failed(status, source, at))
            PleosSensor.BRAKE -> snapshot.copy(brakePressed = Signal.failed(status, source, at))
            PleosSensor.TURN -> snapshot.copy(turnSignal = Signal.failed(status, source, at))
            PleosSensor.WIPER -> snapshot.copy(wiperOn = Signal.failed(status, source, at))
            PleosSensor.LOW_BEAM -> snapshot.copy(lowBeamOn = Signal.failed(status, source, at))
            PleosSensor.PASSENGER -> snapshot.copy(passengerSeated = Signal.failed(status, source, at))
            PleosSensor.TRIP_DISTANCE -> snapshot.copy(tripDistanceKm = Signal.failed(status, source, at))
            PleosSensor.TRIP_TIME -> snapshot.copy(tripTimeSec = Signal.failed(status, source, at))
        } }
    }

    @Synchronized override fun stop() {
        val session = active ?: return
        active = null
        session.sink?.let { reset(it, SignalStatus.DISCONNECTED) }
        close(session)
    }

    private fun close(session: Session) {
        session.closed = true
        session.sink = null // 미응답 IPC가 ViewModel/Repository를 보유하지 않게 한다.
        draining = session
        session.channels.values.forEach { channel ->
            // 하나의 unregister 실패가 다른 listener 해제나 release를 막지 않는다.
            try { channel.remove?.invoke() } catch (_: Exception) { /* release도 전체 listener를 정리 */ }
            channel.remove = null
        }
        releaseIfDrained(session)
    }

    private fun releaseIfDrained(session: Session) {
        if (session.closed && session.outstanding == 0 && !session.released) {
            session.released = true
            try { session.client.release() } catch (_: Exception) { /* 재사용하지 않는다 */ }
            if (draining === session) draining = null
        }
    }

    private fun reset(sink: SnapshotSink, status: SignalStatus) {
        val at = now()
        sink.update { it.copy(
            driverBeltBuckled = Signal.failed(status, source, at),
            brakePressed = Signal.failed(status, source, at),
            turnSignal = Signal.failed(status, source, at),
            // 주차 게이트는 AAOS 소유. 연결한 채널은 초기/중단 상태를 동일하게 폐기한다.
            passengerSeated = Signal.failed(status, source, at),
            wiperOn = Signal.failed(status, source, at),
            lowBeamOn = Signal.failed(status, source, at),
            // SDK 전륜각은 핸들 회전각 계약과 다름. CarInfo 치수 public API도 없음.
            // 이 두 UNSUPPORTED는 현재 어댑터 매핑 불가이며 차량 capability 판정이 아니다.
            steeringDeg = Signal.failed(SignalStatus.UNSUPPORTED, source, at),
            tripDistanceKm = Signal.failed(status, source, at),
            tripTimeSec = Signal.failed(status, source, at),
            carDimensions = Signal.failed(SignalStatus.UNSUPPORTED, source, at),
        ) }
    }

    companion object { private const val RESPONSE_TIMEOUT_MS = 5_000L }
}

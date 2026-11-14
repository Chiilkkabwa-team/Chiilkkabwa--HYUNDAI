package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.signal.CarDimensions
import com.chiilkkabwa.app.core.signal.Gear
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.TurnSignalState
import com.chiilkkabwa.app.core.signal.VehicleSnapshot

/**
 * 모의 차량. SDK·에뮬레이터 없이 흐름 전체를 시연·테스트할 때 사용(디버그 패널에서 조작).
 * 모든 값에 SignalSource.MOCK가 붙어 화면·기록에 '모의'로 표시된다(T09).
 * 특정 필드만 모의로 덮어쓰는 '혼합 모드'도 지원: overrideOnly=true면 start 시 초기값을 쓰지 않는다.
 */
class MockVehicleSource(
    private val overrideOnly: Boolean = false,
    private val speedNowNanos: () -> Long = android.os.SystemClock::elapsedRealtimeNanos,
    private val speedSessionId: () -> String = { java.util.UUID.randomUUID().toString() },
    private val onSpeedResult: (com.chiilkkabwa.app.core.event.SpeedDetectionResult) -> Unit = {},
) : VehicleSource {
    private val speedBridge = com.chiilkkabwa.app.core.event.SpeedEventBridge()
    private var speedId = ""
    private var speedStarted = 0L
    private var sink: SnapshotSink? = null
    private fun now() = System.currentTimeMillis()
    private fun <T> ok(v: T) = Signal.ok(v, SignalSource.MOCK, now())

    override fun start(sink: SnapshotSink) {
        this.sink = sink
        speedId = speedSessionId(); speedStarted = speedNowNanos(); speedBridge.start(speedId)
        if (overrideOnly) return
        sink.update {
            VehicleSnapshot(
                gear = ok(Gear.PARK), parkingBrakeOn = ok(true), uxRestricted = ok(false),
                driverBeltBuckled = ok(false), brakePressed = ok(false), brakePedalPct = ok(0f), accelPedalPct = ok(0f),
                turnSignal = ok(TurnSignalState.NONE), wiperOn = ok(false), lowBeamOn = ok(false), steeringDeg = ok(0f),
                passengerSeated = ok(true), tripDistanceKm = ok(1200.0f), tripTimeSec = ok(36_000L),
                carDimensions = ok(CarDimensions.COMPACT_SEDAN_DEFAULT),
                speed = com.chiilkkabwa.app.core.signal.VehicleSpeedObservation(receivedAtNanos = speedStarted, source = SignalSource.MOCK, sessionId = speedId),
            )
        }
    }

    override fun stop() { sink = null; speedBridge.stop() }

    /** Explicit Mock only; no inferred speed from gear/pedals and no UI control added. */
    fun setSpeedMps(mps: Float) {
        val target = sink ?: return
        val now = speedNowNanos()
        val reading = decodeSpeed(mps, SignalStatus.OK, now, now, SignalSource.MOCK, speedId, speedStarted)
        target.update { it.copy(speed = reading) }
        speedBridge.accept(reading, now)?.let(onSpeedResult)
    }

    // ── 디버그 패널용 조작 함수 ──
    fun setGear(g: Gear) = sink?.update { it.copy(gear = ok(g), uxRestricted = ok(g != Gear.PARK && g != Gear.NEUTRAL)) }
    fun setParkingBrake(on: Boolean) = sink?.update { it.copy(parkingBrakeOn = ok(on)) }
    fun setUxRestricted(on: Boolean) = sink?.update { it.copy(uxRestricted = ok(on)) }
    fun setBelt(on: Boolean) = sink?.update { it.copy(driverBeltBuckled = ok(on)) }
    fun setBrake(pressed: Boolean) = sink?.update { it.copy(brakePressed = ok(pressed), brakePedalPct = ok(if (pressed) 80f else 0f)) }
    fun setAccelPct(p: Float) = sink?.update { it.copy(accelPedalPct = ok(p)) }
    fun setBrakePct(p: Float) = sink?.update { it.copy(brakePedalPct = ok(p), brakePressed = ok(p > 5f)) }
    fun setTurn(t: TurnSignalState) = sink?.update { it.copy(turnSignal = ok(t)) }
    fun setWiper(on: Boolean) = sink?.update { it.copy(wiperOn = ok(on)) }
    fun setLowBeam(on: Boolean) = sink?.update { it.copy(lowBeamOn = ok(on)) }
    fun setSteering(deg: Float) = sink?.update { it.copy(steeringDeg = ok(deg)) }
    /** 주행을 흉내: 트립 거리·시간을 더한다 */
    fun addTrip(km: Float, sec: Long) = sink?.update {
        it.copy(
            tripDistanceKm = ok((it.tripDistanceKm.value ?: 0f) + km),
            tripTimeSec = ok((it.tripTimeSec.value ?: 0L) + sec),
        )
    }
    /** 예외 시나리오 재현(T03·T08) */
    fun breakSignal(status: SignalStatus) = sink?.update { it.copy(brakePressed = Signal.failed(status, SignalSource.MOCK, now())) }
}

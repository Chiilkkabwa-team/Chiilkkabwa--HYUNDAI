package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.event.SpeedDetectionResult
import com.chiilkkabwa.app.core.event.SpeedEventBridge
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSpeedObservation

internal enum class SpeedReadAccess { GRANTED, DENIED, MISSING_PERMISSION }
internal interface SpeedReadClient {
    fun access(): SpeedReadAccess
    fun connect(ready: () -> Unit, disconnected: (SignalStatus) -> Unit)
    fun isSupported(): Boolean
    fun subscribe(value: (Any?, SignalStatus, Long) -> Unit, error: (SignalStatus) -> Unit): Boolean
    fun close()
}

internal const val SPEED_MAX_AGE_NANOS = 1_000_000_000L

/** Strict Float decoder; timestamps are preserved even when invalid. No wall-clock fallback. */
internal fun decodeSpeed(raw: Any?, status: SignalStatus, timestamp: Long, received: Long,
    source: SignalSource, sessionId: String, started: Long): VehicleSpeedObservation {
    val value = if (status == SignalStatus.OK) raw as? Float else null
    val decoded = when {
        status != SignalStatus.OK -> status
        value == null || !value.isFinite() -> SignalStatus.UNKNOWN
        timestamp < 0 || received < 0 || timestamp > received -> SignalStatus.UNKNOWN
        timestamp < started || received - timestamp > SPEED_MAX_AGE_NANOS -> SignalStatus.STALE
        else -> SignalStatus.OK
    }
    return VehicleSpeedObservation(value, timestamp, received, source, decoded, sessionId)
}

/** Serialized, testable subscription lifecycle. Revokes callbacks before resource close. */
internal class SpeedSignalSession(
    private val factory: () -> SpeedReadClient,
    private val source: SignalSource,
    private val enabled: Boolean,
    private val nowNanos: () -> Long,
    private val newId: () -> String,
    private val onResult: (SpeedDetectionResult) -> Unit = {},
) {
    private var generation = 0L
    private var live = false
    private var terminal = false
    private var started = 0L
    private var client: SpeedReadClient? = null
    private var publisher: ((VehicleSpeedObservation) -> Unit)? = null
    private var observation = VehicleSpeedObservation(source = source)
    private val bridge = SpeedEventBridge()

    @Synchronized fun start(publish: (VehicleSpeedObservation) -> Unit) {
        stop()
        val token = ++generation
        live = enabled; terminal = false; started = nowNanos(); publisher = publish
        observation = VehicleSpeedObservation(receivedAtNanos = started, source = source, sessionId = newId())
        if (enabled) bridge.start(observation.sessionId)
        emit(observation)
        if (!enabled) return
        try {
            val c = factory(); client = c
            if (!checkAccess(c)) return
            var subscribed = false
            var registering = false
            var pending: Triple<Any?, SignalStatus, Long>? = null
            c.connect(ready = {
                synchronized(this) {
                    if (active(token) && !subscribed && !registering) {
                        try {
                            if (checkAccess(c)) {
                                if (!c.isSupported()) fail(SignalStatus.UNSUPPORTED)
                                else {
                                    registering = true
                                    val accepted = c.subscribe(value = { raw, status, timestamp ->
                                        synchronized(this) {
                                            if (active(token)) {
                                                if (registering) pending = Triple(raw, status, timestamp)
                                                else if (subscribed) receive(raw, status, timestamp)
                                            }
                                        }
                                    }, error = { status -> synchronized(this) { if (active(token)) fail(status) } })
                                    registering = false
                                    if (active(token)) {
                                        if (!accepted) fail(SignalStatus.UNKNOWN)
                                        else {
                                            subscribed = true
                                            pending?.let { receive(it.first, it.second, it.third) }
                                            pending = null
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) { if (active(token)) fail(exceptionStatus(e)) }
                    }
                }
            }, disconnected = { status -> synchronized(this) { if (active(token)) fail(status) } })
        } catch (e: Exception) { if (active(token)) fail(exceptionStatus(e)) }
    }

    val needsTick: Boolean @Synchronized get() = live && !terminal

    @Synchronized fun tick() {
        if (!live || terminal) return
        val now = nowNanos()
        val last = observation.timestampNanos ?: started
        if ((now < started || now < observation.receivedAtNanos) && observation.status != SignalStatus.UNKNOWN) {
            emit(observation.copy(rawMps = null, status = SignalStatus.UNKNOWN))
        } else if (now >= last && now - last > SPEED_MAX_AGE_NANOS &&
            observation.status != SignalStatus.STALE) {
            emit(observation.copy(status = SignalStatus.STALE))
        }
    }

    @Synchronized fun stop() {
        generation++; live = false; terminal = true
        closeClient(); bridge.stop()
        publisher?.invoke(observation.disconnected())
        publisher = null
    }

    private fun active(token: Long) = live && !terminal && generation == token
    private fun checkAccess(c: SpeedReadClient): Boolean = when (c.access()) {
        SpeedReadAccess.GRANTED -> true
        SpeedReadAccess.DENIED -> { fail(SignalStatus.PERMISSION_DENIED); false }
        SpeedReadAccess.MISSING_PERMISSION -> { fail(SignalStatus.UNSUPPORTED); false }
    }
    private fun receive(raw: Any?, status: SignalStatus, timestamp: Long) {
        emit(decodeSpeed(raw, status, timestamp, nowNanos(), source, observation.sessionId, started))
    }
    private fun emit(value: VehicleSpeedObservation) {
        observation = value
        publisher?.invoke(value)
        bridge.accept(value, nowNanos())?.let(onResult)
    }
    private fun fail(status: SignalStatus) {
        generation++; terminal = true
        closeClient()
        emit(observation.copy(rawMps = null, timestampNanos = null, status = status))
    }
    private fun closeClient() {
        val retired = client; client = null
        try { retired?.close() } catch (_: Exception) { /* retired generation cannot publish */ }
    }
    private fun exceptionStatus(e: Exception) =
        if (e is SecurityException) SignalStatus.PERMISSION_DENIED else SignalStatus.DISCONNECTED
}


package com.chiilkkabwa.app.core.event

import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSpeedObservation

/** One active subscription session. Callback output is not connected to voice, UI or persistent storage. */
class SpeedEventBridge(private val config: SpeedEventConfig = SpeedEventConfig()) {
    private var engine: SpeedEventEngine? = null
    private var highWaterNowNanos: Long? = null
    private var highWaterReceiptNanos: Long? = null
    fun start(sessionId: String) {
        engine = SpeedEventEngine(sessionId, config); highWaterNowNanos = null; highWaterReceiptNanos = null
    }
    fun stop() { engine = null; highWaterNowNanos = null; highWaterReceiptNanos = null }

    fun accept(reading: VehicleSpeedObservation, nowNanos: Long): SpeedDetectionResult? {
        val current = engine ?: return null
        if (reading.sessionId != current.sessionId) return null
        val timestamp = reading.timestampNanos
        val exactTimeValid = timestamp != null && timestamp >= 0 &&
            timestamp <= reading.receivedAtNanos && reading.receivedAtNanos <= nowNanos &&
            nowNanos - timestamp <= config.maxSampleAgeMs * 1_000_000L
        val sample = SpeedSample(
            reading.rawMps?.toDouble(), (timestamp ?: 0) / 1_000_000,
            SpeedUnit.METERS_PER_SECOND, reading.source,
            if (reading.status == SignalStatus.OK && !exactTimeValid) SignalStatus.UNKNOWN else reading.status,
        )
        val clockReversed = nowNanos < 0 || highWaterNowNanos?.let { nowNanos < it } == true ||
            highWaterReceiptNanos?.let { reading.receivedAtNanos < it } == true
        if (clockReversed) {
            current.reset()
            return SpeedDetectionResult.Rejected(SpeedRejection.INVALID_TIMESTAMP, sample)
        }
        highWaterNowNanos = nowNanos
        if (reading.receivedAtNanos in 0..nowNanos) highWaterReceiptNanos = reading.receivedAtNanos
        // The existing engine explicitly rejects negative values and resets continuity. Never abs().
        return current.update(sample, nowNanos / 1_000_000)
    }
}

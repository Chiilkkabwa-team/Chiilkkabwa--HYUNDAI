package com.chiilkkabwa.app.core.event

import com.chiilkkabwa.app.core.signal.VehicleSpeedObservation
import kotlin.math.abs

/** EXPERIMENTAL offline comparison only. No caller in the application/runtime pipeline. */
enum class QualityMode { WINDOW_ONLY, SUSTAINED_QUANTIZATION }
enum class QualityReason {
    WARMING_UP, REJECTED_INPUT, GAP_RESET, BELOW_THRESHOLD, QUANTIZATION_SENSITIVE,
    NOT_SUSTAINED, CANDIDATE, ONGOING_EPISODE, COOLDOWN,
}

/** Deliberately not a SpeedChangeEvent. This is not suitable for coaching or recorded driving claims. */
data class ShadowSpeedCandidate(
    val id: String,
    val type: SpeedEventType,
    val observations: List<VehicleSpeedObservation>,
    val mode: QualityMode,
    val assumedQuantumMps: Double,
    val accelerationMps2: Double,
)

data class SpeedQualityResult(
    val observation: VehicleSpeedObservation,
    val legacy: SpeedDetectionResult,
    val reason: QualityReason,
    val window: List<VehicleSpeedObservation>,
    val windowAccelerationMps2: Double? = null,
    val candidate: ShadowSpeedCandidate? = null,
)

/**
 * Fixed experimental coverage: >=600ms, >=5 samples, each gap <=300ms, bounded to 32 samples.
 * Quantum is a sensitivity assumption, NOT a certified sensor error bound. Both halves must exceed
 * the original thresholds after subtracting one assumed quantum from their directional delta.
 * Uses exact elapsed ns for quality; the independent legacy bridge retains its original ms logic.
 */
class SpeedQualityShadow(
    private val mode: QualityMode = QualityMode.SUSTAINED_QUANTIZATION,
    private val assumedQuantumMps: Double = 1.0 / 3.6,
) {
    init { require(assumedQuantumMps.isFinite() && assumedQuantumMps >= 0) }
    private val config = SpeedEventConfig()
    private val legacy = SpeedEventBridge(config)
    private var session: String? = null
    private val samples = ArrayList<VehicleSpeedObservation>()
    private var episode: SpeedEventType? = null
    private var sequence = 0L
    private val emitted = mutableMapOf<Pair<com.chiilkkabwa.app.core.signal.SignalSource, SpeedEventType>, Long>()

    fun start(sessionId: String) {
        require(sessionId.isNotBlank())
        session = sessionId; samples.clear(); episode = null; sequence = 0; emitted.clear()
        legacy.start(sessionId)
    }
    fun stop() { session = null; samples.clear(); episode = null; emitted.clear(); legacy.stop() }

    fun accept(reading: VehicleSpeedObservation, nowNanos: Long): SpeedQualityResult? {
        if (session == null || reading.sessionId != session) return null
        val base = legacy.accept(reading, nowNanos) ?: return null
        fun result(reason: QualityReason, acceleration: Double? = null, candidate: ShadowSpeedCandidate? = null) =
            SpeedQualityResult(reading, base, reason, samples.toList(), acceleration, candidate)
        if (base is SpeedDetectionResult.Rejected) {
            samples.clear(); episode = null
            return result(QualityReason.REJECTED_INPUT)
        }
        val previous = samples.lastOrNull()
        val sourceChanged = previous != null && previous.source != reading.source
        val gap = previous != null && reading.timestampNanos!! - previous.timestampNanos!! > 300_000_000L
        if (sourceChanged || gap || base is SpeedDetectionResult.Baseline) {
            samples.clear(); episode = null
        }
        samples.add(reading)
        val currentNs = reading.timestampNanos!!
        // Newest anchor still covering 600ms AND five samples (200ms callbacks need 800ms).
        while (samples.size > 5 && currentNs - samples[1].timestampNanos!! >= 600_000_000L) samples.removeAt(0)
        while (samples.size > 32) samples.removeAt(0)
        val span = currentNs - samples.first().timestampNanos!!
        if (samples.size < 5 || span < 600_000_000L || span > 1_000_000_000L) {
            return result(if (gap) QualityReason.GAP_RESET else QualityReason.WARMING_UP)
        }
        fun acceleration(a: VehicleSpeedObservation, b: VehicleSpeedObservation, allowance: Double = 0.0, sign: Double = 1.0): Double =
            (sign * (b.rawMps!!.toDouble() - a.rawMps!!.toDouble()) - allowance) /
                ((b.timestampNanos!! - a.timestampNanos!!) / 1e9)
        val a = acceleration(samples.first(), reading)
        val type = if (a >= 0) SpeedEventType.RAPID_SPEED_INCREASE else SpeedEventType.RAPID_SPEED_DECREASE
        val threshold = if (a >= 0) config.increaseThresholdMps2 else config.decreaseThresholdMps2
        if (episode != null && (episode != type || abs(a) < threshold * config.releaseRatio)) episode = null
        if (abs(a) < threshold) return result(QualityReason.BELOW_THRESHOLD, a)
        if (mode == QualityMode.SUSTAINED_QUANTIZATION) {
            val sign = if (a >= 0) 1.0 else -1.0
            if (acceleration(samples.first(), reading, assumedQuantumMps, sign) < threshold)
                return result(QualityReason.QUANTIZATION_SENSITIVE, a)
            val midpoint = samples.drop(1).dropLast(1).minBy { abs((it.timestampNanos!! - samples.first().timestampNanos!!) - span / 2) }
            if (midpoint.timestampNanos!! - samples.first().timestampNanos!! < 200_000_000L ||
                currentNs - midpoint.timestampNanos!! < 200_000_000L ||
                acceleration(samples.first(), midpoint, assumedQuantumMps, sign) < threshold ||
                acceleration(midpoint, reading, assumedQuantumMps, sign) < threshold)
                return result(QualityReason.NOT_SUSTAINED, a)
        }
        if (episode == type) return result(QualityReason.ONGOING_EPISODE, a)
        episode = type
        val key = reading.source to type
        if (emitted[key]?.let { currentNs - it < config.cooldownMs * 1_000_000 } == true)
            return result(QualityReason.COOLDOWN, a)
        emitted[key] = currentNs
        val candidate = ShadowSpeedCandidate("$session:quality-shadow:${++sequence}", type, samples.toList(), mode, assumedQuantumMps, a)
        return result(QualityReason.CANDIDATE, a, candidate)
    }
}

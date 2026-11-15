package com.chiilkkabwa.app.core.event

import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import kotlin.math.abs

/**
 * Android/SDK/IO 비의존 스트리밍 엔진. 세션마다 한 인스턴스를 만들고 한 실행 문맥에서 순서대로 호출한다.
 * 표본 1개, 현재 구간, 유형·출처별 마지막 발행 시각만 보유하므로 입력 길이에 무관한 메모리를 쓴다.
 */
class SpeedEventEngine(val sessionId: String, val config: SpeedEventConfig = SpeedEventConfig()) {
    init { require(sessionId.isNotBlank()) }
    private var previous: SpeedSample? = null
    private var highWaterTimestamp: Long? = null
    private var highWaterNow: Long? = null
    private var episode: SpeedEventType? = null
    private var sequence = 0L
    private val lastEmitted = mutableMapOf<Pair<SignalSource, SpeedEventType>, Long>()

    /** 연결/생명주기 단절 시 호출. 같은 세션의 중복 방지를 위해 쿨다운·ID·시각 상한은 유지한다. */
    fun reset() {
        previous = null
        episode = null
    }

    fun update(sample: SpeedSample?, nowMs: Long): SpeedDetectionResult {
        if (nowMs < 0) return reject(SpeedRejection.INVALID_TIMESTAMP, sample)
        val processingClockReversed = highWaterNow?.let { nowMs < it } == true
        if (!processingClockReversed) highWaterNow = nowMs
        if (sample == null) return reject(SpeedRejection.MISSING_SAMPLE, null)
        if (sample.status != SignalStatus.OK) return reject(SpeedRejection.INVALID_STATUS, sample)
        val raw = sample.speed ?: return reject(SpeedRejection.MISSING_VALUE, sample)
        if (!raw.isFinite()) return reject(SpeedRejection.NONFINITE_SPEED, sample)
        val currentMps = metersPerSecond(sample)
        if (currentMps < 0 || currentMps > config.maxSpeedMps) return reject(SpeedRejection.SPEED_OUT_OF_RANGE, sample)
        if (sample.timestampMs < 0) return reject(SpeedRejection.INVALID_TIMESTAMP, sample)
        if (sample.timestampMs > nowMs) return reject(SpeedRejection.FUTURE_SAMPLE, sample)
        if (nowMs - sample.timestampMs > config.maxSampleAgeMs) return reject(SpeedRejection.STALE_SAMPLE, sample)
        if (highWaterTimestamp?.let { sample.timestampMs <= it } == true) {
            return reject(SpeedRejection.NON_INCREASING_TIME, sample)
        }
        if (processingClockReversed) return reject(SpeedRejection.INVALID_TIMESTAMP, sample)
        highWaterTimestamp = sample.timestampMs
        val anchor = previous ?: return baseline(sample, BaselineReason.FIRST_SAMPLE)
        if (anchor.source != sample.source) return baseline(sample, BaselineReason.SOURCE_CHANGED)
        val interval = sample.timestampMs - anchor.timestampMs
        if (interval > config.maxIntervalMs) return baseline(sample, BaselineReason.GAP_TOO_LARGE)
        if (interval < config.minIntervalMs) {
            // 유효한 고빈도 표본은 버리되 기존 anchor를 유지해 최소 간격까지 누적한다.
            return SpeedDetectionResult.Rejected(SpeedRejection.INTERVAL_TOO_SHORT, sample)
        }
        val previousMps = metersPerSecond(anchor)
        val delta = currentMps - previousMps
        val seconds = interval / 1000.0
        val acceleration = delta / seconds
        val tolerance = ((Math.ulp(previousMps) + Math.ulp(currentMps)) / seconds + Math.ulp(acceleration)) * 4
        val precisionLimit = minOf(config.increaseThresholdMps2, config.decreaseThresholdMps2) * (1 - config.releaseRatio)
        if (!acceleration.isFinite() || !tolerance.isFinite() || tolerance >= precisionLimit ||
            abs(acceleration) - tolerance > config.maxAbsAccelerationMps2) {
            return reject(SpeedRejection.IMPLAUSIBLE_CHANGE, sample)
        }
        previous = sample
        val evidence = SpeedChangeEvidence(anchor, sample, previousMps, currentMps, interval, delta, acceleration, tolerance)

        // 회복 임계값을 따로 둬 경계 근처 진동을 새 사건으로 과다 발행하지 않는다.
        when (episode) {
            SpeedEventType.RAPID_SPEED_INCREASE -> if (acceleration - tolerance <= config.increaseThresholdMps2 * config.releaseRatio) episode = null
            SpeedEventType.RAPID_SPEED_DECREASE -> if (acceleration + tolerance >= -config.decreaseThresholdMps2 * config.releaseRatio) episode = null
            null -> Unit
        }
        val candidate = when {
            acceleration + tolerance >= config.increaseThresholdMps2 -> SpeedEventType.RAPID_SPEED_INCREASE
            acceleration - tolerance <= -config.decreaseThresholdMps2 -> SpeedEventType.RAPID_SPEED_DECREASE
            else -> null
        } ?: return SpeedDetectionResult.Evaluated(evidence, null)
        if (episode == candidate) return SpeedDetectionResult.Evaluated(evidence, null, EventSuppression.ONGOING_EPISODE)
        episode = candidate // 쿨다운으로 억제된 구간도 회복하기 전에는 뒤늦게 발행하지 않는다.
        val key = sample.source to candidate
        if (lastEmitted[key]?.let { sample.timestampMs - it < config.cooldownMs } == true) {
            return SpeedDetectionResult.Evaluated(evidence, null, EventSuppression.COOLDOWN)
        }
        lastEmitted[key] = sample.timestampMs
        val threshold = when (candidate) {
            SpeedEventType.RAPID_SPEED_INCREASE -> config.increaseThresholdMps2
            SpeedEventType.RAPID_SPEED_DECREASE -> -config.decreaseThresholdMps2
        }
        val event = SpeedChangeEvent("$sessionId:speed:${++sequence}", sessionId, candidate, evidence, threshold)
        return SpeedDetectionResult.Evaluated(evidence, event)
    }

    private fun reject(reason: SpeedRejection, sample: SpeedSample?): SpeedDetectionResult {
        reset()
        return SpeedDetectionResult.Rejected(reason, sample)
    }
    private fun baseline(sample: SpeedSample, reason: BaselineReason): SpeedDetectionResult {
        previous = sample
        episode = null
        return SpeedDetectionResult.Baseline(sample, reason)
    }
    private fun metersPerSecond(sample: SpeedSample): Double = when (sample.unit) {
        SpeedUnit.METERS_PER_SECOND -> sample.speed!!
        SpeedUnit.KILOMETERS_PER_HOUR -> sample.speed!! / 3.6
    }
}

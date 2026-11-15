package com.chiilkkabwa.app.core.event

import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus

/** 단위는 호출자가 명시한다. 음수 속도를 후진으로 해석하거나 절댓값으로 바꾸지 않는다. */
enum class SpeedUnit { METERS_PER_SECOND, KILOMETERS_PER_HOUR }

/**
 * timestampMs와 update의 nowMs는 동일한, 세션 내 단조 증가하는 밀리초 시계여야 한다.
 * 상태 OK는 공급자가 지원·권한·유효성을 확인했다는 입력 계약이며 엔진의 실측 인증이 아니다.
 */
data class SpeedSample(
    val speed: Double?,
    val timestampMs: Long,
    val unit: SpeedUnit,
    val source: SignalSource,
    val status: SignalStatus,
) {
    companion object {
        /** 기존 Signal 계약을 변경하지 않는 명시적 변환. receivedAtMs의 시계와 nowMs를 맞춘다. */
        fun fromSignal(signal: Signal<Number>, unit: SpeedUnit): SpeedSample = SpeedSample(
            signal.value?.toDouble(), signal.receivedAtMs, unit, signal.source, signal.status,
        )
    }
}

/** 초기 개발/시험 설정. 안전·법규·실차 임계값으로 인증된 값이 아니다. */
data class SpeedEventConfig(
    val increaseThresholdMps2: Double = 2.5,
    val decreaseThresholdMps2: Double = 12.0 / 3.6,
    val minIntervalMs: Long = 50,
    val maxIntervalMs: Long = 2_000,
    val maxSampleAgeMs: Long = 1_000,
    val maxSpeedMps: Double = 100.0,
    val maxAbsAccelerationMps2: Double = 15.0,
    val cooldownMs: Long = 6_000,
    val releaseRatio: Double = 0.6,
) {
    init {
        require(increaseThresholdMps2.isFinite() && increaseThresholdMps2 > 0)
        require(decreaseThresholdMps2.isFinite() && decreaseThresholdMps2 > 0)
        require(minIntervalMs > 0 && maxIntervalMs >= minIntervalMs)
        require(maxSampleAgeMs >= 0 && cooldownMs >= 0)
        require(maxSpeedMps.isFinite() && maxSpeedMps > 0)
        require(maxAbsAccelerationMps2.isFinite() &&
            maxAbsAccelerationMps2 >= maxOf(increaseThresholdMps2, decreaseThresholdMps2))
        require(releaseRatio.isFinite() && releaseRatio > 0 && releaseRatio < 1)
    }
}

/** 속도 변화 관측만 표현한다. 브레이크/가속 페달 조작, 충돌, 주변 차 위험을 뜻하지 않는다. */
enum class SpeedEventType { RAPID_SPEED_INCREASE, RAPID_SPEED_DECREASE }

data class SpeedChangeEvidence(
    val previous: SpeedSample,
    val current: SpeedSample,
    val previousMps: Double,
    val currentMps: Double,
    val intervalMs: Long,
    val deltaSpeedMps: Double,
    val accelerationMps2: Double,
    /** 단위 변환·뺄셈·나눗셈의 부동소수점 반올림 오차 범위. 측정 노이즈 보정이 아니다. */
    val roundingToleranceMps2: Double,
)

data class SpeedChangeEvent(
    val eventId: String,
    val sessionId: String,
    val type: SpeedEventType,
    val evidence: SpeedChangeEvidence,
    /** 감소 이벤트는 음수 임계값이다. */
    val triggerThresholdMps2: Double,
) {
    val occurredAtMs: Long get() = evidence.current.timestampMs
    val source: SignalSource get() = evidence.current.source
}

enum class BaselineReason { FIRST_SAMPLE, SOURCE_CHANGED, GAP_TOO_LARGE }
enum class SpeedRejection {
    MISSING_SAMPLE, INVALID_STATUS, MISSING_VALUE, NONFINITE_SPEED, SPEED_OUT_OF_RANGE,
    INVALID_TIMESTAMP, FUTURE_SAMPLE, STALE_SAMPLE, NON_INCREASING_TIME,
    INTERVAL_TOO_SHORT, IMPLAUSIBLE_CHANGE,
}
enum class EventSuppression { ONGOING_EPISODE, COOLDOWN }

sealed interface SpeedDetectionResult {
    data class Baseline(val sample: SpeedSample, val reason: BaselineReason) : SpeedDetectionResult
    data class Rejected(val reason: SpeedRejection, val sample: SpeedSample?) : SpeedDetectionResult
    data class Evaluated(
        val evidence: SpeedChangeEvidence,
        val event: SpeedChangeEvent?,
        val suppression: EventSuppression? = null,
    ) : SpeedDetectionResult
}

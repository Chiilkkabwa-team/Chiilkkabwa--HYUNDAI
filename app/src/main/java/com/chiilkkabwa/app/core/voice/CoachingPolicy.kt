package com.chiilkkabwa.app.core.voice

/** Development policy, not measured TTS duration or certification of driving audio permission. */
data class CoachingPolicy(
    val minIntervalMs: Long = 45_000,
    val maxPerSession: Int = 5,
    val maxSameCode: Int = 2,
    val windowMs: Long = 800,
    val maxEventAgeMs: Long = 2_000,
    val playbackTimeoutMs: Long = 4_000,
    val maxTrackedEventIds: Int = 4_096,
    val maxHistoryRecords: Int = 512,
) {
    init {
        require(minIntervalMs >= 45_000)
        require(maxPerSession in 1..5 && maxSameCode in 1..2)
        require(windowMs > 0 && maxEventAgeMs >= windowMs && playbackTimeoutMs > 0)
        require(maxTrackedEventIds > 0 && maxHistoryRecords > 0)
    }

    companion object {
        const val QUIET_MS = 90_000L
        const val STEADY_MIN_KMH = 10.0
        const val MAX_LINE_SECONDS = 2.5
        // Conservative development bounds matching A02 defaults; not real-vehicle safety limits.
        const val MAX_ABS_ACCELERATION_MPS2 = 15.0
        const val MAX_SPEED_KMH = 360.0
        fun line(code: CoachingCode): String = when (code) {
            CoachingCode.RAPID_SPEED_INCREASE -> "속도가 빠르게 늘었어요."
            CoachingCode.RAPID_SPEED_DECREASE -> "속도가 빠르게 줄었어요."
            CoachingCode.STEADY -> "모의 주행을 이어가고 있어요."
        }
        /** Relative ordering only; decrease is not interpreted as braking or danger. */
        fun priority(code: CoachingCode): Int = when (code) {
            CoachingCode.RAPID_SPEED_INCREASE -> 35
            CoachingCode.RAPID_SPEED_DECREASE -> 85
            CoachingCode.STEADY -> 5
        }
        /** Web heuristic: Hangul syllable=1, other non-space=0.4, 6.5 syllables/sec. */
        fun estimateSeconds(text: String): Double = text.sumOf {
            when {
                it.isWhitespace() -> 0.0
                it in '\uAC00'..'\uD7A3' -> 1.0
                else -> 0.4
            }
        } / 6.5
    }
}

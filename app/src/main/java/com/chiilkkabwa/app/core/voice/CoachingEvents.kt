package com.chiilkkabwa.app.core.voice

import com.chiilkkabwa.app.core.event.SpeedChangeEvent
import com.chiilkkabwa.app.core.event.SpeedChangeEvidence
import com.chiilkkabwa.app.core.event.SpeedEventType
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus

/** No Android audio/vehicle implementation is connected to this core. */
enum class CoachingRuntime { DISABLED, MOCK_REHEARSAL }
enum class CoachingMode { SOLO, MENTOR }
enum class CoachingCode { RAPID_SPEED_INCREASE, RAPID_SPEED_DECREASE, STEADY }
enum class CoachingUnit { METERS_PER_SECOND_SQUARED, KILOMETERS_PER_HOUR }

data class CoachingSession(
    val sessionId: String,
    val goal: String,
    val mode: CoachingMode = CoachingMode.SOLO,
    val voiceEnabled: Boolean? = null,
    val runtime: CoachingRuntime = CoachingRuntime.DISABLED,
    /** Caller must verify the existing parked Mock gate; speed zero is insufficient. */
    val parkedMockAllowed: Boolean = false,
)

data class CoachingEvidence(
    val value: Double,
    val unit: CoachingUnit,
    val speedChange: SpeedChangeEvidence? = null,
    val drivingConditionsValid: Boolean = false,
)

data class CoachingCandidate(
    val eventId: String,
    val sessionId: String,
    val code: CoachingCode,
    /** Session-monotonic ms, same clock as all engine calls. Never mix wall-clock ms. */
    val occurredAtMs: Long,
    val evidence: CoachingEvidence,
    val status: SignalStatus,
    val source: SignalSource,
    /** Goal selected when this event was created. */
    val goal: String,
    val voiceAllowed: Boolean,
) {
    companion object {
        /** A02 -> B02 contract only. No subscription or live playback is introduced. */
        fun fromSpeedEvent(event: SpeedChangeEvent, goal: String, voiceAllowed: Boolean) =
            CoachingCandidate(
                event.eventId, event.sessionId,
                when (event.type) {
                    SpeedEventType.RAPID_SPEED_INCREASE -> CoachingCode.RAPID_SPEED_INCREASE
                    SpeedEventType.RAPID_SPEED_DECREASE -> CoachingCode.RAPID_SPEED_DECREASE
                },
                event.occurredAtMs,
                CoachingEvidence(event.evidence.accelerationMps2,
                    CoachingUnit.METERS_PER_SECOND_SQUARED, event.evidence),
                if (event.evidence.previous.status != SignalStatus.OK) event.evidence.previous.status
                else event.evidence.current.status,
                event.source, goal, voiceAllowed,
            )
    }
}

enum class CoachingStatus { OFFERED, SPOKEN, SKIPPED, BLOCKED, INTERRUPTED }
enum class CoachingReason {
    HIGHEST_ELIGIBLE_PRIORITY, COMPLETED, NOT_THIS_GOAL, VOICE_OFF, RUNTIME_BLOCKED,
    INVALID_ID, WRONG_SESSION, INVALID_SIGNAL, INVALID_EVIDENCE,
    FUTURE_EVENT, STALE_EVENT, TIME_REVERSED, DUPLICATE_EVENT, CAPACITY_REACHED,
    WINDOW_ELAPSED, LOWER_PRIORITY, BUSY, TOO_SOON, DRIVE_CAP, SAME_CODE_CAP,
    ONCE_ONLY, QUIET_TOO_SHORT, TOO_LONG, SESSION_STOPPED,
    FOCUS_DENIED, FOCUS_LOST, APP_STOPPED, TIMEOUT, TTS_FAILED,
}
enum class PlaybackOutcome { COMPLETED, FOCUS_DENIED, FOCUS_LOST, APP_STOPPED, TIMEOUT, TTS_FAILED }

/** Callback correlation uses this instance's identity, not an eventId/string supplied by a callback. */
class CoachingOffer internal constructor(
    val candidate: CoachingCandidate,
    val line: String,
    val estimatedSeconds: Double,
    val offeredAtMs: Long,
)

data class CoachingRecord(
    val candidate: CoachingCandidate,
    val status: CoachingStatus,
    val reason: CoachingReason,
    val recordedAtMs: Long,
) {
    val eventId: String get() = candidate.eventId
    val sessionId: String get() = candidate.sessionId
}

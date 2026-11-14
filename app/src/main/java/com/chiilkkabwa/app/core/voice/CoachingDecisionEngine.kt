package com.chiilkkabwa.app.core.voice

import com.chiilkkabwa.app.core.review.Goals
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus

/**
 * Session-scoped, single-execution-context pure core. Does not play, schedule or persist audio.
 * Call flush on the same monotonic clock to close a window/expire an offer; stop on gate/lifecycle loss.
 * A new instance is required for a new session or after stop. All live sources remain blocked.
 */
class CoachingDecisionEngine(
    private val session: CoachingSession,
    private val startedAtMs: Long,
    private val policy: CoachingPolicy = CoachingPolicy(),
) {
    init {
        require(session.sessionId.isNotBlank())
        require(startedAtMs >= 0)
    }

    private var clock = startedAtMs
    private var lastObservedAt = startedAtMs
    private var lastNoteAt = startedAtMs
    private var stopped = false
    private var windowAt: Long? = null
    private val queue = mutableListOf<CoachingCandidate>()
    private val consumedIds = mutableSetOf<String>()
    private val records = ArrayDeque<CoachingRecord>()
    private val spokenIds = mutableSetOf<String>()
    private val counts = mutableMapOf<CoachingCode, Int>()
    private var lastSpokenOfferAt: Long? = null
    private var active: CoachingOffer? = null

    val history: List<CoachingRecord> get() = records.toList()
    val spokenCount: Int get() = spokenIds.size
    fun didSpeak(eventId: String): Boolean = eventId in spokenIds

    /** Null means admitted to the current 800ms comparison window, not offered or spoken. */
    fun enqueue(candidate: CoachingCandidate, nowMs: Long): CoachingRecord? {
        if (!advance(nowMs)) return record(candidate, CoachingReason.TIME_REVERSED, clock)
        expire(nowMs)
        if (candidate.sessionId != session.sessionId)
            return record(candidate, CoachingReason.WRONG_SESSION, nowMs)
        if (candidate.eventId.isBlank()) return record(candidate, CoachingReason.INVALID_ID, nowMs)
        if (candidate.eventId in consumedIds) return record(candidate, CoachingReason.DUPLICATE_EVENT, nowMs)
        if (consumedIds.size >= policy.maxTrackedEventIds)
            return record(candidate, CoachingReason.CAPACITY_REACHED, nowMs)
        consumedIds += candidate.eventId
        if (candidate.code != CoachingCode.STEADY) lastNoteAt = nowMs
        val timeReason = when {
            candidate.occurredAtMs < startedAtMs || candidate.occurredAtMs < lastObservedAt ->
                CoachingReason.TIME_REVERSED
            candidate.occurredAtMs > nowMs -> CoachingReason.FUTURE_EVENT
            nowMs - candidate.occurredAtMs > policy.maxEventAgeMs -> CoachingReason.STALE_EVENT
            else -> null
        }
        if (timeReason != null) return record(candidate, timeReason, nowMs)
        lastObservedAt = candidate.occurredAtMs
        val reason = blockedReason(candidate, nowMs)
        if (reason != null) return record(candidate, reason, nowMs)
        val open = windowAt
        if (open != null && nowMs - open > policy.windowMs)
            return record(candidate, CoachingReason.WINDOW_ELAPSED, nowMs)
        if (open == null) windowAt = nowMs
        queue += candidate
        return null
    }

    /** Filter AGAIN at decision time before priority selection. Caller must flush expired windows. */
    fun flush(nowMs: Long): CoachingOffer? {
        if (!advance(nowMs)) return null
        expire(nowMs)
        val open = windowAt ?: return null
        if (nowMs - open < policy.windowMs) return null
        val candidates = queue.toList()
        queue.clear()
        windowAt = null
        val eligible = candidates.filter { candidate ->
            val reason = if (nowMs - candidate.occurredAtMs > policy.maxEventAgeMs)
                CoachingReason.STALE_EVENT else blockedReason(candidate, nowMs)
            if (reason != null) record(candidate, reason, nowMs)
            reason == null
        }.sortedWith(compareByDescending<CoachingCandidate> { CoachingPolicy.priority(it.code) }
            .thenBy { it.occurredAtMs })
        val top = eligible.firstOrNull() ?: return null
        eligible.drop(1).forEach { record(it, CoachingReason.LOWER_PRIORITY, nowMs) }
        val offer = CoachingOffer(top, CoachingPolicy.line(top.code),
            CoachingPolicy.estimateSeconds(CoachingPolicy.line(top.code)), nowMs)
        active = offer
        record(top, CoachingReason.HIGHEST_ELIGIBLE_PRIORITY, nowMs, CoachingStatus.OFFERED)
        return offer
    }

    /** Only the playback adapter's normal completion may supply COMPLETED. No adapter is wired here. */
    fun complete(offer: CoachingOffer, outcome: PlaybackOutcome, nowMs: Long): CoachingRecord? {
        if (!advance(nowMs)) return null
        expire(nowMs)
        if (active !== offer) return null
        active = null
        val (status, reason) = when (outcome) {
            PlaybackOutcome.COMPLETED -> CoachingStatus.SPOKEN to CoachingReason.COMPLETED
            PlaybackOutcome.FOCUS_DENIED -> CoachingStatus.BLOCKED to CoachingReason.FOCUS_DENIED
            PlaybackOutcome.FOCUS_LOST -> CoachingStatus.INTERRUPTED to CoachingReason.FOCUS_LOST
            PlaybackOutcome.APP_STOPPED -> CoachingStatus.INTERRUPTED to CoachingReason.APP_STOPPED
            PlaybackOutcome.TIMEOUT -> CoachingStatus.INTERRUPTED to CoachingReason.TIMEOUT
            PlaybackOutcome.TTS_FAILED -> CoachingStatus.SKIPPED to CoachingReason.TTS_FAILED
        }
        if (status == CoachingStatus.SPOKEN) {
            spokenIds += offer.candidate.eventId
            counts[offer.candidate.code] = (counts[offer.candidate.code] ?: 0) + 1
            lastSpokenOfferAt = offer.offeredAtMs
        }
        return record(offer.candidate, reason, nowMs, status)
    }

    /** Cancels pending work permanently; the audio owner must also stop audio/abandon focus. */
    fun stop(nowMs: Long) {
        if (!advance(nowMs)) return
        halt(nowMs, CoachingReason.SESSION_STOPPED)
    }

    /** Mock candidate generation only. Missing data is never evidence of smooth real driving. */
    fun considerSteady(
        eventId: String,
        nowMs: Long,
        speedKmh: Signal<Double>,
        drivingConditionsValid: Boolean,
        voiceAllowed: Boolean = true,
    ): CoachingRecord? = enqueue(
        CoachingCandidate(eventId, session.sessionId, CoachingCode.STEADY,
            speedKmh.receivedAtMs,
            CoachingEvidence(speedKmh.value ?: Double.NaN, CoachingUnit.KILOMETERS_PER_HOUR,
                drivingConditionsValid = drivingConditionsValid),
            speedKmh.status, speedKmh.source, session.goal, voiceAllowed),
        nowMs,
    )

    private fun blockedReason(c: CoachingCandidate, now: Long): CoachingReason? {
        if (stopped) return CoachingReason.SESSION_STOPPED
        if (session.runtime != CoachingRuntime.MOCK_REHEARSAL || !session.parkedMockAllowed ||
            c.source != SignalSource.MOCK) return CoachingReason.RUNTIME_BLOCKED
        if (!(session.voiceEnabled ?: (session.mode != CoachingMode.MENTOR)) || !c.voiceAllowed)
            return CoachingReason.VOICE_OFF
        if (c.status != SignalStatus.OK) return CoachingReason.INVALID_SIGNAL
        if (!validEvidence(c)) return CoachingReason.INVALID_EVIDENCE
        if (session.goal !in Goals.all || c.goal != session.goal ||
            (c.code != CoachingCode.STEADY && session.goal !in listOf("출발·정지", "그냥 몸풀기")))
            return CoachingReason.NOT_THIS_GOAL
        if (active != null) return CoachingReason.BUSY
        if (spokenCount >= policy.maxPerSession) return CoachingReason.DRIVE_CAP
        if (lastSpokenOfferAt?.let { now - it < policy.minIntervalMs } == true)
            return CoachingReason.TOO_SOON
        val used = counts[c.code] ?: 0
        if (c.code == CoachingCode.STEADY && used > 0) return CoachingReason.ONCE_ONLY
        if (used >= policy.maxSameCode) return CoachingReason.SAME_CODE_CAP
        if (CoachingPolicy.estimateSeconds(CoachingPolicy.line(c.code)) > CoachingPolicy.MAX_LINE_SECONDS)
            return CoachingReason.TOO_LONG
        if (c.code == CoachingCode.STEADY && now - lastNoteAt < CoachingPolicy.QUIET_MS)
            return CoachingReason.QUIET_TOO_SHORT
        return null
    }

    private fun validEvidence(c: CoachingCandidate): Boolean {
        val e = c.evidence
        if (!e.value.isFinite()) return false
        val raw = e.speedChange
        if (raw != null && (raw.previous.source != c.source || raw.current.source != c.source ||
            raw.previous.status != SignalStatus.OK || raw.current.status != SignalStatus.OK ||
            raw.current.timestampMs != c.occurredAtMs || raw.accelerationMps2 != e.value)) return false
        return when (c.code) {
            CoachingCode.RAPID_SPEED_INCREASE ->
                e.unit == CoachingUnit.METERS_PER_SECOND_SQUARED && e.value > 0 && e.value <= CoachingPolicy.MAX_ABS_ACCELERATION_MPS2
            CoachingCode.RAPID_SPEED_DECREASE ->
                e.unit == CoachingUnit.METERS_PER_SECOND_SQUARED && e.value < 0 && e.value >= -CoachingPolicy.MAX_ABS_ACCELERATION_MPS2
            CoachingCode.STEADY ->
                e.unit == CoachingUnit.KILOMETERS_PER_HOUR && e.value >= CoachingPolicy.STEADY_MIN_KMH &&
                    e.value <= CoachingPolicy.MAX_SPEED_KMH &&
                    e.drivingConditionsValid
        }
    }

    private fun advance(now: Long): Boolean {
        if (now < clock) {
            halt(clock, CoachingReason.TIME_REVERSED)
            return false
        }
        clock = now
        return true
    }

    private fun halt(now: Long, reason: CoachingReason) {
        stopped = true
        active?.let { record(it.candidate, reason, now, CoachingStatus.INTERRUPTED) }
        active = null
        queue.forEach { record(it, reason, now) }
        queue.clear()
        windowAt = null
    }

    private fun expire(now: Long) {
        val offer = active ?: return
        if (now - offer.offeredAtMs < policy.playbackTimeoutMs) return
        active = null
        record(offer.candidate, CoachingReason.TIMEOUT, now, CoachingStatus.INTERRUPTED)
    }

    private fun record(c: CoachingCandidate, reason: CoachingReason, at: Long,
                       status: CoachingStatus = if (reason in skippedReasons) CoachingStatus.SKIPPED
                       else CoachingStatus.BLOCKED): CoachingRecord {
        val result = CoachingRecord(c, status, reason, at)
        if (records.size == policy.maxHistoryRecords) records.removeFirst()
        records.addLast(result)
        return result
    }

    private companion object {
        val skippedReasons = setOf(CoachingReason.LOWER_PRIORITY, CoachingReason.BUSY,
            CoachingReason.TOO_SOON, CoachingReason.DRIVE_CAP, CoachingReason.SAME_CODE_CAP,
            CoachingReason.ONCE_ONLY, CoachingReason.QUIET_TOO_SHORT, CoachingReason.WINDOW_ELAPSED)
    }
}

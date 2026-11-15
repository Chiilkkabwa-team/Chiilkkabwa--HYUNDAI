package com.chiilkkabwa.app.core.voice

import com.chiilkkabwa.app.core.event.SpeedChangeEvent

/**
 * mock 플레이버의 주행 잠금 동안만: A02 속도 사건 → B02 판단 엔진. 소리는 호출자(Activity)가 낸다.
 * 엔진은 출처가 MOCK인 사건만 받으므로(RUNTIME_BLOCKED) 실제·에뮬레이터 신호로는 절대 말하지 않는다(S05).
 * 시계는 모두 SystemClock.elapsedRealtime() 밀리초(A02 occurredAtMs와 같은 시계).
 */
class MockDriveCoach(
    private val goal: String?,
    private val mentor: Boolean,
    private val startedAtMs: Long,
    /** 설정의 음성 켜기. null이면 B02 기본(혼자 켜짐·멘토 꺼짐) */
    private val voiceEnabled: Boolean? = null,
    private val maxPerSession: Int = 5,
) {
    private var engine: CoachingDecisionEngine? = null

    /** 엔진은 주행의 첫 속도 구독 세션에 묶는다. 중간에 구독이 바뀌면 그 사건은 WRONG_SESSION으로 버려 주행당 횟수 상한을 지킨다. */
    fun onSpeedEvent(event: SpeedChangeEvent, nowMs: Long): CoachingRecord? {
        val g = goal ?: return null
        val e = engine ?: CoachingDecisionEngine(
            CoachingSession(event.sessionId, g, if (mentor) CoachingMode.MENTOR else CoachingMode.SOLO,
                voiceEnabled = voiceEnabled,
                runtime = CoachingRuntime.MOCK_REHEARSAL,
                // mock 플레이버에는 실제 주행이 없다: 이 값은 '모의 신호만'임을 호출자가 보증한다는 뜻
                parkedMockAllowed = true),
            startedAtMs,
            CoachingPolicy(maxPerSession = maxPerSession.coerceIn(1, 5)),
        ).also { engine = it }
        return e.enqueue(CoachingCandidate.fromSpeedEvent(event, g, voiceAllowed = true), nowMs)
    }

    fun flush(nowMs: Long): CoachingOffer? = engine?.flush(nowMs)
    fun complete(offer: CoachingOffer, outcome: PlaybackOutcome, nowMs: Long) = engine?.complete(offer, outcome, nowMs)
    fun stop(nowMs: Long) = engine?.stop(nowMs)
    fun didSpeak(eventId: String) = engine?.didSpeak(eventId) == true
}

package com.chiilkkabwa.app.core.voice

import com.chiilkkabwa.app.core.event.SpeedChangeEvent
import com.chiilkkabwa.app.core.review.DriveTimelineRecorder
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSpeedObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** mock 주행 음성 길잡이: 속도 표본 → A02 사건(시간표와 같은 것) → B02 판단. 실제·에뮬레이터 신호로는 말하지 않는다. */
class MockDriveCoachTest {
    private val sec = 1_000_000_000L

    /** 0 → 0 → 5 m/s (0.5초 간격) = +10 m/s² 급가속 사건 하나 */
    private fun rapidIncrease(source: SignalSource): SpeedChangeEvent {
        val r = DriveTimelineRecorder().apply { start() }
        val xs = listOf(0.0 to 0f, 0.5 to 0f, 1.0 to 5f).map { (t, mps) ->
            (t * sec).toLong().let { VehicleSpeedObservation(mps, it, it, source, SignalStatus.OK, "drive-1") }
        }
        return xs.mapNotNull { r.accept(it, it.receivedAtNanos) }.single()
    }

    private fun offerFor(coach: MockDriveCoach, e: SpeedChangeEvent): CoachingOffer? {
        coach.onSpeedEvent(e, e.occurredAtMs)
        return coach.flush(e.occurredAtMs + 900)
    }

    @Test fun mock_rapid_increase_is_offered_and_counts_only_when_completed() {
        val coach = MockDriveCoach("출발·정지", mentor = false, startedAtMs = 0)
        val e = rapidIncrease(SignalSource.MOCK)
        val offer = offerFor(coach, e)!!
        assertEquals(CoachingPolicy.line(CoachingCode.RAPID_SPEED_INCREASE), offer.line)
        assertEquals(false, coach.didSpeak(e.eventId))
        coach.complete(offer, PlaybackOutcome.COMPLETED, e.occurredAtMs + 2_000)
        assertTrue(coach.didSpeak(e.eventId))
    }

    @Test fun emulator_signal_never_speaks() {
        val coach = MockDriveCoach("출발·정지", mentor = false, startedAtMs = 0)
        val e = rapidIncrease(SignalSource.EMULATOR)
        assertEquals(CoachingReason.RUNTIME_BLOCKED, coach.onSpeedEvent(e, e.occurredAtMs)?.reason)
        assertNull(coach.flush(e.occurredAtMs + 900))
    }

    @Test fun mentor_other_goal_or_no_goal_stay_silent() {
        val e = rapidIncrease(SignalSource.MOCK)
        assertNull(offerFor(MockDriveCoach("출발·정지", mentor = true, startedAtMs = 0), e))
        assertNull(offerFor(MockDriveCoach("주차", mentor = false, startedAtMs = 0), e))
        assertNull(offerFor(MockDriveCoach(null, mentor = false, startedAtMs = 0), e))
    }

    @Test fun focus_lost_is_dropped_not_counted() {
        val coach = MockDriveCoach("그냥 몸풀기", mentor = false, startedAtMs = 0)
        val e = rapidIncrease(SignalSource.MOCK)
        val offer = offerFor(coach, e)!!
        coach.complete(offer, PlaybackOutcome.FOCUS_LOST, e.occurredAtMs + 1_500)
        assertEquals(false, coach.didSpeak(e.eventId))
    }
}

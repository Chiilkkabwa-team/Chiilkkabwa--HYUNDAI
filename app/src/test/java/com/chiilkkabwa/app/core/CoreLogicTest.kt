package com.chiilkkabwa.app.core

import com.chiilkkabwa.app.core.achievement.AchievementRules
import com.chiilkkabwa.app.core.achievement.Badge
import com.chiilkkabwa.app.core.achievement.PracticeHistory
import com.chiilkkabwa.app.core.mission.BrakePressReleaseJudge
import com.chiilkkabwa.app.core.mission.CarPose
import com.chiilkkabwa.app.core.mission.MissionCatalog
import com.chiilkkabwa.app.core.mission.MissionId
import com.chiilkkabwa.app.core.mission.MissionResult
import com.chiilkkabwa.app.core.mission.MissionRunner
import com.chiilkkabwa.app.core.mission.ParkingSim
import com.chiilkkabwa.app.core.mission.PedalHoldJudge
import com.chiilkkabwa.app.core.mission.Progress
import com.chiilkkabwa.app.core.mission.SeatBeltJudge
import com.chiilkkabwa.app.core.mission.TurnSignalJudge
import com.chiilkkabwa.app.core.safety.GatePolicy
import com.chiilkkabwa.app.core.safety.GateState
import com.chiilkkabwa.app.core.safety.LockReason
import com.chiilkkabwa.app.core.safety.SafetyGate
import com.chiilkkabwa.app.core.session.Phase
import com.chiilkkabwa.app.core.session.SessionReducer
import com.chiilkkabwa.app.core.session.SessionState
import com.chiilkkabwa.app.core.session.summarize
import com.chiilkkabwa.app.core.signal.CarDimensions
import com.chiilkkabwa.app.core.signal.Gear
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.TurnSignalState
import com.chiilkkabwa.app.core.signal.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 문서 docs/06_TEST_PLAN.md의 T01~T12 중 순수 로직으로 검증 가능한 부분 */
class CoreLogicTest {
    private val E = SignalSource.EMULATOR
    private fun <T> ok(v: T, at: Long) = Signal.ok(v, E, at)

    private fun parked(at: Long = 0) = VehicleSnapshot(
        gear = ok(Gear.PARK, at), parkingBrakeOn = ok(true, at), uxRestricted = ok(false, at),
    )

    // ---------- 안전 게이트 (T01, T02) ----------
    @Test fun gate_ready_only_when_park_brake_and_no_restriction() {
        assertEquals(GateState.Ready, SafetyGate.evaluate(parked()))
    }

    @Test fun gate_locks_when_unknown() {
        assertEquals(GateState.Locked(LockReason.UNKNOWN_STATE), SafetyGate.evaluate(VehicleSnapshot()))
    }

    @Test fun gate_restriction_has_priority() {
        val s = parked().copy(uxRestricted = ok(true, 1))
        assertEquals(GateState.Locked(LockReason.DRIVING_RESTRICTED), SafetyGate.evaluate(s))
    }

    @Test fun gate_speed_zero_but_drive_is_not_parked() {
        val s = parked().copy(gear = ok(Gear.DRIVE, 1))
        assertEquals(GateState.Locked(LockReason.NOT_IN_PARK), SafetyGate.evaluate(s))
    }

    @Test fun gate_parking_brake_unsupported_policy() {
        val s = parked().copy(parkingBrakeOn = Signal.failed(SignalStatus.UNSUPPORTED, E, 1))
        assertEquals(GateState.Locked(LockReason.UNKNOWN_STATE), SafetyGate.evaluate(s))
        assertEquals(GateState.Ready, SafetyGate.evaluate(s, GatePolicy(allowWhenParkingBrakeUnsupported = true)))
    }

    // ---------- 미션 (T04, T05, T03) ----------
    @Test fun brake_already_pressed_does_not_complete() {
        val j = BrakePressReleaseJudge()
        j.start(parked().copy(brakePressed = ok(true, 0)), 100)
        // 시작 후 뗌만 관측 → 완료 아님
        val p1 = j.update(parked().copy(brakePressed = ok(false, 150)), 150)
        assertTrue(p1 is Progress.InProgress)
        // 밟음 → 뗌 → 완료
        j.update(parked().copy(brakePressed = ok(true, 200)), 200)
        val p3 = j.update(parked().copy(brakePressed = ok(false, 300)), 300)
        assertTrue(p3 is Progress.Done)
        assertEquals(E, (p3 as Progress.Done).source)
    }

    @Test fun old_values_before_start_are_ignored() {
        val j = BrakePressReleaseJudge()
        j.start(parked().copy(brakePressed = ok(false, 0)), 1000)
        // 시작 이전 시각의 이벤트는 무시
        j.update(parked().copy(brakePressed = ok(true, 500)), 1001)
        val p = j.update(parked().copy(brakePressed = ok(false, 600)), 1002)
        assertTrue(p is Progress.InProgress)
    }

    @Test fun unsupported_signal_gives_unavailable() {
        val j = SeatBeltJudge()
        j.start(parked(), 0)
        val p = j.update(parked().copy(driverBeltBuckled = Signal.failed(SignalStatus.UNSUPPORTED, E, 1)), 1)
        assertEquals(Progress.Unavailable(SignalStatus.UNSUPPORTED), p)
    }

    @Test fun belt_already_buckled_requires_rebuckle() {
        val j = SeatBeltJudge()
        j.start(parked().copy(driverBeltBuckled = ok(true, 0)), 10)
        assertTrue(j.update(parked().copy(driverBeltBuckled = ok(true, 20)), 20) is Progress.InProgress)
        j.update(parked().copy(driverBeltBuckled = ok(false, 30)), 30)
        assertTrue(j.update(parked().copy(driverBeltBuckled = ok(true, 40)), 40) is Progress.Done)
    }

    @Test fun turn_signal_left_right_off() {
        val j = TurnSignalJudge()
        j.start(parked().copy(turnSignal = ok(TurnSignalState.NONE, 0)), 1)
        j.update(parked().copy(turnSignal = ok(TurnSignalState.LEFT, 2)), 2)
        j.update(parked().copy(turnSignal = ok(TurnSignalState.RIGHT, 3)), 3)
        assertTrue(j.update(parked().copy(turnSignal = ok(TurnSignalState.NONE, 4)), 4) is Progress.Done)
    }

    @Test fun pedal_hold_two_seconds_in_band() {
        val j = PedalHoldJudge({ it.accelPedalPct })
        j.start(parked(), 0)
        var p: Progress? = null
        for (t in 0..2200L step 100) p = j.update(parked().copy(accelPedalPct = ok(27f, t)), t)
        assertTrue(p is Progress.Done)
    }

    // ---------- 러너: 일시정지 중 완료 안 됨(T06), 재개 시 새로 시작(T07) ----------
    @Test fun runner_pause_and_resume() {
        val r = MissionRunner(MissionCatalog.warmupRequired())
        r.start(parked().copy(driverBeltBuckled = ok(false, 0)), 1)
        r.pause()
        r.update(parked().copy(driverBeltBuckled = ok(true, 5)), 5)
        assertEquals(0, r.state().results.size)
        r.resume(parked().copy(driverBeltBuckled = ok(false, 6)), 6)
        r.update(parked().copy(driverBeltBuckled = ok(true, 7)), 7)
        assertEquals(MissionId.SEAT_BELT, r.state().results.single().id)
        assertEquals(1, r.state().index)
    }

    // ---------- 세션 흐름 ----------
    @Test fun session_flow_parked_drive_review() {
        var st = SessionState()
        st = SessionReducer.onGate(st, SafetyGate.evaluate(parked()), parked(), 0)
        assertEquals(Phase.READY, st.phase)
        st = SessionReducer.onStartPressed(st)
        assertEquals(Phase.PREPARING, st.phase)
        val driving = parked().copy(gear = ok(Gear.DRIVE, 10), tripDistanceKm = ok(100f, 10), tripTimeSec = ok(1000L, 10))
        st = SessionReducer.onGate(st, SafetyGate.evaluate(driving), driving, 10_000)
        assertEquals(Phase.DRIVING_LOCK, st.phase)
        val back = parked(20).copy(tripDistanceKm = ok(103.2f, 20), tripTimeSec = ok(1840L, 20))
        st = SessionReducer.onGate(st, SafetyGate.evaluate(back), back, 900_000)
        assertEquals(Phase.REVIEW, st.phase)
        val sum = summarize(st.tripStart, st.tripEnd)!!
        assertEquals(14, sum.durationMin)
        assertTrue(sum.durationFromVehicle)
        assertTrue(kotlin.math.abs(sum.distanceKm!! - 3.2f) < 0.01f)
    }

    /** 준비 중 주차 브레이크를 풀었다가 다시 걸면 일시 정지가 풀려야 한다(2026-11-04 수정, web 테스트와 같은 시나리오) */
    @Test fun preparing_pause_resumes_when_brake_reapplied() {
        var st = SessionState(phase = Phase.PREPARING, lockReason = null)
        val off = parked().copy(parkingBrakeOn = ok(false, 1))
        st = SessionReducer.onGate(st, SafetyGate.evaluate(off), off, 1)
        assertEquals(Phase.PREPARING, st.phase)
        assertEquals(LockReason.PARKING_BRAKE_OFF, st.lockReason)
        st = SessionReducer.onGate(st, SafetyGate.evaluate(parked(2)), parked(2), 2)
        assertEquals(Phase.PREPARING, st.phase)
        assertEquals(null, st.lockReason)
    }

    @Test fun summary_falls_back_to_wall_clock() {
        var st = SessionState(phase = Phase.PREPARING)
        val d = parked().copy(uxRestricted = ok(true, 1))
        st = SessionReducer.onGate(st, SafetyGate.evaluate(d), d, 0)
        st = SessionReducer.onGate(st, SafetyGate.evaluate(parked(2)), parked(2), 600_000)
        val sum = summarize(st.tripStart, st.tripEnd)!!
        assertEquals(10, sum.durationMin)
        assertEquals(false, sum.durationFromVehicle)
        assertEquals(null, sum.distanceKm)
    }

    // ---------- 배지 ----------
    @Test fun badges_mark_mock_only() {
        val h = PracticeHistory(missionResults = listOf(MissionResult(MissionId.SEAT_BELT, SignalSource.MOCK, 1, null)))
        val b = AchievementRules.newlyEarned(h, emptySet(), 2)
        assertEquals(Badge.FIRST_CLICK, b.single().badge)
        assertTrue(b.single().mockOnly)
    }

    // ---------- 가상 주차 ----------
    @Test fun parking_sim_reverse_turn_changes_heading() {
        val sim = ParkingSim(CarDimensions.COMPACT_SEDAN_DEFAULT)
        var p = CarPose(0.0, 0.0, 0.0)
        repeat(30) { p = sim.step(p, steeringDeg = 540.0, speedMps = -1.0, dtSec = 0.1) }
        assertTrue(p.y > 0.0)              // 후진 → 아래로
        assertTrue(kotlin.math.abs(p.heading) > 0.1) // 핸들을 돌렸으니 방향이 바뀜
    }
}

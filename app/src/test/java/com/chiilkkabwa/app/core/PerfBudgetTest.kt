package com.chiilkkabwa.app.core

import com.chiilkkabwa.app.core.mission.MissionCatalog
import com.chiilkkabwa.app.core.mission.MissionRunner
import com.chiilkkabwa.app.core.review.DriveTimelineRecorder
import com.chiilkkabwa.app.core.safety.SafetyGate
import com.chiilkkabwa.app.core.session.SessionReducer
import com.chiilkkabwa.app.core.session.SessionState
import com.chiilkkabwa.app.core.signal.Gear
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSnapshot
import com.chiilkkabwa.app.core.signal.VehicleSpeedObservation
import com.chiilkkabwa.app.core.voice.MockDriveCoach
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 성능 예산(P10: 차량 앱 CPU 3%) 중 **앱 판단 로직 몫**만 JVM에서 잰다.
 * 화면 그리기(Compose)·시스템 서비스·실기기 CPU 차이는 포함하지 않는다 → 에뮬레이터 프로파일러 실측을 대신하지 않는다.
 * 결과는 test XML의 system-out에 남는다(docs/VERIFICATION_LOG.md).
 */
class PerfBudgetTest {
    private val M = SignalSource.MOCK
    private fun <T> ok(v: T, at: Long) = Signal.ok(v, M, at)

    /** 한 번 갱신에 드는 평균 나노초(워밍업 뒤) */
    private inline fun nsPerOp(n: Int, op: (Int) -> Unit): Double {
        repeat(n) { op(it) }
        val t0 = System.nanoTime(); repeat(n) { op(it) }
        return (System.nanoTime() - t0).toDouble() / n
    }

    /** 1코어 기준 CPU 점유율(%) = 1회 비용 × 초당 횟수 */
    private fun cpuPct(ns: Double, perSec: Int) = ns * perSec / 1e9 * 100

    @Test fun driving_lock_update_fits_budget() {
        // 주행 잠금 중 신호가 초당 10번 바뀐다고 보고(속도 표본 10Hz) 게이트 + 세션 + 시간표·A02 + 모의 음성 판단을 돈다
        val rec = DriveTimelineRecorder().apply { start() }
        val coach = MockDriveCoach("출발·정지", mentor = false, startedAtMs = 0)
        var session = SessionState()
        val ns = nsPerOp(50_000) { i ->
            val ms = i * 100L
            val ts = ms * 1_000_000
            val s = VehicleSnapshot(
                gear = ok(Gear.DRIVE, ms), parkingBrakeOn = ok(false, ms), uxRestricted = ok(true, ms),
                speed = VehicleSpeedObservation((i % 200) / 10f, ts, ts, M, SignalStatus.OK, "s"),
            )
            val gate = SafetyGate.evaluate(s)
            session = SessionReducer.onGate(session, gate, s, ms)
            rec.accept(s.speed, ts)?.let { coach.onSpeedEvent(it, ms) }
            coach.flush(ms)
        }
        val pct = cpuPct(ns, 10)
        println("PERF driving_lock: ${"%.1f".format(ns / 1000)}µs/update, ${"%.4f".format(pct)}% CPU @10Hz (JVM, 로직만)")
        assertTrue("주행 잠금 로직이 1코어의 0.5%를 넘으면 안 된다: $pct%", pct < 0.5)
    }

    @Test fun preparing_tick_fits_budget() {
        // 준비 단계: ViewModel이 200ms마다(초당 5번) 미션 판정을 돈다
        val runner = MissionRunner(MissionCatalog.warmupRequired() + MissionCatalog.warmupOptional())
        val parked = { ms: Long -> VehicleSnapshot(gear = ok(Gear.PARK, ms), parkingBrakeOn = ok(true, ms), uxRestricted = ok(false, ms)) }
        runner.start(parked(0), 0)
        val ns = nsPerOp(50_000) { i ->
            val ms = i * 200L
            val s = parked(ms)
            SafetyGate.evaluate(s); runner.update(s, ms)
        }
        val pct = cpuPct(ns, 5)
        println("PERF preparing: ${"%.1f".format(ns / 1000)}µs/tick, ${"%.4f".format(pct)}% CPU @5Hz (JVM, 로직만)")
        assertTrue("준비 단계 로직이 1코어의 0.5%를 넘으면 안 된다: $pct%", pct < 0.5)
    }
}

package com.chiilkkabwa.app.core.review

import com.chiilkkabwa.app.core.event.SpeedEventType
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSpeedObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C01 Android 시간표: A02 사건 판정 재사용, 같은 표본 중복, 신호 없음, 상한 */
class DriveTimelineTest {
    private val sec = 1_000_000_000L
    private fun obs(t: Double, mps: Float, session: String = "s1", status: SignalStatus = SignalStatus.OK) =
        (t * sec).toLong().let { VehicleSpeedObservation(mps, it, it, SignalSource.EMULATOR, status, session) }

    private fun feed(r: DriveTimelineRecorder, vararg xs: VehicleSpeedObservation) = xs.forEach { r.accept(it, it.receivedAtNanos) }

    @Test fun records_points_about_once_per_second() {
        val r = DriveTimelineRecorder().apply { start() }
        feed(r, *(0..40).map { obs(it * 0.25, 10f) }.toTypedArray())    // 10초, 0.25초 간격
        val tl = r.snapshot()
        assertEquals(11, tl.points.size)
        assertEquals(setOf(SignalSource.EMULATOR), tl.sources)
    }

    @Test fun rapid_decrease_is_marked_with_A02_rule() {
        val r = DriveTimelineRecorder().apply { start() }
        feed(r, obs(0.0, 15f), obs(0.5, 15f), obs(1.0, 11f))             // -8 m/s² > 3.33 임계
        val m = r.snapshot().marks.single()
        assertEquals(SpeedEventType.RAPID_SPEED_DECREASE, m.type)
        assertEquals(54f, m.fromKmh, 0.01f); assertEquals(39.6f, m.toKmh, 0.01f)
    }

    @Test fun repeated_same_sample_does_not_reset_detection() {
        // 다른 신호 변화로 같은 속도 표본이 여러 번 들어와도 사건을 놓치지 않는다
        val r = DriveTimelineRecorder().apply { start() }
        val a = obs(0.0, 15f); val b = obs(0.5, 15f); val c = obs(1.0, 11f)
        feed(r, a, a, a, b, b, c, c)
        assertEquals(1, r.snapshot().marks.size)
    }

    @Test fun no_speed_signal_gives_empty_timeline() {
        val r = DriveTimelineRecorder().apply { start() }
        feed(r, obs(0.0, 10f, status = SignalStatus.DISCONNECTED), obs(1.0, 10f, status = SignalStatus.UNSUPPORTED))
        assertFalse(r.snapshot().hasData)
    }

    @Test fun new_subscription_session_starts_new_baseline() {
        // 앱이 중단됐다 돌아오면(새 sessionId) 이전 세션 값과 이어 계산하지 않는다
        val r = DriveTimelineRecorder().apply { start() }
        feed(r, obs(0.0, 20f, "s1"), obs(1.0, 2f, "s2"))
        assertTrue(r.snapshot().marks.isEmpty())
    }

    @Test fun start_clears_previous_drive_and_points_are_capped() {
        val r = DriveTimelineRecorder(maxPoints = 3).apply { start() }
        feed(r, *(0..10).map { obs(it.toDouble(), 5f) }.toTypedArray())
        assertEquals(3, r.snapshot().points.size)
        r.start()
        assertFalse(r.snapshot().hasData)
    }
}

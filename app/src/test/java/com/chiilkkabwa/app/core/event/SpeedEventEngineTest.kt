package com.chiilkkabwa.app.core.event

import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import org.junit.Assert.*
import org.junit.Test

class SpeedEventEngineTest {
    private fun sample(speed: Double?, at: Long, unit: SpeedUnit = SpeedUnit.METERS_PER_SECOND,
        source: SignalSource = SignalSource.MOCK, status: SignalStatus = SignalStatus.OK) =
        SpeedSample(speed, at, unit, source, status)
    private fun feed(engine: SpeedEventEngine, speed: Double?, at: Long,
        unit: SpeedUnit = SpeedUnit.METERS_PER_SECOND): SpeedDetectionResult =
        engine.update(sample(speed, at, unit), at)
    private fun evaluated(result: SpeedDetectionResult) = result as SpeedDetectionResult.Evaluated
    private fun reason(result: SpeedDetectionResult) = (result as SpeedDetectionResult.Rejected).reason

    @Test fun first_sample_is_only_a_baseline() {
        assertTrue(feed(SpeedEventEngine("s"), 20.0, 0) is SpeedDetectionResult.Baseline)
    }
    @Test fun constant_and_gentle_speed_produce_evidence_without_event() {
        val e = SpeedEventEngine("s"); feed(e, 10.0, 0)
        assertEquals(0.0, evaluated(feed(e, 10.0, 1000)).evidence.accelerationMps2, 0.0)
        assertNull(evaluated(feed(e, 11.0, 2000)).event)
    }
    @Test fun increase_at_threshold_has_reproducible_evidence_and_unique_id() {
        val e = SpeedEventEngine("session-A"); feed(e, 10.0, 0)
        val result = evaluated(feed(e, 12.5, 1000)); val event = result.event!!
        assertEquals(SpeedEventType.RAPID_SPEED_INCREASE, event.type)
        assertEquals("session-A", event.sessionId)
        assertEquals("session-A:speed:1", event.eventId)
        assertEquals(1000L, event.occurredAtMs)
        assertEquals(SignalSource.MOCK, event.source)
        assertEquals(2.5, event.triggerThresholdMps2, 0.0)
        assertEquals(10.0, event.evidence.previous.speed!!, 0.0)
        assertEquals(12.5, event.evidence.current.speed!!, 0.0)
        assertEquals(1000L, event.evidence.intervalMs)
        assertEquals(2.5, event.evidence.deltaSpeedMps, 0.0)
        assertEquals(SignalStatus.OK, event.evidence.current.status)
    }
    @Test fun decrease_reports_speed_change_without_claiming_brake_or_risk() {
        val e = SpeedEventEngine("s", SpeedEventConfig(decreaseThresholdMps2 = 3.0))
        feed(e, 20.0, 0)
        val event = evaluated(feed(e, 17.0, 1000)).event!!
        assertEquals(SpeedEventType.RAPID_SPEED_DECREASE, event.type)
        assertEquals(-3.0, event.triggerThresholdMps2, 0.0)
        assertEquals(-3.0, event.evidence.accelerationMps2, 0.0)
    }
    @Test fun just_below_each_threshold_does_not_emit() {
        val config = SpeedEventConfig(increaseThresholdMps2 = 2.5, decreaseThresholdMps2 = 3.0)
        val up = SpeedEventEngine("up", config); feed(up, 10.0, 0)
        assertNull(evaluated(feed(up, 12.499, 1000)).event)
        val down = SpeedEventEngine("down", config); feed(down, 10.0, 0)
        assertNull(evaluated(feed(down, 7.001, 1000)).event)
    }
    @Test fun kmh_and_mixed_units_use_elapsed_seconds_and_keep_raw_units() {
        val e = SpeedEventEngine("s"); feed(e, 36.0, 0, SpeedUnit.KILOMETERS_PER_HOUR)
        val r = evaluated(feed(e, 15.0, 2000))
        assertEquals(2.5, r.evidence.accelerationMps2, 1e-12)
        assertEquals(10.0, r.evidence.previousMps, 1e-12)
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, r.event!!.evidence.previous.unit)
        assertEquals(36.0, r.evidence.previous.speed!!, 0.0)
    }
    @Test fun signal_adapter_preserves_status_time_and_source() {
        val signal: Signal<Number> = Signal.ok(36f, SignalSource.EMULATOR, 123)
        val input = SpeedSample.fromSignal(signal, SpeedUnit.KILOMETERS_PER_HOUR)
        assertEquals(36.0, input.speed)
        assertEquals(123L, input.timestampMs)
        assertEquals(SignalSource.EMULATOR, input.source)
        assertEquals(SignalStatus.OK, input.status)
        val missing = SpeedSample.fromSignal(Signal.failed<Number>(SignalStatus.UNKNOWN, SignalSource.VEHICLE, 500), SpeedUnit.METERS_PER_SECOND)
        assertNull(missing.speed)
        assertEquals(SignalStatus.UNKNOWN, missing.status)
    }
    @Test fun source_is_never_promoted_and_source_change_seeds_a_new_baseline() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0)
        val changed = e.update(sample(30.0, 1000, source = SignalSource.EMULATOR), 1000)
        assertEquals(BaselineReason.SOURCE_CHANGED, (changed as SpeedDetectionResult.Baseline).reason)
        val event = evaluated(e.update(sample(33.0, 2000, source = SignalSource.EMULATOR), 2000)).event!!
        assertEquals(SignalSource.EMULATOR, event.source)
        assertEquals(SignalSource.EMULATOR, event.evidence.previous.source)
    }
    @Test fun every_non_ok_status_breaks_continuity_even_with_a_numeric_value() {
        SignalStatus.entries.filter { it != SignalStatus.OK }.forEach { status ->
            val e = SpeedEventEngine("s"); feed(e, 0.0, 0)
            assertEquals(SpeedRejection.INVALID_STATUS, reason(e.update(sample(20.0, 1000, status = status), 1000)))
            assertTrue(feed(e, 30.0, 2000) is SpeedDetectionResult.Baseline)
        }
    }
    @Test fun missing_sample_and_missing_value_break_continuity() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0)
        assertEquals(SpeedRejection.MISSING_SAMPLE, reason(e.update(null, 500)))
        assertTrue(feed(e, 10.0, 1000) is SpeedDetectionResult.Baseline)
        assertEquals(SpeedRejection.MISSING_VALUE, reason(feed(e, null, 1500)))
        assertTrue(feed(e, 20.0, 2000) is SpeedDetectionResult.Baseline)
    }
    @Test fun nonfinite_negative_and_over_limit_speed_are_rejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01, 100.01).forEach { speed ->
            val e = SpeedEventEngine("s"); feed(e, 10.0, 0)
            assertTrue(feed(e, speed, 1000) is SpeedDetectionResult.Rejected)
            assertTrue(feed(e, 20.0, 2000) is SpeedDetectionResult.Baseline)
        }
        assertTrue(feed(SpeedEventEngine("s"), 100.0, 0) is SpeedDetectionResult.Baseline)
    }
    @Test fun freshness_includes_age_boundary_and_rejects_future_or_invalid_clock() {
        assertTrue(SpeedEventEngine("a").update(sample(1.0, 0), 1000) is SpeedDetectionResult.Baseline)
        assertEquals(SpeedRejection.STALE_SAMPLE, reason(SpeedEventEngine("b").update(sample(1.0, 0), 1001)))
        assertEquals(SpeedRejection.FUTURE_SAMPLE, reason(SpeedEventEngine("c").update(sample(1.0, 11), 10)))
        assertEquals(SpeedRejection.INVALID_TIMESTAMP, reason(SpeedEventEngine("d").update(sample(1.0, -1), 10)))
        assertEquals(SpeedRejection.INVALID_TIMESTAMP, reason(SpeedEventEngine("e").update(sample(1.0, 0), -1)))
    }
    @Test fun duplicate_and_reversed_timestamps_cannot_replay_old_pairs() {
        val e = SpeedEventEngine("s"); feed(e, 10.0, 1000)
        assertEquals(SpeedRejection.NON_INCREASING_TIME, reason(feed(e, 20.0, 1000)))
        assertEquals(SpeedRejection.NON_INCREASING_TIME, reason(feed(e, 1.0, 0)))
        assertEquals(SpeedRejection.NON_INCREASING_TIME, reason(feed(e, 4.0, 500)))
        assertTrue(feed(e, 15.0, 2000) is SpeedDetectionResult.Baseline)
    }
    @Test fun long_gap_seeds_current_sample_instead_of_inventing_acceleration() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0)
        val gap = feed(e, 40.0, 2001) as SpeedDetectionResult.Baseline
        assertEquals(BaselineReason.GAP_TOO_LARGE, gap.reason)
        assertEquals(1.0, evaluated(feed(e, 41.0, 3001)).evidence.accelerationMps2, 0.0)
    }
    @Test fun short_intervals_accumulate_against_anchor_and_exact_minimum_is_valid() {
        val e = SpeedEventEngine("s"); feed(e, 10.0, 0)
        assertEquals(SpeedRejection.INTERVAL_TOO_SHORT, reason(feed(e, 10.025, 10)))
        assertEquals(SpeedRejection.INTERVAL_TOO_SHORT, reason(feed(e, 10.1, 40)))
        val r = evaluated(feed(e, 10.125, 50))
        assertEquals(50L, r.evidence.intervalMs)
        assertEquals(2.5, r.evidence.accelerationMps2, 1e-10)
        assertNotNull(r.event)
    }
    @Test fun implausible_acceleration_is_dropped_without_rebound_event() {
        val e = SpeedEventEngine("s"); feed(e, 10.0, 0)
        assertEquals(SpeedRejection.IMPLAUSIBLE_CHANGE, reason(feed(e, 90.0, 1000)))
        assertTrue(feed(e, 10.0, 2000) is SpeedDetectionResult.Baseline)
    }
    @Test fun continuous_episode_emits_once_even_after_cooldown() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0)
        assertNotNull(evaluated(feed(e, 3.0, 1000)).event)
        (2L..10L).forEach { second ->
            val r = evaluated(feed(e, second * 3.0, second * 1000))
            assertNull(r.event); assertEquals(EventSuppression.ONGOING_EPISODE, r.suppression)
        }
    }
    @Test fun recovery_hysteresis_and_cooldown_boundary_control_new_episodes() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0)
        assertNotNull(evaluated(feed(e, 3.0, 1000)).event)
        feed(e, 5.0, 2000) // 2.0 is above release 1.5; do not rearm.
        assertEquals(EventSuppression.ONGOING_EPISODE, evaluated(feed(e, 8.0, 3000)).suppression)
        feed(e, 9.5, 4000) // release boundary, rearm.
        assertEquals(EventSuppression.COOLDOWN, evaluated(feed(e, 12.5, 5000)).suppression)
        feed(e, 12.5, 6000)
        val event = evaluated(feed(e, 15.5, 7000)).event!! // exactly 6000 ms from first event.
        assertEquals("s:speed:2", event.eventId)
    }
    @Test fun opposite_direction_has_its_own_cooldown() {
        val e = SpeedEventEngine("s"); feed(e, 20.0, 0)
        assertNotNull(evaluated(feed(e, 23.0, 1000)).event)
        assertEquals(SpeedEventType.RAPID_SPEED_DECREASE, evaluated(feed(e, 19.0, 2000)).event!!.type)
    }
    @Test fun invalid_data_does_not_clear_emitted_event_cooldown() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0); feed(e, 3.0, 1000)
        e.update(null, 1500); feed(e, 3.0, 2000)
        assertEquals(EventSuppression.COOLDOWN, evaluated(feed(e, 6.0, 3000)).suppression)
    }
    @Test fun reset_discards_baseline_without_reusing_ids_or_bypassing_cooldown() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0); feed(e, 3.0, 1000)
        e.reset(); assertTrue(feed(e, 10.0, 6000) is SpeedDetectionResult.Baseline)
        assertEquals("s:speed:2", evaluated(feed(e, 13.0, 7000)).event!!.eventId)
    }
    @Test fun invalid_configuration_and_blank_session_are_rejected() {
        val configs = listOf<() -> SpeedEventConfig>(
            { SpeedEventConfig(increaseThresholdMps2 = 0.0) },
            { SpeedEventConfig(decreaseThresholdMps2 = Double.NaN) },
            { SpeedEventConfig(maxSpeedMps = Double.POSITIVE_INFINITY) },
            { SpeedEventConfig(minIntervalMs = 0) },
            { SpeedEventConfig(minIntervalMs = 2001) },
            { SpeedEventConfig(maxSampleAgeMs = -1) },
            { SpeedEventConfig(cooldownMs = -1) },
            { SpeedEventConfig(releaseRatio = 1.0) },
            { SpeedEventConfig(releaseRatio = 0.0) },
            { SpeedEventConfig(maxAbsAccelerationMps2 = 2.0) },
        )
        configs.forEach { make -> try { make(); fail("Invalid config accepted") } catch (_: IllegalArgumentException) {} }
        try { SpeedEventEngine(" "); fail("Blank session accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun zero_cooldown_still_requires_recovery_before_another_event() {
        val e = SpeedEventEngine("s", SpeedEventConfig(cooldownMs = 0)); feed(e, 0.0, 0)
        assertNotNull(evaluated(feed(e, 3.0, 1000)).event)
        assertNull(evaluated(feed(e, 6.0, 2000)).event)
        feed(e, 6.0, 3000)
        assertNotNull(evaluated(feed(e, 9.0, 4000)).event)
    }
    @Test fun processing_clock_rollback_cannot_make_buffered_data_look_fresh() {
        val e = SpeedEventEngine("s")
        e.update(sample(10.0, 1000), 2000)
        assertEquals(SpeedRejection.INVALID_TIMESTAMP, reason(e.update(sample(13.0, 1500), 1500)))
        assertTrue(e.update(sample(13.0, 2000), 2000) is SpeedDetectionResult.Baseline)
    }
    @Test fun gap_at_maximum_is_evaluated_and_acceleration_cap_is_inclusive() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0)
        val r = evaluated(feed(e, 30.0, 2000))
        assertEquals(15.0, r.evidence.accelerationMps2, 0.0)
        assertNotNull(r.event)
    }
    @Test fun cooldown_suppressed_episode_is_not_emitted_late_when_time_expires() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0); feed(e, 3.0, 1000)
        feed(e, 3.0, 2000)
        assertEquals(EventSuppression.COOLDOWN, evaluated(feed(e, 6.0, 3000)).suppression)
        (4L..9L).forEach { second ->
            assertNull(evaluated(feed(e, (second - 1) * 3.0, second * 1000)).event)
        }
    }
    @Test fun mock_cooldown_does_not_suppress_a_confirmed_different_source() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0); feed(e, 3.0, 1000)
        e.update(sample(10.0, 2000, source = SignalSource.VEHICLE), 2000)
        val event = evaluated(e.update(sample(13.0, 3000, source = SignalSource.VEHICLE), 3000)).event!!
        assertEquals(SignalSource.VEHICLE, event.source)
        assertEquals("s:speed:2", event.eventId)
        // VEHICLE 표시는 입력 전달 시험일 뿐 실제 차량 수신/정책 검증이 아니다.
    }
    @Test fun configured_thresholds_change_detection_without_changing_the_engine() {
        val e = SpeedEventEngine("s", SpeedEventConfig(increaseThresholdMps2 = 4.0, decreaseThresholdMps2 = 5.0))
        feed(e, 10.0, 0)
        assertNull(evaluated(feed(e, 13.0, 1000)).event)
        assertNotNull(evaluated(feed(e, 17.0, 2000)).event)
        assertNull(evaluated(feed(e, 13.0, 3000)).event)
        assertNotNull(evaluated(feed(e, 8.0, 4000)).event)
    }
    @Test fun large_timestamps_do_not_overflow_interval_or_age_calculation() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, Long.MAX_VALUE - 1000)
        val event = evaluated(feed(e, 3.0, Long.MAX_VALUE)).event!!
        assertEquals(1000L, event.evidence.intervalMs)
        assertEquals(3.0, event.evidence.accelerationMps2, 0.0)
    }
    @Test fun reset_cannot_bypass_cooldown_or_allow_old_timestamp_replay() {
        val e = SpeedEventEngine("s"); feed(e, 0.0, 0); feed(e, 3.0, 1000)
        e.reset()
        assertEquals(SpeedRejection.NON_INCREASING_TIME, reason(feed(e, 0.0, 1000)))
        feed(e, 3.0, 2000)
        assertEquals(EventSuppression.COOLDOWN, evaluated(feed(e, 6.0, 3000)).suppression)
    }
    @Test fun separate_sessions_have_independent_baselines_and_ids() {
        val a = SpeedEventEngine("a"); val b = SpeedEventEngine("b")
        feed(a, 0.0, 0)
        assertTrue(feed(b, 50.0, 1000) is SpeedDetectionResult.Baseline)
        assertEquals("a:speed:1", evaluated(feed(a, 3.0, 1000)).event!!.eventId)
        assertEquals("b:speed:1", evaluated(feed(b, 53.0, 2000)).event!!.eventId)
    }    @Test fun default_decrease_threshold_is_inclusive_after_kmh_conversion() {
        val e = SpeedEventEngine("s")
        feed(e, 72.0, 0, SpeedUnit.KILOMETERS_PER_HOUR)
        val r = evaluated(feed(e, 60.0, 1000, SpeedUnit.KILOMETERS_PER_HOUR))
        assertEquals(SpeedEventType.RAPID_SPEED_DECREASE, r.event?.type)
        assertEquals(-12.0 / 3.6, r.evidence.accelerationMps2, 1e-12)
    }    @Test fun rounding_allowance_does_not_promote_a_real_below_threshold_change() {
        val e = SpeedEventEngine("s"); feed(e, 72.0, 0, SpeedUnit.KILOMETERS_PER_HOUR)
        val r = evaluated(feed(e, 60.00001, 1000, SpeedUnit.KILOMETERS_PER_HOUR))
        assertNull(r.event)
        assertTrue(r.evidence.roundingToleranceMps2 < 1e-10)
    }
    @Test fun numerically_unresolvable_configuration_inputs_fail_closed() {
        val e = SpeedEventEngine("s", SpeedEventConfig(maxSpeedMps = 1e300))
        feed(e, 1e300, 0)
        assertEquals(SpeedRejection.IMPLAUSIBLE_CHANGE, reason(feed(e, 1e300, 1000)))
    }}

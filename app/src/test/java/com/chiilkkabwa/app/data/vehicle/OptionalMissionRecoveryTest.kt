package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.mission.*
import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test

class OptionalMissionRecoveryTest {
    private val E = SignalSource.EMULATOR
    private fun toggle(index: Int, signal: Signal<Boolean>) =
        if (index == 0) VehicleSnapshot(wiperOn = signal) else VehicleSnapshot(lowBeamOn = signal)
    private fun angle(value: Float, at: Long, source: SignalSource = E) = VehicleSnapshot(steeringDeg = Signal.ok(value, source, at))

    @Test fun toggles_require_a_baseline_after_unknown_start_then_a_new_change() {
        for (index in 0..1) {
            val j = MissionCatalog.warmupOptional()[index]
            j.start(toggle(index, Signal.unknown(E)), 100)
            assertTrue(j.update(toggle(index, Signal.ok(true, E, 101)), 101) is Progress.InProgress)
            assertTrue(j.update(toggle(index, Signal.ok(false, E, 102)), 102) is Progress.Done)
        }
    }

    @Test fun every_invalid_toggle_state_discards_old_baseline_and_never_silently_skips() {
        for (index in 0..1) for (status in SignalStatus.entries.filter { it != SignalStatus.OK }) {
            val j = MissionCatalog.warmupOptional()[index]
            j.start(toggle(index, Signal.ok(false, E, 100)), 100)
            assertFalse(j.update(toggle(index, Signal.failed(status, E, 101)), 101) is Progress.Done)
            assertTrue(j.update(toggle(index, Signal.ok(true, E, 102)), 102) is Progress.InProgress)
            assertTrue(j.update(toggle(index, Signal.ok(false, E, 103)), 103) is Progress.Done)
        }
    }

    @Test fun future_and_pre_start_toggle_readings_do_not_complete_or_replace_baseline() {
        for (index in 0..1) {
            val j = MissionCatalog.warmupOptional()[index]
            j.start(toggle(index, Signal.ok(false, E, 99)), 100)
            assertTrue(j.update(toggle(index, Signal.ok(true, E, 98)), 101) is Progress.InProgress)
            assertTrue(j.update(toggle(index, Signal.ok(true, E, 1000)), 102) is Progress.InProgress)
            assertTrue(j.update(toggle(index, Signal.ok(true, E, 103)), 103) is Progress.Done)
        }
    }

    @Test fun invalid_steering_discards_hold_for_all_statuses_and_demands_new_observation() {
        for (status in SignalStatus.entries.filter { it != SignalStatus.OK }) {
            val j = SteeringCenterJudge()
            j.start(angle(0f, 100), 100); j.update(angle(0f, 101), 101)
            j.update(VehicleSnapshot(steeringDeg = Signal.failed(status, E, 1000)), 1000)
            assertTrue(j.update(angle(0f, 101), 3000) is Progress.InProgress)
            assertTrue(j.update(angle(0f, 3001), 3001) is Progress.InProgress)
            assertTrue(j.update(angle(0f, 5001), 5001) is Progress.Done)
        }
    }

    @Test fun nonfinite_future_and_old_steering_cannot_bridge_the_center_hold() {
        val invalid = listOf(Signal.ok(Float.NaN, E, 1000), Signal.ok(Float.POSITIVE_INFINITY, E, 1000),
            Signal.ok(0f, E, 99), Signal.ok(0f, E, 9000))
        for (signal in invalid) {
            val j = SteeringCenterJudge()
            j.start(angle(0f, 100), 100); j.update(angle(0f, 101), 101)
            assertTrue(j.update(VehicleSnapshot(steeringDeg = signal), 1000) is Progress.InProgress)
            assertTrue(j.update(angle(0f, 3000), 3000) is Progress.InProgress)
            assertTrue(j.update(angle(0f, 5000), 5000) is Progress.Done)
        }
    }

    @Test fun steering_source_change_cannot_combine_mock_and_emulator_hold() {
        val j = SteeringCenterJudge()
        j.start(angle(0f, 100), 100); j.update(angle(0f, 101), 101)
        assertTrue(j.update(angle(0f, 3000, SignalSource.MOCK), 3000) is Progress.InProgress)
        assertTrue(j.update(angle(0f, 5000, SignalSource.MOCK), 5000) is Progress.Done)
    }
}

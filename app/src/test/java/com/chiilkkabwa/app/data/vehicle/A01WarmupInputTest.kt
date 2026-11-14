package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.mission.*
import com.chiilkkabwa.app.core.safety.*
import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test

/** A01 contract tests: real Mock source/repository/judges, no Android UI or Pleos input claim. */
class A01WarmupInputTest {
    private val source = SignalSource.MOCK
    private fun belt(value: Boolean, at: Long) = VehicleSnapshot(driverBeltBuckled = Signal.ok(value, source, at))

    @Test fun unbuckled_start_rejects_a_true_observed_before_mission_start() {
        val judge = SeatBeltJudge()
        judge.start(belt(false, 90), 100)
        assertTrue(judge.update(belt(true, 95), 110) is Progress.InProgress)
        assertTrue(judge.update(belt(true, 111), 111) is Progress.Done)
    }

    @Test fun unknown_start_rejects_old_true_and_future_dated_true() {
        val judge = SeatBeltJudge()
        judge.start(VehicleSnapshot(), 100)
        assertTrue(judge.update(belt(true, 99), 101) is Progress.InProgress)
        assertTrue(judge.update(belt(true, 200), 102) is Progress.InProgress)
        assertTrue(judge.update(belt(true, 103), 103) is Progress.Done)
    }

    @Test fun already_buckled_start_needs_no_unbuckling_but_lost_state_cannot_reuse_initial_true() {
        val judge = SeatBeltJudge()
        val initial = belt(true, 90)
        judge.start(initial, 100)
        assertTrue(judge.update(initial, 101) is Progress.Done)
        assertEquals(Progress.Unavailable(SignalStatus.DISCONNECTED), judge.update(
            initial.copy(driverBeltBuckled = Signal.failed(SignalStatus.DISCONNECTED, source, 102)), 102))
        assertTrue(judge.update(initial, 103) is Progress.InProgress)
        assertTrue(judge.update(belt(true, 104), 104) is Progress.Done)
    }

    @Test fun invalid_belt_states_never_succeed_even_with_true_payload() {
        for (status in SignalStatus.entries.filter { it != SignalStatus.OK }) {
            val judge = SeatBeltJudge()
            val s = VehicleSnapshot(driverBeltBuckled = Signal(true, status, source, 100))
            judge.start(s, 100)
            assertFalse(status.name, judge.update(s, 101) is Progress.Done)
        }
    }

    @Test fun parking_brake_table_requires_actual_usable_true_even_with_park_and_zero_speed() {
        val parked = VehicleSnapshot(gear = Signal.ok(Gear.PARK, source, 100),
            uxRestricted = Signal.ok(false, source, 100))
        assertEquals(GateState.Ready, SafetyGate.evaluate(parked.copy(parkingBrakeOn = Signal.ok(true, source, 100))))
        assertEquals(GateState.Locked(LockReason.PARKING_BRAKE_OFF), SafetyGate.evaluate(
            parked.copy(parkingBrakeOn = Signal.ok(false, source, 100))))
        for (status in SignalStatus.entries.filter { it != SignalStatus.OK }) {
            val s = parked.copy(parkingBrakeOn = Signal(true, status, source, 100),
                speed = decodeSpeed(0f, SignalStatus.OK, 100, 100, source, "mock", 0))
            assertTrue(status.name, SafetyGate.evaluate(s) is GateState.Locked)
        }
    }


    @Test fun future_belt_timestamp_cannot_confirm_current_buckle() {
        val judge = SeatBeltJudge(); judge.start(belt(false, 90), 100)
        assertTrue(judge.update(belt(true, 200), 101) is Progress.InProgress)
    }

    @Test fun brake_interruption_discards_partial_press_for_every_invalid_state() {
        for (status in SignalStatus.entries.filter { it != SignalStatus.OK }) {
            val j = BrakePressReleaseJudge()
            fun snap(value: Boolean, at: Long) = VehicleSnapshot(brakePressed = Signal.ok(value, source, at))
            j.start(snap(false, 0), 1)
            j.update(snap(true, 2), 2)
            j.update(VehicleSnapshot(brakePressed = Signal.failed(status, source, 3)), 3)
            assertTrue(status.name, j.update(snap(false, 4), 4) is Progress.InProgress)
            j.update(snap(true, 5), 5)
            assertTrue(j.update(snap(false, 6), 6) is Progress.Done)
        }
    }

    @Test fun indicator_interruption_discards_partial_sequence_for_every_invalid_state() {
        for (status in SignalStatus.entries.filter { it != SignalStatus.OK }) {
            val j = TurnSignalJudge()
            fun snap(v: TurnSignalState, at: Long) = VehicleSnapshot(turnSignal = Signal.ok(v, source, at))
            j.start(snap(TurnSignalState.NONE, 0), 1)
            j.update(snap(TurnSignalState.LEFT, 2), 2)
            j.update(VehicleSnapshot(turnSignal = Signal.failed(status, source, 3)), 3)
            j.update(snap(TurnSignalState.RIGHT, 4), 4)
            assertTrue(status.name, j.update(snap(TurnSignalState.NONE, 5), 5) is Progress.InProgress)
            j.update(snap(TurnSignalState.LEFT, 6), 6)
            j.update(snap(TurnSignalState.RIGHT, 7), 7)
            assertTrue(j.update(snap(TurnSignalState.NONE, 8), 8) is Progress.Done)
        }
    }

    private class MockFlow {
        val mock = MockVehicleSource(speedNowNanos = { 100L }, speedSessionId = { "a01-mock-test" })
        val repo = VehicleRepository(listOf(mock))
        val runner = MissionRunner(MissionCatalog.warmupRequired() + MissionCatalog.warmupOptional())
        var gate: GateState = GateState.Locked(LockReason.UNKNOWN_STATE)
        init { repo.start() }
        fun start() {
            gate = SafetyGate.evaluate(repo.snapshot.value)
            check(gate is GateState.Ready)
            runner.start(repo.snapshot.value, System.currentTimeMillis())
        }
        // Mirrors the existing owner's pause/resume contract, without modifying B/C UI.
        fun consume(): RunnerState {
            val s = repo.snapshot.value
            val next = SafetyGate.evaluate(s)
            val now = System.currentTimeMillis()
            if (next is GateState.Locked && gate is GateState.Ready) runner.pause()
            if (next is GateState.Ready && gate is GateState.Locked) runner.resume(s, now)
            gate = next
            return if (next is GateState.Ready) runner.update(s, now) else runner.state()
        }
        fun reachBrake() { mock.setBelt(true); start(); consume() }
    }

    @Test fun full_mock_warmup_including_optional_missions_keeps_mock_source() {
        val f = MockFlow()
        try {
            f.reachBrake()
            assertEquals(MissionId.SEAT_BELT, f.runner.state().results.single().id)
            f.mock.setBrake(true); f.consume()
            f.mock.setBrake(false); f.consume()
            f.mock.setTurn(TurnSignalState.RIGHT); f.consume() // wrong initial order cannot complete
            assertEquals(2, f.runner.state().results.size)
            f.mock.setTurn(TurnSignalState.LEFT); f.consume()
            f.mock.setTurn(TurnSignalState.RIGHT); f.consume()
            f.mock.setTurn(TurnSignalState.NONE); f.consume()
            f.mock.setWiper(true); f.consume()
            f.mock.setLowBeam(true); f.consume()
            f.mock.setSteering(0f)
            val at = maxOf(System.currentTimeMillis(), f.repo.snapshot.value.steeringDeg.receivedAtMs)
            f.runner.update(f.repo.snapshot.value, at)
            val done = f.runner.update(f.repo.snapshot.value, at + 2_000)
            assertTrue(done.finished)
            assertEquals(6, done.results.size)
            assertTrue(done.results.all { it.source == SignalSource.MOCK })
            assertEquals(6, done.results.map { it.id }.distinct().size)
        } finally { f.repo.stop() }
    }

    @Test fun brake_pressed_before_mission_requires_a_new_press_release() {
        val f = MockFlow()
        try {
            f.mock.setBrake(true); f.reachBrake()
            f.mock.setBrake(false); f.consume()
            assertEquals(1, f.runner.state().results.size)
            f.mock.setBrake(true); f.consume()
            f.mock.setBrake(false); f.consume()
            assertEquals(2, f.runner.state().results.size)
        } finally { f.repo.stop() }
    }

    @Test fun gate_loss_pauses_and_requires_new_brake_cycle_after_reparking() {
        val f = MockFlow()
        try {
            f.reachBrake(); f.mock.setBrake(true); f.consume()
            f.mock.setParkingBrake(false); f.consume()
            f.mock.setBrake(false); f.consume()
            assertTrue(f.gate is GateState.Locked)
            assertEquals(1, f.runner.state().results.size)
            f.mock.setParkingBrake(true); f.consume()
            assertEquals(1, f.runner.state().results.size)
            f.mock.setBrake(true); f.consume(); f.mock.setBrake(false); f.consume()
            assertEquals(2, f.runner.state().results.size)
        } finally { f.repo.stop() }
    }

    @Test fun signal_loss_is_unavailable_and_never_a_success_or_silent_skip() {
        val f = MockFlow()
        try {
            f.reachBrake()
            f.mock.breakSignal(SignalStatus.DISCONNECTED)
            assertEquals(Progress.Unavailable(SignalStatus.DISCONNECTED), f.consume().progress)
            assertEquals(1, f.runner.state().results.size)
            f.mock.setBrake(false); f.consume()
            f.mock.setBrake(true); f.consume(); f.mock.setBrake(false); f.consume()
            assertEquals(2, f.runner.state().results.size)
        } finally { f.repo.stop() }
    }

    @Test fun repository_stop_restart_discards_values_and_restarts_incomplete_mission() {
        val f = MockFlow()
        try {
            f.reachBrake(); f.mock.setBrake(true); f.consume()
            f.repo.stop(); f.consume()
            assertEquals(SignalStatus.DISCONNECTED, f.repo.snapshot.value.driverBeltBuckled.status)
            assertTrue(f.gate is GateState.Locked)
            f.repo.start(); f.consume()
            assertEquals(1, f.runner.state().results.size)
            f.mock.setBrake(true); f.consume(); f.mock.setBrake(false); f.consume()
            assertEquals(2, f.runner.state().results.size)
        } finally { f.repo.stop() }
    }
}

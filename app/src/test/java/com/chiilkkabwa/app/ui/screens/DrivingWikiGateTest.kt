package com.chiilkkabwa.app.ui.screens

import com.chiilkkabwa.app.core.safety.GateState
import com.chiilkkabwa.app.core.safety.LockReason
import com.chiilkkabwa.app.core.session.Phase
import com.chiilkkabwa.app.core.session.SessionState
import com.chiilkkabwa.app.core.signal.Gear
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSnapshot
import com.chiilkkabwa.app.ui.UiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingWikiGateTest {
    private val parked = VehicleSnapshot(
        gear = Signal.ok(Gear.PARK, SignalSource.MOCK, 1L),
        parkingBrakeOn = Signal.ok(true, SignalSource.MOCK, 1L),
        uxRestricted = Signal.ok(false, SignalSource.MOCK, 1L),
    )
    private val ready = UiState(
        session = SessionState(phase = Phase.READY),
        gate = GateState.Ready,
        snapshot = parked,
    )

    @Test fun requiresReadyScreenAndConfirmedParking() {
        assertTrue(canOpenDrivingWiki(ready))
        assertFalse(canOpenDrivingWiki(ready.copy(session = SessionState(phase = Phase.DRIVING_LOCK))))
        assertFalse(canOpenDrivingWiki(ready.copy(gate = GateState.Locked(LockReason.UNKNOWN_STATE))))
    }

    @Test fun failsClosedEvenWithPreviousReadyGate() {
        val invalidSnapshots = listOf(
            parked.copy(gear = Signal.ok(Gear.DRIVE, SignalSource.MOCK, 2L)),
            parked.copy(parkingBrakeOn = Signal.ok(false, SignalSource.MOCK, 2L)),
            parked.copy(uxRestricted = Signal.ok(true, SignalSource.MOCK, 2L)),
            parked.copy(gear = Signal.unknown()),
            parked.copy(parkingBrakeOn = Signal.failed(SignalStatus.UNSUPPORTED, SignalSource.MOCK, 2L)),
            parked.copy(uxRestricted = Signal.failed(SignalStatus.DISCONNECTED, SignalSource.MOCK, 2L)),
        )
        invalidSnapshots.forEach { assertFalse(canOpenDrivingWiki(ready.copy(snapshot = it))) }
    }
}

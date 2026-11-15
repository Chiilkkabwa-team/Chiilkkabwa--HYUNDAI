package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.signal.*
import com.chiilkkabwa.app.core.safety.*
import org.junit.Assert.*
import org.junit.Test

class VehicleRepositoryLifecycleTest {
    private class Source : VehicleSource {
        val sinks = mutableListOf<SnapshotSink>()
        override fun start(sink: SnapshotSink) { sinks += sink }
        override fun stop() = Unit
        fun parked(sink: SnapshotSink = sinks.last()) = sink.update { it.copy(
            gear = Signal.ok(Gear.PARK, SignalSource.EMULATOR, 100),
            parkingBrakeOn = Signal.ok(true, SignalSource.EMULATOR, 100),
            uxRestricted = Signal.ok(false, SignalSource.EMULATOR, 100),
            driverBeltBuckled = Signal.ok(true, SignalSource.EMULATOR, 100),
        ) }
    }

    @Test fun stop_locks_and_discards_values_without_relabeling_as_mock() {
        val source = Source(); val repo = VehicleRepository(listOf(source))
        repo.start(); source.parked()
        assertTrue(SafetyGate.evaluate(repo.snapshot.value, GatePolicy()) is GateState.Ready)
        repo.stop()
        assertTrue(SafetyGate.evaluate(repo.snapshot.value, GatePolicy()) is GateState.Locked)
        assertEquals(SignalStatus.DISCONNECTED, repo.snapshot.value.gear.status)
        assertEquals(SignalSource.EMULATOR, repo.snapshot.value.gear.source)
        assertNull(repo.snapshot.value.driverBeltBuckled.value)
    }

    @Test fun old_source_callback_cannot_unlock_stopped_or_restarted_repository() {
        val source = Source(); val repo = VehicleRepository(listOf(source))
        repo.start(); val old = source.sinks.single()
        repo.stop(); source.parked(old)
        assertFalse(repo.snapshot.value.gear.isUsable)
        repo.start(); source.parked(old)
        assertFalse(repo.snapshot.value.gear.isUsable)
        source.parked()
        assertTrue(repo.snapshot.value.gear.isUsable)
    }
}

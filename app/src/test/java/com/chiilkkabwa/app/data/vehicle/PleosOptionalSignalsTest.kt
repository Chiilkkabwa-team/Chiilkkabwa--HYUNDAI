package com.chiilkkabwa.app.data.vehicle

import ai.pleos.playground.vehicle.api.model.area.*
import ai.pleos.playground.vehicle.api.model.capability.*
import ai.pleos.playground.vehicle.constant.area.*
import ai.pleos.playground.vehicle.constant.control.*
import com.chiilkkabwa.app.core.signal.*
import com.chiilkkabwa.app.core.safety.*
import com.chiilkkabwa.app.core.mission.*
import org.junit.Assert.*
import org.junit.Test

/** Fake IPC -> real session/snapshot/judges. No emulator or real vehicle success claim. */
class PleosOptionalSignalsTest {
    private val optional = listOf(PleosSensor.WIPER, PleosSensor.LOW_BEAM, PleosSensor.PASSENGER,
        PleosSensor.TRIP_DISTANCE, PleosSensor.TRIP_TIME)
    private val E = SignalSource.EMULATOR
    private class Client : PleosReadClient {
        val checks = mutableListOf<PleosSensor>()
        val events = mutableMapOf<PleosSensor, Pair<(Any?) -> Unit, (Exception) -> Unit>>()
        val pending = mutableMapOf<PleosSensor, (Any?) -> Unit>()
        val removed = mutableListOf<PleosSensor>()
        val unsupported = mutableSetOf<PleosSensor>()
        var deferred = false
        var subscribeFailure: PleosSensor? = null
        var permissionDenied: PleosSensor? = null
        var released = 0
        override fun initialize() = Unit
        override fun check(sensor: PleosSensor, complete: (Boolean) -> Unit, failed: (Exception) -> Unit) {
            checks += sensor
            if (sensor == permissionDenied) failed(SecurityException()) else complete(sensor !in unsupported)
        }
        override fun read(sensor: PleosSensor, complete: (Any?) -> Unit, failed: (Exception) -> Unit) {
            if (deferred) pending[sensor] = complete else complete(null)
        }
        override fun subscribe(sensor: PleosSensor, updated: (Any?) -> Unit, failed: (Exception) -> Unit): () -> Unit {
            if (sensor == subscribeFailure) throw Exception("ERROR_SERVICE_CONNECTION_FAILURE")
            events[sensor] = updated to failed
            return { removed += sensor }
        }
        override fun release() { released++ }
        fun emit(sensor: PleosSensor, raw: Any?) = events.getValue(sensor).first(raw)
    }
    private inner class Fixture {
        var now = 1000L
        val clients = mutableListOf<Client>()
        var configure: (Client) -> Unit = {}
        var snapshot = VehicleSnapshot(gear = Signal.ok(Gear.PARK, E, now),
            parkingBrakeOn = Signal.ok(true, E, now), uxRestricted = Signal.ok(false, E, now))
        val session = PleosSignalSession({ Client().also { configure(it); clients += it } }, E, true, { now })
        val client get() = clients.last()
        fun start() = session.start { transform -> snapshot = transform(snapshot) }
        fun signal(sensor: PleosSensor): Signal<*> = when(sensor) {
            PleosSensor.WIPER -> snapshot.wiperOn
            PleosSensor.LOW_BEAM -> snapshot.lowBeamOn
            PleosSensor.PASSENGER -> snapshot.passengerSeated
            PleosSensor.TRIP_DISTANCE -> snapshot.tripDistanceKm
            PleosSensor.TRIP_TIME -> snapshot.tripTimeSec
            else -> error("not optional")
        }
    }
    private fun raw(sensor: PleosSensor): Any = when(sensor) {
        PleosSensor.WIPER -> WiperState.ON
        PleosSensor.LOW_BEAM -> LightState.ON
        PleosSensor.PASSENGER -> true
        PleosSensor.TRIP_DISTANCE -> 1234.5f
        PleosSensor.TRIP_TIME -> 3
        else -> error("not optional")
    }

    @Test fun wiper_capability_requires_exact_front_area_read_access_and_known_off_on() {
        val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val rear = WindowArea(WindowValue.REAR_WINDSHIELD)
        val both = WindowArea(WindowValue.FRONT_WINDSHIELD, WindowValue.REAR_WINDSHIELD)
        val states = listOf(WiperState.OFF, WiperState.ON)
        assertTrue(supportsFrontWiper(CapabilityConfig(AccessType.READ), mapOf(front to states)))
        for (area in listOf(rear, both)) assertFalse(supportsFrontWiper(CapabilityConfig(AccessType.READ), mapOf(area to states)))
        assertFalse(supportsFrontWiper(CapabilityConfig(AccessType.WRITE), mapOf(front to states)))
        assertFalse(supportsFrontWiper(CapabilityConfig(AccessType.READ), mapOf(front to listOf(WiperState.OTHER))))
        assertFalse(supportsFrontWiper(CapabilityConfig(AccessType.READ), emptyMap()))
    }

    @Test fun low_beam_capability_never_substitutes_daytime_running_for_low_beam() {
        assertTrue(supportsLowBeam(CapabilityConfig(AccessType.READ), listOf(LightState.ON, LightState.OFF)))
        assertFalse(supportsLowBeam(CapabilityConfig(AccessType.READ), listOf(LightState.DAYTIME_RUNNING)))
        assertFalse(supportsLowBeam(CapabilityConfig(AccessType.NONE), listOf(LightState.ON, LightState.OFF)))
        assertNull(pleosLowBeamOn(LightState.DAYTIME_RUNNING))
        assertEquals(true, pleosLowBeamOn(LightState.ON))
        assertEquals(false, pleosLowBeamOn(LightState.OFF))
        for (raw in listOf(null, true, "ON", 1)) assertNull(pleosLowBeamOn(raw))
    }

    @Test fun passenger_requires_exact_front_right_and_read_access() {
        val right = SeatArea(SeatValue.ROW_1_RIGHT)
        val left = SeatArea(SeatValue.ROW_1_LEFT)
        assertTrue(supportsFrontPassenger(CapabilityConfig(AccessType.READ), listOf(right)))
        assertFalse(supportsFrontPassenger(CapabilityConfig(AccessType.WRITE), listOf(right)))
        assertFalse(supportsFrontPassenger(CapabilityConfig(AccessType.READ), listOf(left)))
        assertFalse(supportsFrontPassenger(CapabilityConfig(AccessType.READ), listOf(SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT))))
    }

    @Test fun optional_check_permission_and_registration_errors_do_not_break_core_channels() {
        for (sensor in optional) for (denied in listOf(true, false)) {
            val f = Fixture()
            f.configure = { if (denied) it.permissionDenied = sensor else it.subscribeFailure = sensor }
            f.start()
            assertEquals(if (denied) SignalStatus.PERMISSION_DENIED else SignalStatus.DISCONNECTED, f.signal(sensor).status)
            assertFalse(f.client.events.containsKey(sensor))
            f.client.emit(PleosSensor.BELT, true)
            assertEquals(true, f.snapshot.driverBeltBuckled.value)
        }
    }

    @Test fun new_optional_callback_wins_over_older_initial_read() {
        val f = Fixture(); f.configure = { it.deferred = true }; f.start()
        optional.forEach { sensor ->
            f.client.emit(sensor, raw(sensor)); f.client.pending.getValue(sensor)(null)
            assertEquals(SignalStatus.OK, f.signal(sensor).status)
            assertNotNull(f.signal(sensor).value)
        }
    }

    @Test fun wiper_uses_actual_state_and_rejects_service_unknown_and_switch_mode() {
        assertEquals(true, pleosWiperOn(WiperState.ON))
        assertEquals(false, pleosWiperOn(WiperState.OFF))
        for (raw in listOf(null, WiperState.OTHER, WiperState.SERVICE, "ON", true, 1)) assertNull(pleosWiperOn(raw))
    }

    @Test fun trip_converts_documented_meters_and_minutes_without_type_or_overflow_guessing() {
        assertEquals(1.2345f, pleosTripKm(1234.5f)!!, 0.00001f)
        assertEquals(0f, pleosTripKm(0f)!!, 0f)
        assertEquals(180L, pleosTripSeconds(3))
        assertEquals(Int.MAX_VALUE.toLong() * 60L, pleosTripSeconds(Int.MAX_VALUE))
        for (raw in listOf(null, -1f, Float.NaN, Float.POSITIVE_INFINITY, 1000, 1000.0)) assertNull(pleosTripKm(raw))
        for (raw in listOf(null, -1, 3L, 3f, "3")) assertNull(pleosTripSeconds(raw))
    }

    @Test fun optional_observations_preserve_source_time_gate_and_core_channel_order() {
        val f = Fixture(); f.start()
        assertEquals(listOf(PleosSensor.BELT, PleosSensor.BRAKE, PleosSensor.TURN), f.client.checks.take(3))
        optional.forEach { sensor -> f.client.emit(sensor, raw(sensor)); assertEquals(E, f.signal(sensor).source); assertEquals(1000L, f.signal(sensor).receivedAtMs); assertEquals(SignalStatus.OK, f.signal(sensor).status) }
        assertEquals(true, f.snapshot.wiperOn.value)
        assertEquals(true, f.snapshot.lowBeamOn.value)
        assertEquals(1.2345f, f.snapshot.tripDistanceKm.value!!, 0.00001f)
        assertEquals(180L, f.snapshot.tripTimeSec.value)
        assertEquals(GateState.Ready, SafetyGate.evaluate(f.snapshot))
        assertNull(f.snapshot.steeringDeg.value) // front-wheel angle is NOT a steering wheel rotation
        assertNull(f.snapshot.carDimensions.value) // no invented dimensions or VIN read
    }

    @Test fun capability_false_never_registers_optional_sensor_and_core_keeps_working() {
        for (sensor in optional) {
            val f = Fixture(); f.configure = { it.unsupported += sensor }; f.start()
            assertFalse(f.client.events.containsKey(sensor))
            assertEquals(SignalStatus.UNSUPPORTED, f.signal(sensor).status)
            f.client.emit(PleosSensor.BELT, true)
            assertEquals(true, f.snapshot.driverBeltBuckled.value)
        }
    }

    @Test fun null_and_wrong_type_invalidate_previous_optional_values() {
        for (sensor in optional) {
            val f = Fixture(); f.start(); f.client.emit(sensor, raw(sensor)); f.client.emit(sensor, "bad")
            assertEquals(SignalStatus.UNKNOWN, f.signal(sensor).status); assertNull(f.signal(sensor).value)
            f.client.emit(sensor, null); assertEquals(SignalStatus.UNKNOWN, f.signal(sensor).status)
        }
    }

    @Test fun optional_permission_or_connection_failure_clears_value_and_blocks_late_event() {
        for (sensor in optional) for ((error, status) in listOf(SecurityException() to SignalStatus.PERMISSION_DENIED,
            Exception("ERROR_SERVICE_CONNECTION_FAILURE") to SignalStatus.DISCONNECTED)) {
            val f = Fixture(); f.start(); f.client.emit(sensor, raw(sensor))
            f.client.events.getValue(sensor).second(error); f.client.emit(sensor, raw(sensor))
            assertEquals(status, f.signal(sensor).status); assertNull(f.signal(sensor).value)
            f.client.emit(PleosSensor.BRAKE, false); assertEquals(false, f.snapshot.brakePressed.value)
        }
    }

    @Test fun all_new_listeners_unregister_release_and_old_session_cannot_change_restart() {
        val f = Fixture(); f.start(); val old = f.client
        optional.forEach { old.emit(it, raw(it)) }; f.session.stop()
        assertEquals(PleosSensor.entries.toSet(), old.removed.toSet()); assertEquals(1, old.released)
        optional.forEach { assertEquals(SignalStatus.DISCONNECTED, f.signal(it).status) }
        f.now += 100; f.start()
        optional.forEach { old.emit(it, raw(it)); assertEquals(SignalStatus.UNKNOWN, f.signal(it).status) }
        assertEquals(2, f.clients.size)
    }

    @Test fun optional_timeout_cannot_be_restored_by_late_read_or_callback() {
        val f = Fixture(); f.configure = { it.deferred = true }; f.start()
        f.now += 5001; f.session.tick()
        optional.forEach { f.client.pending.getValue(it)(raw(it)); f.client.emit(it, raw(it)); assertEquals(SignalStatus.DISCONNECTED, f.signal(it).status) }
    }

    @Test fun wiper_and_low_beam_callbacks_feed_existing_optional_judges() {
        val f = Fixture(); f.start()
        data class Case(val sensor: PleosSensor, val initial: Any, val on: Any, val index: Int)
        for ((channel, initial, on, index) in listOf(
            Case(PleosSensor.WIPER, WiperState.OFF, WiperState.ON, 0),
            Case(PleosSensor.LOW_BEAM, LightState.OFF, LightState.ON, 1))) {
            f.client.emit(channel, initial)
            val judge = MissionCatalog.warmupOptional()[index]
            judge.start(f.snapshot, f.now)
            assertTrue(judge.update(f.snapshot, f.now) is Progress.InProgress)
            f.now++; f.client.emit(channel, on)
            val done = judge.update(f.snapshot, f.now)
            assertTrue(done is Progress.Done); assertEquals(E, (done as Progress.Done).source)
        }
    }
}

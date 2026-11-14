package com.chiilkkabwa.app.data.vehicle

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test

/** SDK IPC만 대역으로 바꾸고 실제 상태/수명 관리 코드를 실행한다. 실수신 시험이 아니다. */
class PleosSignalSessionTest {
    private class FakeClient : PleosReadClient {
        val checks = mutableMapOf<PleosSensor, Pair<(Boolean) -> Unit, (Exception) -> Unit>>()
        val reads = mutableMapOf<PleosSensor, Pair<(Any?) -> Unit, (Exception) -> Unit>>()
        val events = mutableMapOf<PleosSensor, Pair<(Any?) -> Unit, (Exception) -> Unit>>()
        val removed = mutableListOf<PleosSensor>()
        var released = 0
        var throwOnRemove = false
        override fun initialize() = Unit
        override fun check(sensor: PleosSensor, complete: (Boolean) -> Unit, failed: (Exception) -> Unit) {
            checks[sensor] = complete to failed
        }
        override fun read(sensor: PleosSensor, complete: (Any?) -> Unit, failed: (Exception) -> Unit) {
            reads[sensor] = complete to failed
        }
        override fun subscribe(sensor: PleosSensor, updated: (Any?) -> Unit, failed: (Exception) -> Unit): () -> Unit {
            events[sensor] = updated to failed
            return { removed += sensor; if (throwOnRemove) throw SecurityException() }
        }
        override fun release() { released++ }
        fun supportAll() { PleosSensor.entries.forEach { checks.getValue(it).first(true) } }
        fun settleReads() { reads.values.toList().forEach { it.first(null) } }
    }
    private class Fixture(enabled: Boolean = true) {
        val clients = mutableListOf<FakeClient>()
        var snapshot = VehicleSnapshot()
        var time = 100L
        val session = PleosSignalSession(
            { FakeClient().also { clients += it } }, SignalSource.EMULATOR, enabled, { time }
        )
        val client get() = clients.last()
        fun start() = session.start { transform -> snapshot = transform(snapshot) }
    }

    @Test fun default_disabled_never_creates_sdk_or_mock_success() {
        val f = Fixture(false); f.start()
        assertTrue(f.clients.isEmpty())
        assertEquals(SignalStatus.UNSUPPORTED, f.snapshot.driverBeltBuckled.status)
        assertNull(f.snapshot.brakePressed.value)
        assertEquals(SignalSource.EMULATOR, f.snapshot.turnSignal.source)
    }

    @Test fun real_vehicle_and_release_are_blocked_even_with_opt_in() {
        assertFalse(allowPleosSignals(true, true, SignalSource.VEHICLE))
        assertFalse(allowPleosSignals(false, true, SignalSource.EMULATOR))
        assertFalse(allowPleosSignals(true, false, SignalSource.EMULATOR))
        assertFalse(allowPleosSignals(true, true, SignalSource.MOCK))
        assertTrue(allowPleosSignals(true, true, SignalSource.EMULATOR))
    }

    @Test fun driver_capability_requires_read_access_and_exact_front_left_area() {
        val left = SeatArea(SeatValue.ROW_1_LEFT)
        val right = SeatArea(SeatValue.ROW_1_RIGHT)
        val both = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)
        assertTrue(supportsLeftDriver(CapabilityConfig(AccessType.READ), listOf(left)))
        assertTrue(supportsLeftDriver(CapabilityConfig(AccessType.READ_WRITE), listOf(left)))
        assertFalse(supportsLeftDriver(CapabilityConfig(AccessType.WRITE), listOf(left)))
        assertFalse(supportsLeftDriver(CapabilityConfig(AccessType.NONE), listOf(left)))
        assertFalse(supportsLeftDriver(CapabilityConfig(AccessType.READ), emptyList()))
        assertFalse(supportsLeftDriver(CapabilityConfig(AccessType.READ), listOf(right)))
        assertFalse(supportsLeftDriver(CapabilityConfig(AccessType.READ), listOf(both)))
    }

    @Test fun checks_in_belt_brake_turn_order_and_records_typed_observations() {
        val f = Fixture(); f.start()
        assertEquals(listOf(PleosSensor.BELT, PleosSensor.BRAKE, PleosSensor.TURN), f.client.checks.keys.take(3))
        f.client.supportAll()
        f.client.events.getValue(PleosSensor.BELT).first(true)
        f.client.events.getValue(PleosSensor.BRAKE).first(false)
        f.client.events.getValue(PleosSensor.TURN).first(TurnSignalValue.LEFT)
        assertEquals(true, f.snapshot.driverBeltBuckled.value)
        assertEquals(false, f.snapshot.brakePressed.value)
        assertEquals(TurnSignalState.LEFT, f.snapshot.turnSignal.value)
        assertEquals(100L, f.snapshot.turnSignal.receivedAtMs)
        assertEquals(SignalSource.EMULATOR, f.snapshot.driverBeltBuckled.source)
        assertEquals(SignalStatus.UNKNOWN, f.snapshot.gear.status)
        assertEquals(SignalStatus.UNKNOWN, f.snapshot.parkingBrakeOn.status)
    }

    @Test fun unsupported_capability_does_not_read_or_register_and_other_sensors_continue() {
        val f = Fixture(); f.start()
        f.client.checks.getValue(PleosSensor.BELT).first(false)
        f.client.checks.getValue(PleosSensor.BRAKE).first(true)
        assertFalse(f.client.reads.containsKey(PleosSensor.BELT))
        assertFalse(f.client.events.containsKey(PleosSensor.BELT))
        assertEquals(SignalStatus.UNSUPPORTED, f.snapshot.driverBeltBuckled.status)
        assertTrue(f.client.events.containsKey(PleosSensor.BRAKE))
    }

    @Test fun null_and_wrong_type_never_become_valid_or_leave_previous_success() {
        val f = Fixture(); f.start(); f.client.supportAll()
        val belt = f.client.events.getValue(PleosSensor.BELT).first
        belt(true); belt("true")
        assertEquals(SignalStatus.UNKNOWN, f.snapshot.driverBeltBuckled.status)
        assertNull(f.snapshot.driverBeltBuckled.value)
        f.client.events.getValue(PleosSensor.BRAKE).first(null)
        f.client.events.getValue(PleosSensor.TURN).first("HAZARD")
        assertEquals(SignalStatus.UNKNOWN, f.snapshot.brakePressed.status)
        assertEquals(SignalStatus.UNKNOWN, f.snapshot.turnSignal.status)
    }

    @Test fun errors_map_to_explicit_safe_states() {
        val expected = mapOf(
            "ERROR_SECURITY_PERMISSION" to SignalStatus.PERMISSION_DENIED,
            "ERROR_NOT_SUPPORTED_PROPERTY" to SignalStatus.UNSUPPORTED,
            "ERROR_NOT_SUPPORTED_AREA" to SignalStatus.UNSUPPORTED,
            "ERROR_NOT_SUPPORTED_VALUE_TYPE" to SignalStatus.UNKNOWN,
            "ERROR_SERVICE_CONNECTION_FAILURE" to SignalStatus.DISCONNECTED,
            "ERROR_INTERNAL_SERVICE" to SignalStatus.DISCONNECTED,
            "ERROR_EMPTY_RESULT" to SignalStatus.UNKNOWN,
            "ERROR_DRIVING_RESTRICTED" to SignalStatus.UNKNOWN,
        )
        expected.forEach { (code, status) ->
            val f = Fixture(); f.start()
            f.client.checks.getValue(PleosSensor.BELT).second(Exception(code))
            assertEquals(code, status, f.snapshot.driverBeltBuckled.status)
            assertNull(f.snapshot.driverBeltBuckled.value)
        }
        assertEquals(SignalStatus.PERMISSION_DENIED, pleosFailureStatus(SecurityException()))
        assertEquals(SignalStatus.UNKNOWN, pleosFailureStatus(IllegalArgumentException()))
    }

    @Test fun event_wins_over_older_initial_read_even_when_read_fails() {
        val f = Fixture(); f.start(); f.client.supportAll()
        f.client.events.getValue(PleosSensor.BELT).first(true)
        f.client.reads.getValue(PleosSensor.BELT).first(false)
        assertEquals(true, f.snapshot.driverBeltBuckled.value)
        f.client.events.getValue(PleosSensor.BRAKE).first(true)
        f.client.reads.getValue(PleosSensor.BRAKE).second(SecurityException())
        assertEquals(true, f.snapshot.brakePressed.value)
    }

    @Test fun listener_failure_clears_value_and_rejects_delayed_initial_read() {
        val f = Fixture(); f.start(); f.client.supportAll()
        f.client.events.getValue(PleosSensor.BRAKE).first(true)
        f.client.events.getValue(PleosSensor.BRAKE).second(SecurityException())
        f.client.reads.getValue(PleosSensor.BRAKE).first(true)
        assertEquals(SignalStatus.PERMISSION_DENIED, f.snapshot.brakePressed.status)
        assertNull(f.snapshot.brakePressed.value)
    }

    @Test fun stop_invalidates_values_unregisters_all_despite_error_and_drains_before_release() {
        val f = Fixture(); f.start(); f.client.supportAll()
        val old = f.client
        old.events.getValue(PleosSensor.BELT).first(true)
        old.throwOnRemove = true
        f.session.stop()
        assertEquals(SignalStatus.DISCONNECTED, f.snapshot.driverBeltBuckled.status)
        assertEquals(PleosSensor.entries.size, old.removed.size)
        assertEquals(0, old.released)
        old.events.getValue(PleosSensor.BELT).first(true)
        assertNull(f.snapshot.driverBeltBuckled.value)
        old.settleReads()
        assertEquals(1, old.released)
        f.session.stop()
        assertEquals(1, old.released)
    }

    @Test fun stop_during_capability_cannot_register_late_and_old_session_cannot_update_restart() {
        val f = Fixture(); f.start(); val old = f.client
        f.session.stop()
        old.supportAll()
        assertTrue(old.events.isEmpty())
        assertEquals(1, old.released)
        f.start()
        assertEquals(SignalStatus.UNKNOWN, f.snapshot.driverBeltBuckled.status)
        f.client.supportAll()
        f.client.events.getValue(PleosSensor.BELT).first(true)
        old.checks.getValue(PleosSensor.BELT).second(SecurityException())
        assertEquals(true, f.snapshot.driverBeltBuckled.value)
    }

    @Test fun never_replying_sdk_cannot_allocate_more_clients_on_repeated_resume() {
        val f = Fixture(); f.start(); val old = f.client
        repeat(20) { f.session.stop(); f.start(); f.time += 10_000; f.session.tick() }
        assertEquals(1, f.clients.size)
        assertEquals(SignalStatus.DISCONNECTED, f.snapshot.driverBeltBuckled.status)
        assertEquals(0, old.released) // 공식 문서: 진행 중 비동기 callback 전 release 금지.
        old.supportAll()
        assertEquals(1, old.released)
        f.session.stop(); f.start()
        assertEquals(2, f.clients.size)
    }

    @Test fun timeout_invalidates_signal_and_late_reply_cannot_restore_it() {
        val f = Fixture(); f.start(); f.client.supportAll()
        f.client.events.getValue(PleosSensor.BELT).first(true)
        f.time = 5_101; f.session.tick()
        assertEquals(SignalStatus.DISCONNECTED, f.snapshot.driverBeltBuckled.status)
        f.client.reads.getValue(PleosSensor.BELT).first(true)
        f.client.events.getValue(PleosSensor.BELT).first(true)
        assertEquals(SignalStatus.DISCONNECTED, f.snapshot.driverBeltBuckled.status)
    }

    @Test fun heartbeat_detects_silent_disconnect_without_repeating_pending_requests() {
        val f = Fixture(); f.start(); f.client.supportAll(); f.client.settleReads()
        f.client.events.getValue(PleosSensor.BRAKE).first(true)
        f.time = 1_100; f.session.tick()
        val pending = f.client.reads.getValue(PleosSensor.BRAKE)
        f.time = 2_100; f.session.tick()
        assertSame(pending, f.client.reads.getValue(PleosSensor.BRAKE))
        f.time = 6_101; f.session.tick()
        assertEquals(SignalStatus.DISCONNECTED, f.snapshot.brakePressed.status)
    }
}

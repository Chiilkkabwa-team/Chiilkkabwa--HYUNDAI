package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.event.*
import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test

class SpeedSignalSessionTest {
    private class Client : SpeedReadClient {
        var access = SpeedReadAccess.GRANTED
        var supported = true
        var accepted = true
        var connectError: Exception? = null
        var configError: Exception? = null
        var inlineValue: Float? = null
        var closes = 0
        var afterInline: () -> Unit = {}
        var inlineTimestamp = 1_000_000_000L
        lateinit var ready: () -> Unit
        lateinit var disconnected: (SignalStatus) -> Unit
        lateinit var value: (Any?, SignalStatus, Long) -> Unit
        lateinit var error: (SignalStatus) -> Unit
        override fun access() = access
        override fun connect(ready: () -> Unit, disconnected: (SignalStatus) -> Unit) {
            connectError?.let { throw it }; this.ready = ready; this.disconnected = disconnected
        }
        override fun isSupported(): Boolean { configError?.let { throw it }; return supported }
        override fun subscribe(value: (Any?, SignalStatus, Long) -> Unit, error: (SignalStatus) -> Unit): Boolean {
            this.value=value; this.error=error; inlineValue?.let { value(it, SignalStatus.OK, inlineTimestamp) }; afterInline(); return accepted
        }
        override fun close() { closes++ }
    }
    private class Fixture(enabled: Boolean = true) {
        var now=1_000_000_000L
        var id=0
        val clients=mutableListOf<Client>()
        val readings=mutableListOf<VehicleSpeedObservation>()
        val results=mutableListOf<SpeedDetectionResult>()
        val session=SpeedSignalSession({ Client().also { clients+=it } }, SignalSource.EMULATOR, enabled,
            { now }, { "test-${++id}" }, { results+=it })
        fun start(): Client { session.start { readings+=it }; return clients.last() }
        fun emit(client: Client, raw: Any?, advanceMs: Long=1_000, status: SignalStatus=SignalStatus.OK) {
            now+=advanceMs*1_000_000; client.value(raw,status,now)
        }
    }
    @Test fun baseline_then_increase_decrease_and_unit_conversion() {
        val f=Fixture(); val c=f.start(); c.ready(); f.emit(c,10f,0)
        assertTrue(f.results.last() is SpeedDetectionResult.Baseline)
        assertEquals(36.0,f.readings.last().kilometersPerHour!!,0.0)
        f.emit(c,13f); assertEquals(SpeedEventType.RAPID_SPEED_INCREASE,(f.results.last() as SpeedDetectionResult.Evaluated).event!!.type)
        f.emit(c,9f); assertEquals(SpeedEventType.RAPID_SPEED_DECREASE,(f.results.last() as SpeedDetectionResult.Evaluated).event!!.type)
        assertEquals(SpeedUnit.METERS_PER_SECOND,(f.results.last() as SpeedDetectionResult.Evaluated).evidence.current.unit)
    }
    @Test fun reverse_is_preserved_and_next_forward_is_baseline() {
        val f=Fixture(); val c=f.start(); c.ready(); f.emit(c,10f,0); f.emit(c,-3f)
        assertEquals(-3f,f.readings.last().rawMps!!,0f); assertEquals(SignalStatus.OK,f.readings.last().status)
        assertTrue(f.results.last() is SpeedDetectionResult.Rejected)
        f.emit(c,10f); assertTrue(f.results.last() is SpeedDetectionResult.Baseline)
    }
    @Test fun invalid_type_null_nonfinite_and_unavailable_break_continuity() {
        for(raw in listOf(null,10.0,10,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
            val f=Fixture(); val c=f.start(); c.ready(); f.emit(c,10f,0); f.emit(c,raw)
            assertEquals(SignalStatus.UNKNOWN,f.readings.last().status)
            f.emit(c,13f); assertTrue(f.results.last() is SpeedDetectionResult.Baseline)
        }
        val f=Fixture(); val c=f.start(); c.ready(); f.emit(c,10f,0); f.emit(c,99f,status=SignalStatus.STALE)
        assertEquals(SignalStatus.STALE,f.readings.last().status); assertNull(f.readings.last().rawMps)
    }
    @Test fun missing_and_denied_permission_do_not_connect() {
        for(access in listOf(SpeedReadAccess.MISSING_PERMISSION,SpeedReadAccess.DENIED)) {
            var now=1_000_000_000L; val c=Client().also { it.access=access }; val readings=mutableListOf<VehicleSpeedObservation>()
            val session=SpeedSignalSession({c},SignalSource.EMULATOR,true,{now},{"id"})
            session.start { readings+=it }
            assertEquals(if(access==SpeedReadAccess.DENIED) SignalStatus.PERMISSION_DENIED else SignalStatus.UNSUPPORTED,readings.last().status)
            assertEquals(1,c.closes)
        }
    }
    @Test fun unsupported_config_and_security_or_connection_failure_are_explicit() {
        val f=Fixture(); val c=f.start(); c.supported=false; c.ready(); assertEquals(SignalStatus.UNSUPPORTED,f.readings.last().status)
        val g=Fixture(); val d=g.start(); d.configError=SecurityException(); d.ready(); assertEquals(SignalStatus.PERMISSION_DENIED,g.readings.last().status)
        val h=Fixture(); val e=h.start(); e.configError=IllegalStateException(); e.ready(); assertEquals(SignalStatus.DISCONNECTED,h.readings.last().status)
    }
    @Test fun registration_failure_never_publishes_inline_sample() {
        val f=Fixture(); val c=f.start(); c.accepted=false; c.inlineValue=10f; c.ready()
        assertEquals(SignalStatus.UNKNOWN,f.readings.last().status); assertTrue(f.results.none { it is SpeedDetectionResult.Baseline })
    }
    @Test fun stop_and_restart_ignore_old_values_and_ready_callbacks() {
        val f=Fixture(); val old=f.start(); old.ready(); f.emit(old,10f,0); val oldId=f.readings.last().sessionId
        f.session.stop(); assertEquals(SignalStatus.DISCONNECTED,f.readings.last().status); assertNull(f.readings.last().rawMps)
        val stopped=f.readings.size; f.emit(old,13f); assertEquals(stopped,f.readings.size)
        val fresh=f.start(); old.ready(); f.emit(old,20f); fresh.ready(); f.emit(fresh,13f)
        assertNotEquals(oldId,f.readings.last().sessionId); assertTrue(f.results.last() is SpeedDetectionResult.Baseline)
    }
    @Test fun disconnect_is_terminal_until_new_session() {
        val f=Fixture(); val c=f.start(); c.ready(); f.emit(c,10f,0); c.disconnected(SignalStatus.DISCONNECTED)
        assertEquals(SignalStatus.DISCONNECTED,f.readings.last().status)
        val size=f.readings.size; c.ready(); f.emit(c,20f); assertEquals(size,f.readings.size)
    }
    @Test fun missing_timeout_expires_once_and_next_value_is_baseline() {
        val f=Fixture(); val c=f.start(); c.ready(); f.emit(c,10f,0)
        f.now+=1_000_000_001; f.session.tick(); assertEquals(SignalStatus.STALE,f.readings.last().status)
        val size=f.readings.size; f.session.tick(); assertEquals(size,f.readings.size)
        f.emit(c,13f); assertTrue(f.results.last() is SpeedDetectionResult.Baseline)
    }
    @Test fun future_old_and_pre_session_timestamps_are_not_normalized_to_receipt() {
        for(delta in listOf(1L,-1_000_000_001L)) {
            val f=Fixture(); val c=f.start(); c.ready(); f.now=3_000_000_000L; c.value(10f,SignalStatus.OK,f.now+delta)
            assertNotEquals(SignalStatus.OK,f.readings.last().status)
            assertEquals(f.now+delta,f.readings.last().timestampNanos)
        }
        val f=Fixture(); val c=f.start(); c.ready(); c.value(10f,SignalStatus.OK,f.now-1)
        assertEquals(SignalStatus.STALE,f.readings.last().status)
    }
    @Test fun duplicate_and_reversed_time_never_emit_and_restore_baseline() {
        val f=Fixture(); val c=f.start(); c.ready(); f.emit(c,10f,0); c.value(20f,SignalStatus.OK,f.now)
        assertTrue(f.results.last() is SpeedDetectionResult.Rejected)
        f.emit(c,11f); assertTrue(f.results.last() is SpeedDetectionResult.Baseline)
        c.value(20f,SignalStatus.OK,f.now-1); assertTrue(f.results.last() is SpeedDetectionResult.Rejected)
    }
    @Test fun constant_values_and_cooldown_episode_are_preserved() {
        val f=Fixture(); val c=f.start(); c.ready(); f.emit(c,10f,0); f.emit(c,10f)
        assertNull((f.results.last() as SpeedDetectionResult.Evaluated).event)
        f.emit(c,13f); f.emit(c,16f); assertEquals(EventSuppression.ONGOING_EPISODE,(f.results.last() as SpeedDetectionResult.Evaluated).suppression)
        f.emit(c,16f); f.emit(c,19f); assertEquals(EventSuppression.COOLDOWN,(f.results.last() as SpeedDetectionResult.Evaluated).suppression)
    }
    @Test fun disabled_session_never_creates_client() {
        val f=Fixture(false); f.session.start { f.readings+=it }; assertTrue(f.clients.isEmpty()); assertTrue(f.results.isEmpty())
        assertEquals(SignalStatus.UNKNOWN,f.readings.last().status)
    }
    @Test fun client_factory_and_connect_failures_are_disconnected() {
        for(failFactory in listOf(true,false)) {
            val readings=mutableListOf<VehicleSpeedObservation>()
            val c=Client().also { it.connectError=IllegalStateException() }
            val session=SpeedSignalSession({ if(failFactory) throw IllegalStateException() else c },SignalSource.EMULATOR,true,{1_000_000_000L},{"s"})
            session.start { readings+=it }; assertEquals(SignalStatus.DISCONNECTED,readings.last().status)
            assertFalse(session.needsTick)
        }
    }
    @Test fun permission_revoked_while_connecting_is_not_supported_success() {
        val f=Fixture(); val c=f.start(); c.access=SpeedReadAccess.DENIED; c.ready()
        assertEquals(SignalStatus.PERMISSION_DENIED,f.readings.last().status); assertEquals(1,c.closes)
    }
    @Test fun callback_error_clears_value_and_late_callbacks_cannot_recover_it() {
        val f=Fixture(); val c=f.start(); c.ready(); f.emit(c,10f,0); c.error(SignalStatus.UNKNOWN)
        assertEquals(SignalStatus.UNKNOWN,f.readings.last().status); assertNull(f.readings.last().rawMps)
        val size=f.readings.size; f.emit(c,13f); assertEquals(size,f.readings.size)
    }
    @Test fun no_initial_callback_expires_and_reversed_receipt_clock_breaks_continuity() {
        val f=Fixture(); val c=f.start(); c.ready(); f.now+=1_000_000_001; f.session.tick()
        assertEquals(SignalStatus.STALE,f.readings.last().status)
        f.emit(c,10f); f.now-=1; f.session.tick(); assertEquals(SignalStatus.UNKNOWN,f.readings.last().status)
        f.emit(c,13f); assertTrue(f.results.last() is SpeedDetectionResult.Baseline)
    }    @Test fun inline_receipt_is_captured_before_registration_returns() {
        val f=Fixture(); val c=f.start(); c.inlineValue=10f; c.afterInline={ f.now+=1_000_000 }; c.ready()
        assertEquals(1_000_000_000L,f.readings.last().receivedAtNanos)
    }
    @Test fun future_inline_timestamp_does_not_become_valid_while_registration_returns() {
        val f=Fixture(); val c=f.start(); c.inlineValue=10f; c.inlineTimestamp=f.now+1
        c.afterInline={ f.now+=1_000_000 }; c.ready()
        assertEquals(SignalStatus.UNKNOWN,f.readings.last().status)
        assertTrue(f.results.last() is SpeedDetectionResult.Rejected)
    }
    @Test fun inline_sample_that_ages_during_registration_is_stale_when_published() {
        val f=Fixture(); val c=f.start(); c.inlineValue=10f; c.afterInline={ f.now+=1_000_000_001 }; c.ready()
        assertEquals(1_000_000_000L,f.readings.last().receivedAtNanos)
        assertEquals(SignalStatus.STALE,f.readings.last().status)
    }}

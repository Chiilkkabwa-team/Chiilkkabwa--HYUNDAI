package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.event.*
import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic lifecycle regression; not an Android Activity or platform-policy execution test. */
class A02DrivingLifecycleTest {
    private class Client : SpeedReadClient {
        var permission = SpeedReadAccess.GRANTED
        lateinit var ready: () -> Unit
        lateinit var value: (Any?, SignalStatus, Long) -> Unit
        lateinit var error: (SignalStatus) -> Unit
        var subscriptions = 0
        var closes = 0
        var duringClose: (() -> Unit)? = null
        override fun access() = permission
        override fun connect(ready: () -> Unit, disconnected: (SignalStatus) -> Unit) { this.ready = ready }
        override fun isSupported() = true
        override fun subscribe(value: (Any?, SignalStatus, Long) -> Unit, error: (SignalStatus) -> Unit): Boolean {
            subscriptions++; this.value = value; this.error = error; return true
        }
        override fun close() { closes++; duringClose?.invoke() }
    }
    private class Fixture {
        var now = 1_000_000_000L
        var ids = 0
        val clients = mutableListOf<Client>()
        val results = mutableListOf<SpeedDetectionResult>()
        val session = SpeedSignalSession({ Client().also { clients += it } }, SignalSource.EMULATOR,
            true, { now }, { "synthetic-${++ids}" }, { results += it })
        val source = object : VehicleSource {
            override fun start(sink: SnapshotSink) = session.start { speed -> sink.update { it.copy(speed = speed) } }
            override fun stop() = session.stop()
        }
        val repo = VehicleRepository(listOf(source))
        fun resume(): Client { repo.start(); return clients.last().also { it.ready() } }
        fun emit(c: Client, speed: Float) { now += 100_000_000; c.value(speed, SignalStatus.OK, now) }
    }
    @Test fun repeated_resume_and_ready_do_not_duplicate_subscriptions() {
        val f = Fixture(); val c = f.resume(); f.repo.start(); c.ready()
        assertEquals(1, f.clients.size); assertEquals(1, c.subscriptions)
        f.repo.stop(); f.repo.stop(); assertEquals(1, c.closes); assertFalse(f.session.needsTick)
    }
    @Test fun stop_invalidates_before_close_and_no_late_callback_can_publish_or_evaluate() {
        val f = Fixture(); val c = f.resume(); f.emit(c, 10f)
        c.duringClose = { f.emit(c, 13f) }
        val count = f.results.size; f.repo.stop(); f.emit(c, 16f); f.session.tick()
        assertEquals(count, f.results.size)
        assertEquals(SignalStatus.DISCONNECTED, f.repo.snapshot.value.speed.status)
        assertNull(f.repo.snapshot.value.speed.rawMps)
        assertFalse(f.session.needsTick)
    }
    @Test fun reentry_uses_new_session_and_baseline_and_cannot_bridge_pre_pause_speed() {
        val f = Fixture(); val old = f.resume(); f.emit(old, 10f); f.emit(old, 11f)
        val first = (f.results.last() as SpeedDetectionResult.Evaluated).event!!
        f.repo.stop(); f.now += 2_000_000_000; val current = f.resume()
        val count = f.results.size; f.emit(old, 30f); old.ready(); assertEquals(count, f.results.size)
        current.value(30f, SignalStatus.OK, 1_000_000_000)
        assertEquals(SignalStatus.STALE, f.repo.snapshot.value.speed.status)
        f.emit(current, 30f); assertTrue(f.results.last() is SpeedDetectionResult.Baseline)
        f.emit(current, 31f); val second = (f.results.last() as SpeedDetectionResult.Evaluated).event!!
        assertNotEquals(first.sessionId, second.sessionId); assertNotEquals(first.eventId, second.eventId)
        assertEquals(SignalSource.EMULATOR, second.source)
        assertEquals(f.now / 1_000_000, second.occurredAtMs)
        assertEquals(30.0, second.evidence.previous.speed!!, 0.0)
        assertEquals(SpeedUnit.METERS_PER_SECOND, second.evidence.current.unit)
    }
    @Test fun permission_error_is_terminal_until_explicit_stop_start_not_a_normal_value() {
        val f = Fixture(); val c = f.resume(); f.emit(c, 10f); c.error(SignalStatus.PERMISSION_DENIED)
        assertEquals(SignalStatus.PERMISSION_DENIED, f.repo.snapshot.value.speed.status)
        assertNull(f.repo.snapshot.value.speed.rawMps); assertFalse(f.session.needsTick)
        val count = f.results.size; f.emit(c, 13f); c.ready(); assertEquals(count, f.results.size)
        f.repo.stop(); val next = f.resume(); f.emit(next, 13f)
        assertTrue(f.results.last() is SpeedDetectionResult.Baseline)
    }
}

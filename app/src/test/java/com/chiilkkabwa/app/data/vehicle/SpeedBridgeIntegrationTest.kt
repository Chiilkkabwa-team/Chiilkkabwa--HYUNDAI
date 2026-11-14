package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.event.*
import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test

class SpeedBridgeIntegrationTest {
    private fun reading(mps: Float, ms: Long, source: SignalSource=SignalSource.EMULATOR, id: String="s") =
        VehicleSpeedObservation(mps,ms*1_000_000,ms*1_000_000,source,SignalStatus.OK,id)
    @Test fun bridge_source_change_breaks_previous_calculation() {
        val b=SpeedEventBridge(); b.start("s"); b.accept(reading(10f,1_000),1_000_000_000)
        assertEquals(BaselineReason.SOURCE_CHANGED,(b.accept(reading(20f,2_000,SignalSource.MOCK),2_000_000_000) as SpeedDetectionResult.Baseline).reason)
    }
    @Test fun bridge_stopped_or_wrong_session_cannot_emit() {
        val b=SpeedEventBridge(); b.start("s"); b.accept(reading(10f,1_000),1_000_000_000)
        assertNull(b.accept(reading(20f,2_000,id="old"),2_000_000_000)); b.stop()
        assertNull(b.accept(reading(20f,3_000),3_000_000_000)); b.start("new")
        assertTrue(b.accept(reading(20f,4_000,id="new"),4_000_000_000) is SpeedDetectionResult.Baseline)
    }
    @Test fun nanosecond_future_or_stale_is_rejected_before_rounding() {
        val b=SpeedEventBridge(); b.start("s")
        val r=reading(10f,1_000)
        assertTrue(b.accept(r.copy(timestampNanos=r.timestampNanos!!+1),r.receivedAtNanos) is SpeedDetectionResult.Rejected)
        assertTrue(b.accept(r,r.receivedAtNanos+1_000_000_001) is SpeedDetectionResult.Rejected)
    }
    @Test fun receipt_clock_reversal_is_rejected() {
        val b=SpeedEventBridge(); b.start("s")
        assertTrue(b.accept(reading(10f,1_000),999_999_999) is SpeedDetectionResult.Rejected)
    }
    @Test fun invalid_observation_never_exposes_a_usable_derived_speed() {
        assertNull(reading(Float.NaN,1_000).copy(status=SignalStatus.UNKNOWN).kilometersPerHour)
        assertNull(reading(10f,1_000).copy(status=SignalStatus.STALE).kilometersPerHour)
        assertEquals(-10.8,reading(-3f,1_000).kilometersPerHour!!,0.000001)
    }
    @Test fun activation_requires_every_gate_and_positive_emulator_hardware() {
        assertTrue(allowAaosSpeed(true,true,SignalSource.EMULATOR,"ranchu"))
        assertTrue(allowAaosSpeed(true,true,SignalSource.EMULATOR,"goldfish"))
        assertFalse(allowAaosSpeed(false,true,SignalSource.EMULATOR,"ranchu"))
        assertFalse(allowAaosSpeed(true,false,SignalSource.EMULATOR,"ranchu"))
        assertFalse(allowAaosSpeed(true,true,SignalSource.VEHICLE,"ranchu"))
        assertFalse(allowAaosSpeed(true,true,SignalSource.EMULATOR,"physical-board"))
        assertFalse(allowAaosSpeed(true,true,SignalSource.MOCK,"ranchu"))
    }
    @Test fun explicit_mock_input_reaches_bridge_and_stop_discards_speed() {
        var now=1_000_000_000L; var id=0; val results=mutableListOf<SpeedDetectionResult>()
        val mock=MockVehicleSource(speedNowNanos={now},speedSessionId={"mock-${++id}"},onSpeedResult={results+=it})
        val repo=VehicleRepository(listOf(mock)); repo.start(); mock.setSpeedMps(10f)
        assertTrue(results.last() is SpeedDetectionResult.Baseline)
        now+=1_000_000_000; mock.setSpeedMps(13f)
        assertEquals(SignalSource.MOCK,(results.last() as SpeedDetectionResult.Evaluated).event!!.source)
        assertEquals(13f,repo.snapshot.value.speed.rawMps!!,0f)
        repo.stop(); assertEquals(SignalStatus.DISCONNECTED,repo.snapshot.value.speed.status); assertNull(repo.snapshot.value.speed.rawMps)
        val size=results.size; mock.setSpeedMps(20f); assertEquals(size,results.size)
        repo.start(); now+=1_000_000_000; mock.setSpeedMps(15f); assertTrue(results.last() is SpeedDetectionResult.Baseline)
    }
    @Test fun reversed_now_inside_same_millisecond_cannot_emit_an_event() {
        val b=SpeedEventBridge(); b.start("s")
        val first=reading(10f,1_001).copy(receivedAtNanos=2_000_900_000L)
        assertTrue(b.accept(first,2_000_900_000L) is SpeedDetectionResult.Baseline)
        val second=reading(13f,2_000).copy(timestampNanos=2_000_800_000L,receivedAtNanos=2_000_800_000L)
        val reversed=b.accept(second,2_000_800_000L)
        assertTrue(reversed is SpeedDetectionResult.Rejected)
        assertEquals(SpeedRejection.INVALID_TIMESTAMP,(reversed as SpeedDetectionResult.Rejected).reason)
        assertTrue(b.accept(reading(13f,2_001),2_001_000_000L) is SpeedDetectionResult.Baseline)
    }}

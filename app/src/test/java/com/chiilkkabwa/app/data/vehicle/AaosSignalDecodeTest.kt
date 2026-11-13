package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A01: 안드로이드/실차 없이 콜백 값 신뢰도를 검증한다. */
class AaosSignalDecodeTest {
    private val source = SignalSource.EMULATOR

    @Test fun available_gear_and_brake_values_are_recorded_with_provenance() {
        val gear = aaosObserved<Int>(1, true, source, 100) { it as? Int }
        val brake = aaosObserved<Boolean>(true, true, source, 101) { it as? Boolean }
        assertEquals(SignalStatus.OK, gear.status)
        assertEquals(1, gear.value)
        assertEquals(source, gear.source)
        assertEquals(100L, gear.receivedAtMs)
        assertEquals(true, brake.value)
    }

    @Test fun unavailable_state_discards_old_or_nonempty_value() {
        val value = aaosObserved<Boolean>(true, false, source, 200) { it as? Boolean }
        assertEquals(SignalStatus.STALE, value.status)
        assertNull(value.value)
    }

    @Test fun wrong_type_or_null_does_not_unlock_gate() {
        val wrong = aaosObserved<Boolean>("P", true, source, 300) { it as? Boolean }
        val missing = aaosObserved<Int>(null, true, source, 301) { it as? Int }
        assertEquals(SignalStatus.UNKNOWN, wrong.status)
        assertEquals(SignalStatus.UNKNOWN, missing.status)
        assertNull(wrong.value)
        assertNull(missing.value)
    }

    @Test fun only_valid_pedal_percentages_are_accepted() {
        assertEquals(0f, pedalPercent(0f))
        assertEquals(51.5f, pedalPercent(51.5))
        assertEquals(100f, pedalPercent(100))
        assertNull(pedalPercent(-1f))
        assertNull(pedalPercent(101f))
        assertNull(pedalPercent(Float.NaN))
        assertNull(pedalPercent(Float.POSITIVE_INFINITY))
        assertNull(pedalPercent("50"))
    }
}

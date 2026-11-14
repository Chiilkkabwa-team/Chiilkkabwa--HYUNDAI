package com.chiilkkabwa.app.core.event

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SpeedLogReplayTest {
    private fun fixture(name: String): File = sequenceOf(File("../docs"), File("docs"))
        .map { File(it, "evidence/a02-2026-11-14/$name.log") }.first { it.exists() }

    @Test fun `parser preserves log text float precision and separates consumed`() {
        val parsed = SpeedLogReplay.parse(fixture("increase").readLines())
        assertEquals(14, parsed.observations.size)
        val last = parsed.observations.last()
        assertEquals("11-13 21:02:36.909", last.logTimestamp)
        assertEquals("1.1111112", last.rawMpsText)
        assertEquals(1.1111111640930176, last.reading.rawMps!!.toDouble(), 0.0)
        assertEquals(699902045600, last.reading.timestampNanos)
        assertTrue(parsed.consumed.any { it.text.contains("event=RAPID_SPEED_INCREASE") })
    }
    @Test fun `missing fields cannot be defaulted and explicit null stays null`() {
        val line = fixture("increase").readLines().first()
        try { SpeedLogReplay.parse(listOf(line.replace("status=OK ", ""))); fail("missing status") }
        catch (_: IllegalArgumentException) { }
        val nullLine = line.replace("rawMps=0.5555556", "rawMps=null").replace("propertyNs=698009649100", "propertyNs=null")
        assertNull(SpeedLogReplay.parse(listOf(nullLine)).observations.single().reading.rawMps)
        assertNull(SpeedLogReplay.parse(listOf(nullLine)).observations.single().reading.timestampNanos)
    }
    @Test fun `cold replay reproduces quantization candidate without claiming truth`() {
        val parsed = SpeedLogReplay.parse(fixture("increase").readLines())
        val rows = SpeedLogReplay.replay(parsed)
        val legacy = rows.mapNotNull { (it.sustained.legacy as? SpeedDetectionResult.Evaluated)?.event }
        assertEquals(1, legacy.size)
        assertEquals(699902L, legacy.single().occurredAtMs)
        assertEquals(101L, legacy.single().evidence.intervalMs)
        assertEquals(2.75027516, legacy.single().evidence.accelerationMps2, 0.000001)
        assertTrue(rows.none { it.sustained.candidate != null })
        assertTrue(rows.none { it.windowOnly.candidate != null })
    }
    @Test fun `produce deterministic reports without modifying fixture files`() {
        val output = System.getenv("A02_REPLAY_REPORT_DIR")?.let(::File)
        for (name in listOf("increase", "decrease")) {
            val file = fixture(name)
            val bytes = file.readBytes()
            val parsed = SpeedLogReplay.parse(file.readLines())
            val rows = SpeedLogReplay.replay(parsed)
            assertEquals(parsed.observations.size, rows.size)
            assertArrayEquals(bytes, file.readBytes())
            if (output != null) SpeedLogReplay.report(name, parsed, rows, output)
        }
    }
}

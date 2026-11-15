package com.chiilkkabwa.app.core.event

import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.round
import java.io.File

class SpeedQualityShadowTest {
    private fun sample(ms: Long, v: Float, source: SignalSource = SignalSource.EMULATOR,
                       status: SignalStatus = SignalStatus.OK, session: String = "s") =
        VehicleSpeedObservation(v, ms * 1_000_000, ms * 1_000_000 + 1, source, status, session)
    private fun run(values: List<VehicleSpeedObservation>, mode: QualityMode = QualityMode.SUSTAINED_QUANTIZATION): List<SpeedQualityResult> {
        val shadow = SpeedQualityShadow(mode); shadow.start("s")
        return values.mapNotNull { shadow.accept(it, it.receivedAtNanos) }
    }
    private fun ramp(a: Double, quantized: Boolean = false): List<VehicleSpeedObservation> =
        (0..20).map { i ->
            val v = 20 + a * i / 10
            sample(i * 100L, (if (quantized) round(v * 3.6) / 3.6 else v).toFloat())
        }
    @Test fun `gentle quantized ramps retain raw candidates but hold shadow`() {
        for (a in listOf(1.0, -1.0)) {
            val results = run(ramp(a, true))
            if (a > 0) assertTrue(results.any { (it.legacy as? SpeedDetectionResult.Evaluated)?.event != null })
            assertTrue(results.none { it.candidate != null })
        }
    }
    @Test fun `strong sustained changes survive with explicit delay and original evidence`() {
        for (a in listOf(5.0, -5.0)) {
            val input = ramp(a)
            val events = run(input).mapNotNull { it.candidate }
            assertEquals(1, events.size)
            assertEquals(600_000_000L, events.single().observations.last().timestampNanos)
            assertEquals(input.take(7), events.single().observations)
            assertTrue(events.single().id.contains("quality-shadow"))
        }
    }
    @Test fun `irregular quantized constant truth noise and isolated spike are held`() {
        var t = 0L
        val noisy = (0..20).map { i -> t += if (i % 2 == 0) 100 else 200
            sample(t, (10 + (i % 2) / 3.6).toFloat()) }
        assertTrue(run(noisy).none { it.candidate != null })
        val spike = (0..20).map { sample(it * 100L, if (it == 8) 11f else 10f) }
        assertTrue(run(spike).none { it.candidate != null })
        assertTrue(run(ramp(0.0)).none { it.candidate != null })
    }
    @Test fun `invalid status and reverse signed speed break continuity`() {
        for (status in listOf(SignalStatus.STALE, SignalStatus.UNKNOWN, SignalStatus.DISCONNECTED)) {
            val values = ramp(5.0).take(6) + sample(600, -1f, status = status) + sample(700, 23.5f)
            val result = run(values)
            assertEquals(QualityReason.REJECTED_INPUT, result[result.lastIndex - 1].reason)
            assertNull(result.last().candidate)
        }
        val negative = run(listOf(sample(0, 0f), sample(100, -1f)))
        assertEquals(-1f, negative.last().observation.rawMps)
        assertEquals(QualityReason.REJECTED_INPUT, negative.last().reason)
    }
    @Test fun `gap duplicate reverse clocks and source change cannot bridge a window`() {
        val base = ramp(5.0).take(6)
        for (extra in listOf(sample(1000, 25f), sample(500, 22.5f), sample(400, 22f),
                sample(600, 23f, SignalSource.MOCK), sample(600, 23f).copy(receivedAtNanos = 1))) {
            assertNull(run(base + extra).last().candidate)
        }
    }
    @Test fun `session mismatch stop and restart ignore late callbacks`() {
        val s = SpeedQualityShadow(); s.start("s")
        assertNull(s.accept(sample(100, 10f, session = "old"), 100_000_001))
        s.stop(); assertNull(s.accept(sample(100, 10f), 100_000_001))
        s.start("new"); assertNull(s.accept(sample(200, 11f), 200_000_001))
    }
    @Test fun `legacy comparison is unchanged and evidence memory bounded`() {
        val s = SpeedQualityShadow(); s.start("s")
        val b = SpeedEventBridge(); b.start("s")
        for (reading in ramp(5.0) + ramp(0.0).map { it.copy(timestampNanos = it.timestampNanos!! + 3_000_000_000, receivedAtNanos = it.receivedAtNanos + 3_000_000_000) }) {
            val r = s.accept(reading, reading.receivedAtNanos)!!
            assertEquals(b.accept(reading, reading.receivedAtNanos), r.legacy)
            assertTrue(r.window.size <= 32)
        }
    }
    @Test fun `window only and quantization modes expose threshold sensitivity`() {
        assertTrue(run(ramp(3.0), QualityMode.WINDOW_ONLY).any { it.candidate != null })
        assertTrue(run(ramp(3.0)).none { it.candidate != null })
    }

    @Test fun `synthetic comparisons expose counts times holds and assumptions`() {
        var time = 0L
        val irregular = (0..18).map { i -> time += if (i % 2 == 0) 100 else 200
            sample(time, (20 + 5 * time / 1000.0).toFloat()) }
        val scenarios = linkedMapOf(
            "constant truth 10mps; alternating 0 or +1kmh observation error" to (0..20).map { sample(it * 100L, (10 + (it % 2) / 3.6).toFloat()) },
            "gentle +1mps2 rounded to nearest 1kmh" to ramp(1.0, true),
            "gentle -1mps2 rounded to nearest 1kmh" to ramp(-1.0, true),
            "sustained +5mps2" to ramp(5.0),
            "sustained -5mps2" to ramp(-5.0),
            "100-200ms irregular +5mps2" to irregular,
            "regular 200ms +5mps2" to (0..12).map { sample(it * 200L, (20 + it).toFloat()) },
            "single 100ms +1mps spike" to (0..20).map { sample(it * 100L, if (it == 8) 11f else 10f) },
            "constant 20mps" to ramp(0.0),
            "one missing callback at 300ms in +5mps2" to ramp(5.0).filterIndexed { i, _ -> i != 3 },
            "near threshold +3mps2 limitation" to ramp(3.0),
        )
        val lines = mutableListOf("# Synthetic comparison", "", "Assumed truth is specified per row; all input is synthetic EMULATOR-tagged data, not new emulator execution.",
            "Each ramp spans 2s at 100ms unless stated. Times below are elapsed property milliseconds.", "",
            "| Assumption | Legacy count@ms | Window count@ms | Sustained quantum count@ms | Quality reasons |", "|---|---|---|---|---|")
        for ((name, values) in scenarios) {
            val sustained = run(values)
            val window = run(values, QualityMode.WINDOW_ONLY)
            val expected = if (name.contains("5mps2")) 1 else 0
            assertEquals(name, expected, sustained.count { it.candidate != null })
            val legacyTimes = sustained.mapNotNull { (it.legacy as? SpeedDetectionResult.Evaluated)?.event?.occurredAtMs }
            fun times(results: List<SpeedQualityResult>) = results.mapNotNull { it.candidate?.observations?.last()?.timestampNanos?.div(1_000_000) }
            fun display(times: List<Long>) = "${times.size}@${times.joinToString(",").ifEmpty { "none" }}"
            lines += "| $name | ${display(legacyTimes)} | ${display(times(window))} | ${display(times(sustained))} | ${sustained.groupingBy { it.reason }.eachCount()} |"
        }
        System.getenv("A02_REPLAY_REPORT_DIR")?.let { File(it).apply { mkdirs() }.resolve("synthetic.md").writeText(lines.joinToString("\n", postfix = "\n")) }
    }

    @Test fun `rejected values permission revocation and gaps require new complete coverage`() {
        val invalids = listOf(sample(600, 23f, status = SignalStatus.PERMISSION_DENIED), sample(600, Float.NaN),
            sample(600, 23f).copy(timestampNanos = null), sample(600, 23f).copy(timestampNanos = 600_000_002))
        for (invalid in invalids) {
            val values = ramp(5.0).take(6) + invalid + (7..12).map { sample(it * 100L, (20 + it * 0.5).toFloat()) }
            assertTrue(run(values).none { it.candidate != null })
        }
        val afterGap = run(ramp(5.0).take(6) + (10..16).map { sample(it * 100L, (20 + it * 0.5).toFloat()) })
        assertEquals(QualityReason.GAP_RESET, afterGap[6].reason)
        assertEquals(1_600_000_000L, afterGap.last().candidate!!.observations.last().timestampNanos)
    }

    @Test fun `same session invalid break does not bypass cooldown`() {
        val values = ramp(5.0).take(8) + sample(800, 24f, status = SignalStatus.UNKNOWN) +
            (9..16).map { sample(it * 100L, (20 + it * 0.5).toFloat()) }
        val results = run(values)
        assertEquals(1, results.count { it.candidate != null })
        assertTrue(results.any { it.reason == QualityReason.COOLDOWN })
    }

    @Test fun `regular 200ms callbacks can retain five samples and detect sustained change`() {
        val input = (0..12).map { sample(it * 200L, (20 + it).toFloat()) }
        val candidates = run(input).mapNotNull { it.candidate }
        assertEquals(1, candidates.size)
        assertEquals(800_000_000L, candidates.single().observations.last().timestampNanos)
        assertEquals(5, candidates.single().observations.size)
    }
}

package com.chiilkkabwa.app.core.event

import com.chiilkkabwa.app.core.signal.*
import java.io.File

/** Test-source utility: never subscribes to AAOS. Missing fields fail instead of being imputed. */
object SpeedLogReplay {
    data class Observation(val line: Int, val logTimestamp: String, val rawMpsText: String,
                           val reading: VehicleSpeedObservation, val original: String)
    data class Consumed(val line: Int, val logTimestamp: String, val text: String, val original: String)
    data class Parsed(val observations: List<Observation>, val consumed: List<Consumed>)
    data class Row(val original: Observation, val windowOnly: SpeedQualityResult, val sustained: SpeedQualityResult)

    fun parse(lines: List<String>): Parsed {
        val observations = mutableListOf<Observation>()
        val consumed = mutableListOf<Consumed>()
        lines.forEachIndexed { index, line ->
            val marker = "A02SpeedTrace: "
            if (!line.contains(marker)) return@forEachIndexed
            require(Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} ").containsMatchIn(line)) { "Missing log timestamp at ${index + 1}" }
            val stamp = line.take(18) // year/timezone are not present; never infer an epoch.
            val body = line.substringAfter(marker)
            if (body.startsWith("consumed ")) consumed.add(Consumed(index + 1, stamp, body, line))
            if (body.startsWith("observation ")) {
                val fields = body.removePrefix("observation ").split(' ').associate { token ->
                    require(token.contains('=')) { "Malformed observation at ${index + 1}" }
                    token.substringBefore('=') to token.substringAfter('=')
                }
                fun field(key: String) = requireNotNull(fields[key]) { "Missing $key at ${index + 1}" }
                val raw = field("rawMps")
                val property = field("propertyNs")
                observations.add(Observation(index + 1, stamp, raw, VehicleSpeedObservation(
                    if (raw == "null") null else raw.toFloat(),
                    if (property == "null") null else property.toLong(), field("receiptNs").toLong(),
                    SignalSource.valueOf(field("source")), SignalStatus.valueOf(field("status")), field("session"),
                ), line))
            }
        }
        return Parsed(observations, consumed)
    }

    /** Each fixture is a COLD excerpt. Receipt ns is the analysis clock; processing ns is unlogged. */
    fun replay(parsed: Parsed): List<Row> {
        require(parsed.observations.map { it.reading.sessionId }.distinct().size <= 1) {
            "Split sessions explicitly; do not invent lifecycle boundaries from log excerpts"
        }
        val window = SpeedQualityShadow(QualityMode.WINDOW_ONLY)
        val sustained = SpeedQualityShadow()
        parsed.observations.firstOrNull()?.reading?.sessionId?.let { window.start(it); sustained.start(it) }
        return parsed.observations.map { o ->
            Row(o, window.accept(o.reading, o.reading.receivedAtNanos)!!,
                sustained.accept(o.reading, o.reading.receivedAtNanos)!!)
        }
    }

    fun report(name: String, parsed: Parsed, rows: List<Row>, output: File) {
        output.mkdirs()
        fun csv(values: List<Any?>) = values.joinToString(",") { "\"${(it?.toString() ?: "").replace("\"", "\"\"")}\"" }
        val header = listOf("line", "logTimestamp", "rawMpsText", "floatAsDouble", "propertyNs", "receiptNs",
            "status", "source", "sessionId", "adjacentDtNs", "adjacentAccelerationMps2", "legacyDtMs", "legacyAccelerationMps2",
            "legacyEvent", "legacySuppression", "legacyRejectionOrBaseline", "windowAccelerationMps2", "windowReason", "windowCandidate",
            "sustainedReason", "sustainedCandidate", "windowPropertyNs", "originalLogLine")
        var previous: VehicleSpeedObservation? = null
        val csvLines = rows.map { row ->
            val o = row.original; val r = o.reading; val old = previous; previous = r
            val dt = r.timestampNanos?.let { current -> old?.timestampNanos?.let { current - it } }
            val adjacent = if (dt != null && dt > 0 && r.rawMps != null && old?.rawMps != null)
                (r.rawMps.toDouble() - old.rawMps.toDouble()) / (dt / 1e9) else null
            val evaluated = row.sustained.legacy as? SpeedDetectionResult.Evaluated
            csv(listOf(o.line, o.logTimestamp, o.rawMpsText, r.rawMps?.toDouble(), r.timestampNanos, r.receivedAtNanos,
                r.status, r.source, r.sessionId, dt, adjacent, evaluated?.evidence?.intervalMs, evaluated?.evidence?.accelerationMps2,
                evaluated?.event?.type, evaluated?.suppression,
                when (val legacy = row.sustained.legacy) {
                    is SpeedDetectionResult.Rejected -> legacy.reason
                    is SpeedDetectionResult.Baseline -> legacy.reason
                    else -> null
                }, row.windowOnly.windowAccelerationMps2, row.windowOnly.reason, row.windowOnly.candidate?.type,
                row.sustained.reason, row.sustained.candidate?.type,
                row.sustained.window.joinToString(";") { it.timestampNanos.toString() }, o.original))
        }
        File(output, "$name.csv").writeText((listOf(csv(header)) + csvLines).joinToString("\n", postfix = "\n"))
        // Keep consumed results separate; do not use them to seed unknown historical cooldown/episodes.
        File(output, "$name-consumed.log").writeText(parsed.consumed.joinToString("\n", postfix = "\n") { it.original })
        val legacy = rows.mapNotNull { (it.sustained.legacy as? SpeedDetectionResult.Evaluated)?.event }
        fun candidates(mode: (Row) -> SpeedQualityResult) = rows.mapNotNull { mode(it).candidate }
            .joinToString { "${it.type}@${it.observations.last().timestampNanos}ns" }.ifEmpty { "none" }
        val summary = """
            # $name offline replay

            EXPERIMENTAL. Cold excerpt; no truth labels. Receipt ns used as analysis processing clock.
            Log timestamp year/timezone and earlier cooldown/episode state are unknown.
            Observations: ${rows.size}; recorded consumed lines: ${parsed.consumed.size} (separate file).

            | Path | Candidate times |
            |---|---|
            | Cold legacy | ${legacy.joinToString { "${it.type}@${it.occurredAtMs}ms" }.ifEmpty { "none" }} |
            | Window only | ${candidates { it.windowOnly }} |
            | Sustained + assumed 1km/h quantum | ${candidates { it.sustained }} |

            Legacy reasons: ${rows.groupingBy { r -> when (val l = r.sustained.legacy) {
                is SpeedDetectionResult.Rejected -> l.reason.toString()
                is SpeedDetectionResult.Baseline -> l.reason.toString()
                is SpeedDetectionResult.Evaluated -> l.suppression?.toString() ?: if (l.event != null) "EVENT" else "BELOW_THRESHOLD"
            } }.eachCount()}
            Quality reasons: ${rows.groupingBy { it.sustained.reason }.eachCount()}
        """.trimIndent()
        File(output, "$name.md").writeText("$summary\n")
    }
}

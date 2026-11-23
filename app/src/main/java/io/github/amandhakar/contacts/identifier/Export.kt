package io.github.amandhakar.contacts.identifier

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CSV text of the corrections and of the processing log, so they can be opened in a spreadsheet and the rules fixed from the data. */
object Export {
    /** One CSV line. Cells with a comma, quote or line break are quoted; a leading = or @ is neutralised so a spreadsheet won't run it. */
    fun csvRow(cells: List<String?>): String = cells.joinToString(",") { raw ->
        var s = raw.orEmpty().replace("\r", " ").replace("\n", " ")
        if (s.startsWith("=") || s.startsWith("@")) s = "\'$s"
        if (s.contains(',') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun time(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    fun corrections(list: List<Correction>): String = buildString {
        appendLine(csvRow(listOf("reported_at", "number", "name", "was_category", "decided_by", "rule", "should_be", "note")))
        list.forEach {
            appendLine(csvRow(listOf(
                time(it.ts), it.number, it.name, it.wasCategory ?: "(none)", it.wasVia.name.lowercase(), it.wasRuleName,
                it.shouldCategory ?: "(none)", it.note,
            )))
        }
    }

    fun log(rows: List<LogRow>): String = buildString {
        appendLine(csvRow(listOf("processed_at", "source", "number", "name", "direction", "call_time", "category", "decided_by", "rule", "ms", "error", "rules_tried")))
        rows.forEach {
            appendLine(csvRow(listOf(
                time(it.ts), it.source.name.lowercase(), it.number, it.name, it.direction.lowercase(), time(it.callTs),
                it.category ?: "(none)", it.via.name.lowercase(), it.ruleName, it.ms.toString(), it.error,
                it.trace.joinToString(" | ") { t -> "${t.rule}: ${t.result}" },
            )))
        }
    }
}

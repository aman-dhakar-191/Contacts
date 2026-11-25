package io.github.amandhakar.contacts.util

/**
 * A caller-ID string split into the part that is a name and the part that is a carrier warning.
 * Some carriers put the warning inside the name itself, e.g. "Vi Alert: Spam REENA PATEL".
 */
data class CallerLabel(
    /** The person or business, without the warning. Null when the string was only a warning. */
    val name: String?,
    /** Who raised the warning, e.g. "Vi Alert". */
    val source: String? = null,
    /** What they flagged it as, e.g. "Spam". Null when the carrier gave a warning with no category word. */
    val category: String? = null,
) {
    val hasAlert: Boolean get() = source != null

    /** For a chip: "Vi Alert · Spam". */
    val alert: String? get() = if (source == null) null else listOfNotNull(source, category).joinToString(" · ")

    /** For one line of text: "Spam" if known, else "Vi Alert". */
    val alertShort: String? get() = category ?: source
}

object CallerLabels {
    // "<something up to 24 chars> Alert: <rest>". Only this shape is recognised, because it is the only
    // carrier format seen so far (Vi). Anything else is left alone and shown as an ordinary name.
    private val alertShape = Regex("""^\s*(\S.{0,23}?)\s+Alert\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val categories = listOf("spam", "scam", "fraud", "telemarketer", "telemarketing", "robocall", "suspected")

    fun parse(raw: String): CallerLabel {
        val m = alertShape.matchEntire(raw.trim()) ?: return CallerLabel(raw.trim().ifEmpty { null })
        val source = m.groupValues[1].trim() + " Alert"
        val rest = m.groupValues[2].trim()
        val first = rest.substringBefore(' ')
        val category = categories.firstOrNull { it.equals(first, ignoreCase = true) }
        val name = (if (category != null) rest.substring(first.length) else rest).trim()
        return CallerLabel(
            name = name.ifEmpty { null },
            source = source,
            category = category?.replaceFirstChar { it.uppercase() },
        )
    }
}

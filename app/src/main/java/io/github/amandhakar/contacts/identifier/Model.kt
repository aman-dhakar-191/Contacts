package io.github.amandhakar.contacts.identifier

/** What a rule condition looks at. */
enum class CondType(val label: String, val hint: String, val needsParam: Boolean) {
    PREFIX("Number starts with", "e.g. 140 or +9170", true),
    REGEX("Number matches pattern", "regular expression, e.g. ^1[48]00", true),
    MAX_DIGITS("Number has at most N digits", "e.g. 6", true),
    HIDDEN("Number is hidden", "", false),
    UNKNOWN("Not in contacts", "", false),
    KNOWN("In contacts", "", false),
    LABEL("Contact has label", "label name, e.g. Family", true),
    NAME_CONTAINS("Name contains", "text, e.g. Bank", true),
    DIRECTION("Call type is", "incoming, outgoing, missed, declined or blocked", true),
    SHORT_CALL("Answered call lasted at most N seconds", "e.g. 5", true),
    REPEAT("Called N or more times within H hours", "N/H, e.g. 3/24", true),
    TIME_RANGE("Call time is between", "HH:MM-HH:MM, e.g. 22:00-07:00", true),

    // What you did with this number's calls before. Uses your call history; "N/D" limits it to the last D days.
    ANSWERED("I answered N or more of its calls", "N or N/days, e.g. 3 or 3/30", true),
    DECLINED("I declined or blocked N or more of its calls", "N or N/days, e.g. 2/30", true),
    MISSED("I missed N or more of its calls", "N or N/days, e.g. 5/7", true),
    CALLED_BY_ME("I called this number N or more times", "N or N/days, e.g. 1", true),
    ACCEPT_RATE_BELOW("I answered less than P% of its calls", "P/M or P/M/days: percent, at least M calls, e.g. 20/5", true),
    ACCEPT_RATE_ABOVE("I answered at least P% of its calls", "P/M or P/M/days, e.g. 80/5", true),
}

data class Condition(val type: CondType, val param: String = "") {
    fun describe(): String = when (type) {
        CondType.PREFIX -> "number starts with $param"
        CondType.REGEX -> "number matches $param"
        CondType.MAX_DIGITS -> "at most $param digits"
        CondType.HIDDEN -> "hidden number"
        CondType.UNKNOWN -> "not in contacts"
        CondType.KNOWN -> "in contacts"
        CondType.LABEL -> "label $param"
        CondType.NAME_CONTAINS -> "name contains $param"
        CondType.DIRECTION -> "call is $param"
        CondType.SHORT_CALL -> "answered, at most $param s"
        CondType.REPEAT -> "$param calls (count/hours)"
        CondType.TIME_RANGE -> "between $param"
        CondType.ANSWERED -> "I answered ≥ $param"
        CondType.DECLINED -> "I declined/blocked ≥ $param"
        CondType.MISSED -> "I missed ≥ $param"
        CondType.CALLED_BY_ME -> "I called it ≥ $param"
        CondType.ACCEPT_RATE_BELOW -> "answered < $param (% / min calls)"
        CondType.ACCEPT_RATE_ABOVE -> "answered ≥ $param (% / min calls)"
    }
}

data class Category(val id: Long, val name: String, val color: Int)

data class Rule(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val enabled: Boolean,
    val sort: Int,
    val conditions: List<Condition>,
    /** Set when the rule is broken (e.g. a bad pattern). A broken rule is switched off. */
    val error: String? = null,
) {
    fun describe(): String = conditions.joinToString(" and ") { it.describe() }.ifBlank { "no conditions" }
}

enum class Direction { IN, OUT, MISSED, DECLINED, BLOCKED, OTHER }
enum class Source { LIVE, HISTORY }

/** One call to classify: a ringing/placed call, or a row from the call log. */
data class CallRecord(
    val source: Source,
    val number: String,
    val nameHint: String?,
    val direction: Direction,
    val timeMs: Long,
    val durationSec: Long?,
    val callLogId: Long?,
)

enum class Via { MANUAL, RULE, NONE }

data class TraceEntry(val rule: String, val result: String)

data class Outcome(
    val via: Via,
    val category: Category?,
    val rule: Rule?,
    val trace: List<TraceEntry>,
    /** Rules that turned out to be broken while evaluating: id to message. */
    val ruleErrors: List<Pair<Long, String>> = emptyList(),
)

/** What you did with a number's calls in some period, from the call log. */
data class ActionStats(
    val answered: Int = 0,
    val outgoing: Int = 0,
    val missed: Int = 0,
    val declined: Int = 0,
    val blocked: Int = 0,
) {
    /** Calls that rang you: answered + missed + declined + blocked. */
    val offered get() = answered + missed + declined + blocked
    val total get() = offered + outgoing
}

/** A call the identifier got wrong, as reported by the user. Kept so rules can be fixed from the data. */
data class Correction(
    val id: Long,
    val ts: Long,
    val number: String,
    val name: String?,
    val wasCategory: String?,
    val wasVia: Via,
    val wasRuleId: Long?,
    val wasRuleName: String?,
    /** What it should have been; null = no category. */
    val shouldCategory: String?,
    val note: String,
)

/** What the engine needs to know about the outside world. */
interface Env {
    fun isKnown(number: String): Boolean
    fun labelsOf(number: String): Set<String>
    fun contactName(number: String): String?
    /** What you did with this number's calls at or before [untilMs] and within [windowMs] of it, from the call log. */
    fun stats(number: String, untilMs: Long, windowMs: Long): ActionStats

    fun callsFrom(number: String, untilMs: Long, windowMs: Long): Int = stats(number, untilMs, windowMs).total
}

/** Conditions are stored as lines "TYPE<TAB>param". */
object ConditionCodec {
    fun encode(list: List<Condition>): String =
        list.joinToString("\n") { "${it.type.name}\t${it.param.replace('\t', ' ').replace('\n', ' ')}" }

    fun decode(text: String): List<Condition> = text.lineSequence().mapNotNull { line ->
        val p = line.split('\t', limit = 2)
        val t = CondType.entries.firstOrNull { it.name == p[0] } ?: return@mapNotNull null
        Condition(t, p.getOrElse(1) { "" })
    }.toList()
}

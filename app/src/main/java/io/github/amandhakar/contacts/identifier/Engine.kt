package io.github.amandhakar.contacts.identifier

import io.github.amandhakar.contacts.util.PhoneUtil
import java.util.Calendar

/**
 * Decides a category for a call. Order: a manual flag on the number, then the enabled rules from top to bottom (the
 * first rule whose conditions all match wins). Pure code: no Android, so it is unit tested.
 */
object Engine {
    sealed interface R {
        data object Match : R
        data class No(val why: String) : R
        data class Err(val msg: String) : R
    }

    fun classify(rec: CallRecord, rules: List<Rule>, categories: Map<Long, Category>, flags: Map<String, Long>, env: Env): Outcome {
        val key = PhoneUtil.key(rec.number)
        if (key.isNotEmpty()) {
            val flagged = flags[key]?.let { categories[it] }
            if (flagged != null) {
                return Outcome(Via.MANUAL, flagged, null, listOf(TraceEntry("Manual flag", "this number is flagged as ${flagged.name}")))
            }
        }
        val trace = ArrayList<TraceEntry>()
        val errors = ArrayList<Pair<Long, String>>()
        for (rule in rules) {
            if (!rule.enabled) { trace += TraceEntry(rule.name, "off"); continue }
            val cat = categories[rule.categoryId]
            if (cat == null) { trace += TraceEntry(rule.name, "skipped: its category was removed"); continue }
            val bad = validate(rule)
            if (bad != null) { errors += rule.id to bad; trace += TraceEntry(rule.name, "error: $bad"); continue }
            var failed: String? = null
            var broke: String? = null
            for (c in rule.conditions) {
                when (val r = eval(c, rec, env)) {
                    R.Match -> {}
                    is R.No -> { failed = "${c.type.label}: ${r.why}"; break }
                    is R.Err -> { broke = r.msg; break }
                }
            }
            when {
                broke != null -> { errors += rule.id to broke; trace += TraceEntry(rule.name, "error: $broke") }
                failed != null -> trace += TraceEntry(rule.name, "no match ($failed)")
                else -> {
                    trace += TraceEntry(rule.name, "matched")
                    return Outcome(Via.RULE, cat, rule, trace, errors)
                }
            }
        }
        return Outcome(Via.NONE, null, null, trace, errors)
    }

    /** Null when the rule is usable, else what is wrong with it. */
    fun validate(rule: Rule): String? {
        if (rule.conditions.isEmpty()) return "the rule has no conditions"
        for (c in rule.conditions) paramError(c)?.let { return "${c.type.label}: $it" }
        return null
    }

    fun paramError(c: Condition): String? = when (c.type) {
        CondType.PREFIX -> if (PhoneUtil.digits(c.param).isEmpty()) "enter the digits it starts with" else null
        CondType.REGEX -> try { Regex(c.param); null } catch (e: Exception) { "not a valid pattern" }
        CondType.MAX_DIGITS, CondType.SHORT_CALL -> if (c.param.trim().toIntOrNull()?.let { it >= 0 } == true) null else "enter a whole number"
        CondType.LABEL, CondType.NAME_CONTAINS -> if (c.param.isBlank()) "enter some text" else null
        CondType.DIRECTION -> if (directionOf(c.param) == null) "use incoming, outgoing, missed, declined or blocked" else null
        CondType.REPEAT -> if (parseRepeat(c.param) == null) "use count/hours, e.g. 3/24" else null
        CondType.TIME_RANGE -> if (parseTimeRange(c.param) == null) "use HH:MM-HH:MM, e.g. 22:00-07:00" else null
        CondType.HIDDEN, CondType.UNKNOWN, CondType.KNOWN -> null
    }

    private fun normalized(number: String) = number.filter { it.isDigit() || it == '+' }

    fun eval(c: Condition, rec: CallRecord, env: Env): R {
        paramError(c)?.let { return R.Err(it) }
        val n = normalized(rec.number)
        val digits = PhoneUtil.digits(n)
        return when (c.type) {
            CondType.PREFIX -> {
                val p = normalized(c.param)
                val pd = PhoneUtil.digits(p)
                if (n.startsWith(p) || digits.startsWith(pd) || PhoneUtil.key(n).startsWith(pd)) R.Match else R.No("number is ${rec.number.ifBlank { "hidden" }}")
            }
            CondType.REGEX -> if (n.isNotEmpty() && Regex(c.param).containsMatchIn(n)) R.Match else R.No("no match in ${n.ifEmpty { "hidden number" }}")
            CondType.MAX_DIGITS -> when {
                n.isEmpty() -> R.No("number is hidden")
                digits.length <= c.param.trim().toInt() -> R.Match
                else -> R.No("${digits.length} digits")
            }
            CondType.HIDDEN -> if (n.isEmpty()) R.Match else R.No("number is shown")
            CondType.UNKNOWN -> if (!env.isKnown(rec.number)) R.Match else R.No("number is in contacts")
            CondType.KNOWN -> if (env.isKnown(rec.number)) R.Match else R.No("number is not in contacts")
            CondType.LABEL -> if (env.labelsOf(rec.number).any { it.equals(c.param.trim(), ignoreCase = true) }) R.Match else R.No("contact has no such label")
            CondType.NAME_CONTAINS -> {
                val name = env.contactName(rec.number) ?: rec.nameHint.orEmpty()
                if (name.contains(c.param.trim(), ignoreCase = true)) R.Match else R.No("name is \"${name.ifEmpty { "unknown" }}\"")
            }
            CondType.DIRECTION -> if (directionOf(c.param) == rec.direction) R.Match else R.No("call is ${rec.direction.name.lowercase()}")
            CondType.SHORT_CALL -> {
                val d = rec.durationSec
                when {
                    d == null -> R.No("needs a finished call")
                    d <= 0 -> R.No("call was not answered")
                    d <= c.param.trim().toInt() -> R.Match
                    else -> R.No("lasted $d s")
                }
            }
            CondType.REPEAT -> {
                val (need, hours) = parseRepeat(c.param)!!
                if (n.isEmpty()) R.No("number is hidden")
                else {
                    // a ringing call is not in the call log yet, so count it too
                    val count = env.callsFrom(rec.number, rec.timeMs, hours * 3_600_000L) + if (rec.source == Source.LIVE) 1 else 0
                    if (count >= need) R.Match else R.No("$count call(s) in $hours h")
                }
            }
            CondType.TIME_RANGE -> {
                val (from, to) = parseTimeRange(c.param)!!
                val m = minuteOfDay(rec.timeMs)
                val inside = if (from <= to) m in from until to else m >= from || m < to
                if (inside) R.Match else R.No("call time ${"%02d:%02d".format(m / 60, m % 60)}")
            }
        }
    }

    fun directionOf(s: String): Direction? = when (s.trim().lowercase()) {
        "incoming", "in" -> Direction.IN
        "outgoing", "out" -> Direction.OUT
        "missed" -> Direction.MISSED
        "declined", "rejected" -> Direction.DECLINED
        "blocked" -> Direction.BLOCKED
        else -> null
    }

    /** "3/24" -> 3 calls within 24 hours. */
    fun parseRepeat(s: String): Pair<Int, Int>? {
        val p = s.split('/')
        if (p.size != 2) return null
        val n = p[0].trim().toIntOrNull() ?: return null
        val h = p[1].trim().toIntOrNull() ?: return null
        return if (n >= 1 && h >= 1) n to h else null
    }

    /** "22:00-07:00" -> minutes of the day (1320, 420). */
    fun parseTimeRange(s: String): Pair<Int, Int>? {
        val p = s.split('-')
        if (p.size != 2) return null
        fun mins(t: String): Int? {
            val q = t.trim().split(':')
            if (q.size != 2) return null
            val h = q[0].toIntOrNull() ?: return null
            val m = q[1].toIntOrNull() ?: return null
            return if (h in 0..23 && m in 0..59) h * 60 + m else null
        }
        val a = mins(p[0]) ?: return null
        val b = mins(p[1]) ?: return null
        return a to b
    }

    fun minuteOfDay(timeMs: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = timeMs }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
}

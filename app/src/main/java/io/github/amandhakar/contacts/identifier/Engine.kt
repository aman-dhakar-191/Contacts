package io.github.amandhakar.contacts.identifier

import io.github.amandhakar.contacts.util.PhoneUtil
import java.util.Calendar

/**
 * Decides a category for a call. Order: a manual flag on the number, then the enabled rules from top to bottom (the
 * first rule whose conditions all match wins). Pure code: no Android, so it is unit tested.
 */
object Engine {
    /** Category id of a flag meaning "never categorise this number". */
    const val EXEMPT = 0L

    private const val ALL_TIME = Long.MAX_VALUE / 4

    /** A pattern (maybe from an imported file) only ever sees this many characters of a number, which bounds the worst case. */
    private const val MAX_MATCH_CHARS = 32

    sealed interface R {
        data object Match : R
        data class No(val why: String) : R
        data class Err(val msg: String) : R
    }

    /** What is looked at for one call. Worked out once, then shared by every condition of every rule. */
    class View(val rec: CallRecord) {
        val n: String = normalized(rec.number)
        val digits: String = PhoneUtil.digits(n)
        val key: String = PhoneUtil.key(n)
    }

    /** One condition with its settings already parsed (regex compiled, numbers read) and a rough cost. */
    class Cond(val c: Condition, val cost: Int, val test: (View, Env) -> R)

    /** A rule ready to run. [error] is set when it can never work (bad pattern, no conditions, ...). */
    class Prepared(val rule: Rule, val error: String?, val conds: List<Cond>)

    /**
     * Parses and validates the rules once, instead of once per call per rule. Within a rule every condition must match,
     * so they can run in any order: cheap checks on the number go first and the ones that look things up come last, so most
     * rules are rejected without touching the contacts or the call log.
     */
    fun prepare(rules: List<Rule>): List<Prepared> = rules.map { rule ->
        if (!rule.enabled) Prepared(rule, null, emptyList())
        else {
            val error = validate(rule)
            Prepared(rule, error, if (error != null) emptyList() else rule.conditions.map { prepare(it) }.sortedBy { it.cost })
        }
    }

    /** One-off convenience: prepares and classifies. Use [prepare] + [classifyPrepared] when classifying many calls. */
    fun classify(rec: CallRecord, rules: List<Rule>, categories: Map<Long, Category>, flags: Map<String, Long>, env: Env): Outcome =
        classifyPrepared(rec, prepare(rules), categories, flags, env)

    fun classifyPrepared(rec: CallRecord, rules: List<Prepared>, categories: Map<Long, Category>, flags: Map<String, Long>, env: Env): Outcome {
        val view = View(rec)
        val key = view.key
        if (key.isNotEmpty()) {
            if (flags[key] == EXEMPT) {
                return Outcome(Via.MANUAL, null, null, listOf(TraceEntry("Manual flag", "this number is flagged as never categorised")))
            }
            val flagged = flags[key]?.let { categories[it] }
            if (flagged != null) {
                return Outcome(Via.MANUAL, flagged, null, listOf(TraceEntry("Manual flag", "this number is flagged as ${flagged.name}")))
            }
        }
        val trace = ArrayList<TraceEntry>()
        val errors = ArrayList<Pair<Long, String>>()
        for (p in rules) {
            val rule = p.rule
            if (!rule.enabled) { trace += TraceEntry(rule.name, "off"); continue }
            val cat = categories[rule.categoryId]
            if (cat == null) { trace += TraceEntry(rule.name, "skipped: its category was removed"); continue }
            if (p.error != null) { errors += rule.id to p.error; trace += TraceEntry(rule.name, "error: ${p.error}"); continue }
            var failed: String? = null
            var broke: String? = null
            for (cond in p.conds) {
                when (val r = cond.test(view, env)) {
                    R.Match -> {}
                    is R.No -> { failed = "${cond.c.type.label}: ${r.why}"; break }
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
        CondType.ANSWERED, CondType.DECLINED, CondType.MISSED, CondType.CALLED_BY_ME ->
            if (parseCountWindow(c.param) == null) "use a count, or count/days, e.g. 3/30" else null
        CondType.ACCEPT_RATE_BELOW, CondType.ACCEPT_RATE_ABOVE ->
            if (parseRate(c.param) == null) "use percent/minimum calls, e.g. 20/5 (optionally /days)" else null
        CondType.HIDDEN, CondType.UNKNOWN, CondType.KNOWN -> null
    }

    internal fun normalized(number: String) = number.filter { it.isDigit() || it == '+' }

    /** Evaluates one condition against one call (prepares it first; for one-off use and tests). */
    fun eval(c: Condition, rec: CallRecord, env: Env): R = prepare(c).test(View(rec), env)

    private const val COST_NUMBER = 0
    private const val COST_REGEX = 1
    private const val COST_CONTACT = 2
    private const val COST_LABEL = 3
    private const val COST_HISTORY = 4

    /** Reads the settings of a condition once and returns the check for it. */
    fun prepare(c: Condition): Cond {
        paramError(c)?.let { msg -> return Cond(c, COST_HISTORY) { _, _ -> R.Err(msg) } }
        return when (c.type) {
            CondType.PREFIX -> {
                val p = normalized(c.param)
                val pd = PhoneUtil.digits(p)
                Cond(c, COST_NUMBER) { v, _ ->
                    if (v.n.startsWith(p) || v.digits.startsWith(pd) || v.key.startsWith(pd)) R.Match else R.No("number is ${v.rec.number.ifBlank { "hidden" }}")
                }
            }
            CondType.REGEX -> {
                val re = Regex(c.param)
                Cond(c, COST_REGEX) { v, _ -> if (v.n.isNotEmpty() && re.containsMatchIn(v.n.take(MAX_MATCH_CHARS))) R.Match else R.No("no match in ${v.n.ifEmpty { "hidden number" }}") }
            }
            CondType.MAX_DIGITS -> {
                val max = c.param.trim().toInt()
                Cond(c, COST_NUMBER) { v, _ ->
                    when {
                        v.n.isEmpty() -> R.No("number is hidden")
                        v.digits.length <= max -> R.Match
                        else -> R.No("${v.digits.length} digits")
                    }
                }
            }
            CondType.HIDDEN -> Cond(c, COST_NUMBER) { v, _ -> if (v.n.isEmpty()) R.Match else R.No("number is shown") }
            CondType.UNKNOWN -> Cond(c, COST_CONTACT) { v, env -> if (!env.isKnown(v.rec.number)) R.Match else R.No("number is in contacts") }
            CondType.KNOWN -> Cond(c, COST_CONTACT) { v, env -> if (env.isKnown(v.rec.number)) R.Match else R.No("number is not in contacts") }
            CondType.LABEL -> {
                val label = c.param.trim()
                Cond(c, COST_LABEL) { v, env -> if (env.labelsOf(v.rec.number).any { it.equals(label, ignoreCase = true) }) R.Match else R.No("contact has no such label") }
            }
            CondType.NAME_CONTAINS -> {
                val part = c.param.trim()
                Cond(c, COST_CONTACT) { v, env ->
                    val name = env.contactName(v.rec.number) ?: v.rec.nameHint.orEmpty()
                    if (name.contains(part, ignoreCase = true)) R.Match else R.No("name is \"${name.ifEmpty { "unknown" }}\"")
                }
            }
            CondType.DIRECTION -> {
                val want = directionOf(c.param)
                Cond(c, COST_NUMBER) { v, _ -> if (want == v.rec.direction) R.Match else R.No("call is ${v.rec.direction.name.lowercase()}") }
            }
            CondType.SHORT_CALL -> {
                val max = c.param.trim().toInt()
                Cond(c, COST_NUMBER) { v, _ ->
                    val d = v.rec.durationSec
                    when {
                        d == null -> R.No("needs a finished call")
                        d <= 0 -> R.No("call was not answered")
                        d <= max -> R.Match
                        else -> R.No("lasted $d s")
                    }
                }
            }
            CondType.REPEAT -> {
                val (need, hours) = parseRepeat(c.param)!!
                val window = hours * 3_600_000L
                Cond(c, COST_HISTORY) { v, env ->
                    if (v.n.isEmpty()) R.No("number is hidden")
                    else {
                        // a ringing call is not in the call log yet, so count it too
                        val count = env.callsFrom(v.rec.number, v.rec.timeMs, window) + if (v.rec.source == Source.LIVE) 1 else 0
                        if (count >= need) R.Match else R.No("$count call(s) in $hours h")
                    }
                }
            }
            CondType.ANSWERED, CondType.DECLINED, CondType.MISSED, CondType.CALLED_BY_ME -> {
                val (need, days) = parseCountWindow(c.param)!!
                val window = windowMs(days)
                val type = c.type
                Cond(c, COST_HISTORY) { v, env ->
                    if (v.n.isEmpty()) R.No("number is hidden")
                    else {
                        val st = env.stats(v.rec.number, v.rec.timeMs, window)
                        val count = when (type) {
                            CondType.ANSWERED -> st.answered
                            CondType.DECLINED -> st.declined + st.blocked
                            CondType.MISSED -> st.missed
                            else -> st.outgoing
                        }
                        if (count >= need) R.Match else R.No("$count ${if (days == 0) "ever" else "in $days days"}")
                    }
                }
            }
            CondType.ACCEPT_RATE_BELOW, CondType.ACCEPT_RATE_ABOVE -> {
                val (percent, minCalls, days) = parseRate(c.param)!!
                val window = windowMs(days)
                val below = c.type == CondType.ACCEPT_RATE_BELOW
                Cond(c, COST_HISTORY) { v, env ->
                    if (v.n.isEmpty()) R.No("number is hidden")
                    else {
                        val st = env.stats(v.rec.number, v.rec.timeMs, window)
                        if (st.offered < minCalls) R.No("only ${st.offered} call(s) rang you, need $minCalls")
                        else {
                            val rate = st.answered * 100.0 / st.offered
                            if (if (below) rate < percent else rate >= percent) R.Match else R.No("you answered ${"%.0f".format(rate)}% of ${st.offered}")
                        }
                    }
                }
            }
            CondType.TIME_RANGE -> {
                val (from, to) = parseTimeRange(c.param)!!
                Cond(c, COST_NUMBER) { v, _ ->
                    val m = minuteOfDay(v.rec.timeMs)
                    val inside = if (from <= to) m in from until to else m >= from || m < to
                    if (inside) R.Match else R.No("call time ${"%02d:%02d".format(m / 60, m % 60)}")
                }
            }
        }
    }

    private fun windowMs(days: Int): Long = if (days <= 0) ALL_TIME else days * 86_400_000L

    /** "3" -> 3 calls ever; "3/30" -> 3 calls within 30 days. */
    fun parseCountWindow(s: String): Pair<Int, Int>? {
        val p = s.split('/')
        if (p.size !in 1..2) return null
        val n = p[0].trim().toIntOrNull() ?: return null
        val d = if (p.size == 2) (p[1].trim().toIntOrNull() ?: return null) else 0
        return if (n >= 1 && d >= 0) n to d else null
    }

    /** "20/5" -> 20 percent, at least 5 calls; "20/5/30" also limits to the last 30 days. Returns (percent, minCalls, days). */
    fun parseRate(s: String): Triple<Int, Int, Int>? {
        val p = s.split('/')
        if (p.size !in 2..3) return null
        val percent = p[0].trim().toIntOrNull() ?: return null
        val min = p[1].trim().toIntOrNull() ?: return null
        val d = if (p.size == 3) (p[2].trim().toIntOrNull() ?: return null) else 0
        return if (percent in 0..100 && min >= 1 && d >= 0) Triple(percent, min, d) else null
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

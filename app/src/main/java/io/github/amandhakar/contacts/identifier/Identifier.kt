package io.github.amandhakar.contacts.identifier

import android.content.Context
import android.provider.CallLog
import io.github.amandhakar.contacts.data.CallEntry
import io.github.amandhakar.contacts.data.ContactsRepo
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.util.PhoneUtil

/** What the UI shows for a categorised call. */
data class Tag(val name: String, val color: Int, val via: Via, val ruleName: String?, val ruleId: Long? = null)

/** Runs the engine for live calls and for the call log, and writes the processing log. */
object Identifier {
    /** How many call-log rows the frequency and acceptance conditions can look back over. */
    const val STATS_ROWS = 2000

    fun isOn(ctx: Context) = Prefs(ctx).identifierOn

    fun directionOf(logType: Int): Direction = when (logType) {
        CallLog.Calls.INCOMING_TYPE -> Direction.IN
        CallLog.Calls.OUTGOING_TYPE -> Direction.OUT
        CallLog.Calls.MISSED_TYPE -> Direction.MISSED
        CallLog.Calls.REJECTED_TYPE -> Direction.DECLINED
        CallLog.Calls.BLOCKED_TYPE -> Direction.BLOCKED
        else -> Direction.OTHER
    }

    /**
     * Looks things up in the phone's contacts and the call log. For a whole run over the call history, [useDirectory] reads every
     * contact number in one query; for a single ringing call one lookup is cheaper.
     */
    class AppEnv(ctx: Context, private val useDirectory: Boolean = false, loadHistory: () -> List<CallEntry>) : Env {
        // Only read the call log (and index it) if a rule actually asks what you did with the number before.
        private val index by lazy { HistoryStats(loadHistory().map { HistoryStats.Entry(PhoneUtil.key(it.number), it.date, it.type) }) }
        private val repo = ContactsRepo(ctx)
        private val directory by lazy { repo.numberDirectory() }
        private val who = HashMap<String, Pair<Long, String>?>()
        private val groupTitles by lazy { repo.groups().associate { it.id to it.title } }
        private val labels = HashMap<Long, Set<String>>()

        private fun lookup(number: String): Pair<Long, String>? {
            val key = PhoneUtil.key(number)
            if (key.isEmpty()) return null
            if (useDirectory) return directory[key]
            return who.getOrPut(number) { repo.lookup(number)?.let { it.contactId to it.name } }
        }

        override fun isKnown(number: String) = lookup(number) != null
        override fun contactName(number: String) = lookup(number)?.second
        override fun labelsOf(number: String): Set<String> {
            val id = lookup(number)?.first ?: return emptySet()
            return labels.getOrPut(id) { repo.detail(id)?.groupIds?.mapNotNull { groupTitles[it] }?.toSet() ?: emptySet() }
        }

        override fun stats(number: String, untilMs: Long, windowMs: Long): ActionStats {
            val key = PhoneUtil.key(number)
            return if (key.isEmpty()) ActionStats() else index.stats(key, untilMs, windowMs)
        }
    }

    private fun tagOf(o: Outcome): Tag? = o.category?.let { Tag(it.name, it.color, o.via, o.rule?.name, o.rule?.id) }

    /** Classifies a ringing or placed call and logs it. Returns null when the master switch is off. */
    fun classifyLive(ctx: Context, number: String, nameHint: String?, incoming: Boolean): Tag? {
        val on = isOn(ctx)
        val store = Store.get(ctx)
        val flags = store.flags()
        // The master switch is for the rules. A number you flagged yourself is always shown.
        if (!on && PhoneUtil.key(number) !in flags) return null
        val t0 = System.nanoTime()
        val now = System.currentTimeMillis()
        val rec = CallRecord(Source.LIVE, number, nameHint, if (incoming) Direction.IN else Direction.OUT, now, null, null)
        var outcome: Outcome? = null
        var failure: String? = null
        try {
            val env = AppEnv(ctx) { io.github.amandhakar.contacts.data.CallLogRepo(ctx).load(STATS_ROWS) }
            outcome = Engine.classify(rec, if (on) store.rules() else emptyList(), store.categories().associateBy { it.id }, flags, env)
        } catch (e: Exception) {
            failure = "${e.javaClass.simpleName}: ${e.message}"
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val o = outcome ?: Outcome(Via.NONE, null, null, emptyList())
        try {
            store.batch { w -> w.log(Source.LIVE, number, nameHint, rec.direction, now, o, ms, failure) }
            outcome?.ruleErrors?.forEach { (id, msg) -> store.breakRule(id, msg) }
            store.trimLog(Store.LOG_KEEP)
        } catch (_: Exception) {
        }
        return outcome?.let { tagOf(it) }
    }

    /**
     * Classifies the call-log rows that have no up-to-date result yet, logs each one, and returns the tag for every
     * categorised row. A row is only processed again after the rules, categories or flags changed.
     */
    fun processHistory(ctx: Context, history: List<CallEntry>): Map<Long, Tag> {
        val on = isOn(ctx)
        val store = Store.get(ctx)
        val flags = store.flags()
        // Rules off: only the numbers you flagged yourself are looked at (and logged).
        if (history.isEmpty() || (!on && flags.isEmpty())) return emptyMap()
        val rows = if (on) history else history.filter { PhoneUtil.key(it.number) in flags }
        val prefs = Prefs(ctx)
        val version = prefs.identifierVersion
        val rules = if (on) store.rules() else emptyList()
        val categories = store.categories().associateBy { it.id }
        val cached = store.results(version)
        val ruleById = rules.associateBy { it.id }
        val out = HashMap<Long, Tag>()
        // Frequency / acceptance rules look at more history than the list on screen shows.
        val env = AppEnv(ctx, useDirectory = true) { io.github.amandhakar.contacts.data.CallLogRepo(ctx).load(STATS_ROWS) }
        val prepared = Engine.prepare(rules)   // parse, validate and compile the rules once for the whole run
        val broken = LinkedHashMap<Long, String>()

        fun fromCache(r: StoredResult): Tag? {
            val cat = r.categoryId?.let { categories[it] } ?: return null
            return Tag(cat.name, cat.color, r.via, r.ruleId?.let { ruleById[it]?.name }, r.ruleId)
        }

        try {
            store.batch { w ->
                for (e in rows) {
                    val hit = cached[e.id]
                    if (hit != null) { fromCache(hit)?.let { out[e.id] = it }; continue }
                    val t0 = System.nanoTime()
                    val rec = CallRecord(Source.HISTORY, e.number, e.cachedName, directionOf(e.type), e.date, e.durationSec, e.id)
                    var o: Outcome
                    var failure: String? = null
                    try {
                        o = Engine.classifyPrepared(rec, prepared, categories, flags, env)
                    } catch (ex: Exception) {
                        o = Outcome(Via.NONE, null, null, emptyList())
                        failure = "${ex.javaClass.simpleName}: ${ex.message}"
                    }
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    o.ruleErrors.forEach { broken[it.first] = it.second }
                    w.log(Source.HISTORY, e.number, e.cachedName, rec.direction, e.date, o, ms, failure)
                    w.putResult(e.id, version, o.category?.id, o.rule?.id, o.via)
                    tagOf(o)?.let { out[e.id] = it }
                }
            }
            broken.forEach { (id, msg) -> store.breakRule(id, msg) }
            store.trimLog(Store.LOG_KEEP)
        } catch (_: Exception) {
        }
        return out
    }
}

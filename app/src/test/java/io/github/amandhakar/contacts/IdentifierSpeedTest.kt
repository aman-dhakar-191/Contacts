package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.identifier.ActionStats
import io.github.amandhakar.contacts.identifier.CallRecord
import io.github.amandhakar.contacts.identifier.Category
import io.github.amandhakar.contacts.identifier.CondType
import io.github.amandhakar.contacts.identifier.Condition
import io.github.amandhakar.contacts.identifier.Direction
import io.github.amandhakar.contacts.identifier.Engine
import io.github.amandhakar.contacts.identifier.Env
import io.github.amandhakar.contacts.identifier.HistoryStats
import io.github.amandhakar.contacts.identifier.Rule
import io.github.amandhakar.contacts.identifier.Source
import io.github.amandhakar.contacts.util.PhoneUtil
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fast path (rules prepared once, call log indexed per number) must give exactly the same answers as the straightforward
 * one (rules parsed per call, call log walked per question). Also prints how long each takes.
 */
class IdentifierSpeedTest {
    private data class Row(val number: String, val date: Long, val type: Int)

    private val rnd = Random(42)
    private val numbers = List(150) { "9${100000000 + rnd.nextInt(899999999)}" } + List(30) { "140${1000000 + rnd.nextInt(8999999)}" } + listOf("")
    private val rows = List(2000) { Row(numbers[rnd.nextInt(numbers.size)], 1_700_000_000_000L + it * 3_600_000L, listOf(1, 2, 3, 3, 5, 6)[rnd.nextInt(6)]) }
    private val cats = mapOf(1L to Category(1, "Spam", 0))

    private abstract class BaseEnv : Env {
        override fun isKnown(number: String) = number.hashCode() % 3 == 0
        override fun labelsOf(number: String) = emptySet<String>()
        override fun contactName(number: String): String? = null
    }

    /** The straightforward way: walk the whole log for every question, recomputing each row's number key. */
    private val scanEnv = object : BaseEnv() {
        override fun stats(number: String, untilMs: Long, windowMs: Long): ActionStats {
            val key = PhoneUtil.key(number)
            if (key.isEmpty()) return ActionStats()
            var a = 0; var o = 0; var m = 0; var d = 0; var b = 0
            for (e in rows) {
                if (e.date > untilMs || e.date <= untilMs - windowMs || PhoneUtil.key(e.number) != key) continue
                when (e.type) { 1 -> a++; 2 -> o++; 3 -> m++; 5 -> d++; 6 -> b++ }
            }
            return ActionStats(a, o, m, d, b)
        }
    }

    /** The fast way: the log grouped by number once. */
    private val indexEnv = object : BaseEnv() {
        private val index = HistoryStats(rows.map { HistoryStats.Entry(PhoneUtil.key(it.number), it.date, it.type) })
        override fun stats(number: String, untilMs: Long, windowMs: Long): ActionStats {
            val key = PhoneUtil.key(number)
            return if (key.isEmpty()) ActionStats() else index.stats(key, untilMs, windowMs)
        }
    }

    private fun rules(): List<Rule> {
        var id = 1L
        val list = ArrayList<Rule>()
        fun add(vararg c: Condition) { list += Rule(id, "r$id", 1, true, id.toInt(), c.toList()); id++ }
        repeat(6) { add(Condition(CondType.PREFIX, "14$it")) }
        repeat(6) { add(Condition(CondType.REGEX, "^9[0-4]$it\\d{6}$")) }
        repeat(4) { add(Condition(CondType.DECLINED, "${it + 2}/30")) }
        repeat(4) { add(Condition(CondType.ACCEPT_RATE_BELOW, "${5 + it}/5")) }
        repeat(4) { add(Condition(CondType.UNKNOWN), Condition(CondType.TIME_RANGE, "2$it:00-07:00")) }
        repeat(3) { add(Condition(CondType.MISSED, "${20 + it}/7")) }
        repeat(3) { add(Condition(CondType.REPEAT, "30/1")) }
        // conditions listed expensive-first on purpose: the prepared path reorders them, the result must not change
        add(Condition(CondType.ANSWERED, "1/30"), Condition(CondType.UNKNOWN), Condition(CondType.PREFIX, "999"))
        return list
    }

    @Test fun indexGivesTheSameStatsAsWalkingTheLog() {
        val windows = listOf(86_400_000L, 7 * 86_400_000L, 30 * 86_400_000L, Long.MAX_VALUE / 4)
        for (n in numbers) for (w in windows) for (r in rows.filterIndexed { i, _ -> i % 97 == 0 }) {
            assertEquals("$n $w", scanEnv.stats(n, r.date, w), indexEnv.stats(n, r.date, w))
        }
    }

    @Test fun preparedAndPlainGiveTheSameCategories() {
        val rules = rules()
        val records = rows.take(500).mapIndexed { i, r -> CallRecord(Source.HISTORY, r.number, null, Direction.IN, r.date, 20, i.toLong()) }
        val prepared = Engine.prepare(rules)

        fun time(block: () -> Unit): Double { val t = System.nanoTime(); block(); return (System.nanoTime() - t) / 1_000_000.0 }
        repeat(3) { records.forEach { Engine.classify(it, rules, cats, emptyMap(), scanEnv); Engine.classifyPrepared(it, prepared, cats, emptyMap(), indexEnv) } } // warm up

        val plain = ArrayList<Long?>()
        val tPlain = time { records.forEach { plain += Engine.classify(it, rules, cats, emptyMap(), scanEnv).rule?.id } }
        val fast = ArrayList<Long?>()
        val tFast = time { records.forEach { fast += Engine.classifyPrepared(it, prepared, cats, emptyMap(), indexEnv).rule?.id } }

        assertEquals("same rule decides every call", plain, fast)
        println("SPEED rules=${rules.size} records=${records.size} history=${rows.size}: plain ${"%.1f".format(tPlain)} ms, prepared+indexed ${"%.1f".format(tFast)} ms, categorised ${fast.count { it != null }}")
        assertTrue("the whole run should take well under 2 s even on a slow CI machine: $tFast ms", tFast < 2000)
    }
}

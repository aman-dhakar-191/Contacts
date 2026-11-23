package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.identifier.CallRecord
import io.github.amandhakar.contacts.identifier.Category
import io.github.amandhakar.contacts.identifier.CondType
import io.github.amandhakar.contacts.identifier.Condition
import io.github.amandhakar.contacts.identifier.ConditionCodec
import io.github.amandhakar.contacts.identifier.Direction
import io.github.amandhakar.contacts.identifier.Engine
import io.github.amandhakar.contacts.identifier.Env
import io.github.amandhakar.contacts.identifier.Rule
import io.github.amandhakar.contacts.identifier.Source
import io.github.amandhakar.contacts.identifier.Via
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentifierEngineTest {
    private val spam = Category(1, "Spam", 0)
    private val delivery = Category(2, "Delivery", 0)
    private val cats = listOf(spam, delivery).associateBy { it.id }

    private class FakeEnv(
        val known: Set<String> = emptySet(),
        val labels: Map<String, Set<String>> = emptyMap(),
        val names: Map<String, String> = emptyMap(),
        val calls: Int = 0,
    ) : Env {
        override fun isKnown(number: String) = number in known
        override fun labelsOf(number: String) = labels[number].orEmpty()
        override fun contactName(number: String) = names[number]
        override fun callsFrom(number: String, untilMs: Long, windowMs: Long) = calls
    }

    private fun at(h: Int, m: Int = 0): Long = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 15, h, m, 0) }.timeInMillis

    private fun rec(
        number: String, dir: Direction = Direction.IN, time: Long = at(12), dur: Long? = null,
        source: Source = Source.HISTORY, name: String? = null,
    ) = CallRecord(source, number, name, dir, time, dur, 1)

    private fun rule(id: Long, name: String, cat: Category, vararg c: Condition, enabled: Boolean = true) =
        Rule(id, name, cat.id, enabled, id.toInt(), c.toList())

    private fun run(r: CallRecord, rules: List<Rule>, flags: Map<String, Long> = emptyMap(), env: Env = FakeEnv()) =
        Engine.classify(r, rules, cats, flags, env)

    @Test fun prefixMatchesAcrossFormats() {
        val rules = listOf(rule(1, "telemarketing", spam, Condition(CondType.PREFIX, "140")))
        assertEquals(spam, run(rec("1400123456"), rules).category)
        assertEquals(spam, run(rec("+91 1400-123456"), rules).category) // last 10 digits start with 140
        assertNull(run(rec("9876543210"), rules).category)
    }

    @Test fun firstMatchingRuleWinsAndOrderMatters() {
        val a = rule(1, "a", spam, Condition(CondType.PREFIX, "98"))
        val b = rule(2, "b", delivery, Condition(CondType.PREFIX, "98"))
        assertEquals(spam, run(rec("9876543210"), listOf(a, b)).category)
        assertEquals(delivery, run(rec("9876543210"), listOf(b, a)).category)
    }

    @Test fun switchedOffRulesAreSkippedAndShownInTheTrace() {
        val off = rule(1, "off", spam, Condition(CondType.HIDDEN), enabled = false)
        val o = run(rec(""), listOf(off))
        assertNull(o.category)
        assertEquals("off", o.trace.single().result)
    }

    @Test fun allConditionsMustMatch() {
        val r = rule(1, "unknown at night", spam, Condition(CondType.UNKNOWN), Condition(CondType.TIME_RANGE, "22:00-07:00"))
        assertEquals(spam, run(rec("5550100", time = at(23, 30)), listOf(r)).category)
        assertEquals(spam, run(rec("5550100", time = at(3)), listOf(r)).category)
        assertNull(run(rec("5550100", time = at(12)), listOf(r)).category)            // wrong time
        assertNull(run(rec("5550100", time = at(23)), listOf(r), env = FakeEnv(known = setOf("5550100"))).category) // known
    }

    @Test fun manualFlagBeatsRules() {
        val r = rule(1, "all spam", spam, Condition(CondType.UNKNOWN))
        val o = run(rec("9876543210"), listOf(r), flags = mapOf("9876543210" to delivery.id))
        assertEquals(Via.MANUAL, o.via)
        assertEquals(delivery, o.category)
    }

    @Test fun flagOnDeletedCategoryFallsBackToRules() {
        val r = rule(1, "all", spam, Condition(CondType.UNKNOWN))
        assertEquals(spam, run(rec("9876543210"), listOf(r), flags = mapOf("9876543210" to 99L)).category)
    }

    @Test fun hiddenUnknownKnownAndLabel() {
        assertEquals(spam, run(rec(""), listOf(rule(1, "h", spam, Condition(CondType.HIDDEN)))).category)
        val known = FakeEnv(known = setOf("111"), labels = mapOf("111" to setOf("Family")), names = mapOf("111" to "Asha Bank"))
        assertEquals(delivery, run(rec("111"), listOf(rule(1, "k", delivery, Condition(CondType.KNOWN))), env = known).category)
        assertEquals(delivery, run(rec("111"), listOf(rule(1, "l", delivery, Condition(CondType.LABEL, "family"))), env = known).category)
        assertNull(run(rec("111"), listOf(rule(1, "l", delivery, Condition(CondType.LABEL, "Work"))), env = known).category)
        assertEquals(delivery, run(rec("111"), listOf(rule(1, "n", delivery, Condition(CondType.NAME_CONTAINS, "bank"))), env = known).category)
    }

    @Test fun directionAndShortCallAndDigits() {
        assertEquals(spam, run(rec("5551234", Direction.MISSED), listOf(rule(1, "m", spam, Condition(CondType.DIRECTION, "missed")))).category)
        val short = rule(1, "short", spam, Condition(CondType.SHORT_CALL, "5"))
        assertEquals(spam, run(rec("5551234", dur = 3), listOf(short)).category)
        assertNull(run(rec("5551234", dur = 0), listOf(short)).category)            // not answered
        assertNull(run(rec("5551234", dur = 30), listOf(short)).category)
        assertNull(run(rec("5551234", dur = null, source = Source.LIVE), listOf(short)).category) // live call: no duration yet
        val digits = rule(1, "short code", spam, Condition(CondType.MAX_DIGITS, "6"))
        assertEquals(spam, run(rec("56767"), listOf(digits)).category)
        assertNull(run(rec("9876543210"), listOf(digits)).category)
        assertNull(run(rec(""), listOf(digits)).category)                             // hidden is not a short code
    }

    @Test fun repeatCallerCountsTheLiveCallToo() {
        val r = rule(1, "repeat", spam, Condition(CondType.REPEAT, "3/24"))
        assertEquals(spam, run(rec("5551234", source = Source.HISTORY), listOf(r), env = FakeEnv(calls = 3)).category)
        assertNull(run(rec("5551234", source = Source.HISTORY), listOf(r), env = FakeEnv(calls = 2)).category)
        assertEquals(spam, run(rec("5551234", source = Source.LIVE), listOf(r), env = FakeEnv(calls = 2)).category) // 2 earlier + this one
    }

    @Test fun regexAndBrokenRules() {
        assertEquals(spam, run(rec("1800123"), listOf(rule(1, "re", spam, Condition(CondType.REGEX, "^1[48]00")))).category)
        val broken = rule(1, "bad", spam, Condition(CondType.REGEX, "([")) // not a valid pattern
        val good = rule(2, "good", delivery, Condition(CondType.HIDDEN))
        val o = run(rec(""), listOf(broken, good))
        assertEquals(delivery, o.category)                 // the broken rule did not stop the others
        assertEquals(listOf(1L), o.ruleErrors.map { it.first })
        assertNotNull(Engine.validate(broken))
    }

    @Test fun validation() {
        assertNotNull(Engine.validate(rule(1, "none", spam)))
        assertNotNull(Engine.validate(rule(1, "p", spam, Condition(CondType.PREFIX, "abc"))))
        assertNotNull(Engine.validate(rule(1, "t", spam, Condition(CondType.TIME_RANGE, "25:00-07:00"))))
        assertNotNull(Engine.validate(rule(1, "r", spam, Condition(CondType.REPEAT, "0/24"))))
        assertNotNull(Engine.validate(rule(1, "d", spam, Condition(CondType.DIRECTION, "sideways"))))
        assertNull(Engine.validate(rule(1, "ok", spam, Condition(CondType.PREFIX, "140"))))
    }

    @Test fun parsersAndCodec() {
        assertEquals(3 to 24, Engine.parseRepeat("3/24"))
        assertNull(Engine.parseRepeat("3"))
        assertEquals(22 * 60 to 7 * 60, Engine.parseTimeRange("22:00-07:00"))
        assertEquals(Direction.DECLINED, Engine.directionOf("Rejected"))
        val list = listOf(Condition(CondType.PREFIX, "140"), Condition(CondType.HIDDEN), Condition(CondType.NAME_CONTAINS, "a\tb\nc"))
        val back = ConditionCodec.decode(ConditionCodec.encode(list))
        assertEquals(3, back.size)
        assertEquals(CondType.HIDDEN, back[1].type)
        assertEquals("a b c", back[2].param) // tabs and newlines can't break the format
        assertTrue(back[0].describe().contains("140"))
    }
}

package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.identifier.CallRecord
import io.github.amandhakar.contacts.identifier.Category
import io.github.amandhakar.contacts.identifier.CondType
import io.github.amandhakar.contacts.identifier.Condition
import io.github.amandhakar.contacts.identifier.Direction
import io.github.amandhakar.contacts.identifier.Engine
import io.github.amandhakar.contacts.identifier.ActionStats
import io.github.amandhakar.contacts.identifier.Env
import io.github.amandhakar.contacts.identifier.Rule
import io.github.amandhakar.contacts.identifier.RuleExchange
import io.github.amandhakar.contacts.identifier.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleExchangeTest {
    private val spam = Category(1, "Spam", 0xFFD32F2F.toInt())
    private val bank = Category(2, "Bank", 0xFF1976D2.toInt())
    private val cats = listOf(spam, bank)

    private fun rule(id: Long, name: String, cat: Category, vararg c: Condition, enabled: Boolean = true) =
        Rule(id, name, cat.id, enabled, id.toInt(), c.toList())

    private val rules = listOf(
        rule(1, "Telemarketing", spam, Condition(CondType.PREFIX, "140, 160")),
        rule(2, "Hidden", spam, Condition(CondType.HIDDEN), enabled = false),
        rule(3, "Bank name at night", bank, Condition(CondType.NAME_CONTAINS, "Bank"), Condition(CondType.TIME_RANGE, "22:00-07:00")),
    )

    private fun ok(text: String) = (RuleExchange.parse(text) as RuleExchange.Result.Ok).parsed
    private fun bad(text: String) = (RuleExchange.parse(text) as RuleExchange.Result.Bad).message

    @Test fun exportThenImportKeepsEverythingIncludingOrder() {
        val back = ok(RuleExchange.export(cats, rules, 0))
        assertEquals(listOf("Telemarketing", "Hidden", "Bank name at night"), back.rules.map { it.name })
        assertEquals(listOf(true, false, true), back.rules.map { it.enabled })
        assertEquals(listOf("Spam", "Spam", "Bank"), back.rules.map { it.category })
        assertEquals(rules[2].conditions, back.rules[2].conditions)
        assertEquals(listOf("Spam", "Bank"), back.categories.map { it.name })
        assertEquals(spam.color, back.categories[0].color)
        assertTrue(back.skipped.isEmpty())
    }

    @Test fun theFileHoldsRulesAndNeverNumbers() {
        val text = RuleExchange.export(cats, rules, 0)
        assertTrue(text.contains("contacts-identifier-rules"))
        assertFalse(Regex("\\d{7,}").containsMatchIn(text.replace(Regex("\"exportedAt\": \\d+"), "")))
    }

    @Test fun badFilesAreRefusedWithAReason() {
        assertTrue(bad("not json at all").contains("not valid JSON"))
        assertTrue(bad("""{"format":"something-else","version":1,"rules":[]}""").contains("not an identifier"))
        assertTrue(bad("""{"format":"contacts-identifier-rules","version":99,"rules":[]}""").contains("newer version"))
        assertTrue(bad("""{"format":"contacts-identifier-rules","version":1}""").contains("no rules"))
        assertTrue(bad("x".repeat(RuleExchange.MAX_CHARS + 1)).contains("too large"))
        val many = (1..RuleExchange.MAX_RULES + 1).joinToString(",") { """{"name":"r$it","category":"A","conditions":[{"type":"HIDDEN"}]}""" }
        assertTrue(bad("""{"format":"contacts-identifier-rules","version":1,"rules":[$many]}""").contains("more than"))
    }

    @Test fun brokenRulesAreSkippedAndReportedTheRestAreKept() {
        val text = """{"format":"contacts-identifier-rules","version":1,"categories":[],"rules":[
            {"name":"good","category":"Spam","conditions":[{"type":"PREFIX","param":"140"}]},
            {"name":"bad pattern","category":"Spam","conditions":[{"type":"REGEX","param":"(["}]},
            {"name":"from the future","category":"Spam","conditions":[{"type":"TELEPATHY","param":"x"}]},
            {"name":"empty","category":"Spam","conditions":[]},
            {"name":"","category":"Spam","conditions":[{"type":"HIDDEN"}]},
            {"name":"no category","conditions":[{"type":"HIDDEN"}]},
            "not an object"
        ]}"""
        val p = ok(text)
        assertEquals(listOf("good"), p.rules.map { it.name })
        assertEquals(6, p.skipped.size)
        assertTrue(p.skipped.any { it.contains("TELEPATHY") })
        assertTrue(p.skipped.any { it.contains("bad pattern") })
    }

    @Test fun textIsCleanedAndCapped() {
        val long = "n".repeat(500)
        val p = ok("""{"format":"contacts-identifier-rules","version":1,"rules":[{"name":"a\u0007b${long}","category":"S","conditions":[{"type":"HIDDEN","param":"ignored"}]}]}""")
        assertTrue(p.rules[0].name.length <= 200)
        assertFalse(p.rules[0].name.contains('\u0007'))
        assertEquals("", p.rules[0].conditions[0].param)   // a condition without a setting gets none
    }

    @Test fun colorsParseOrFallBackToGrey() {
        val p = ok("""{"format":"contacts-identifier-rules","version":1,"categories":[{"name":"A","color":"#112233"},{"name":"B","color":"nonsense"},{"name":"C","color":"#80112233"}],"rules":[]}""")
        assertEquals(0xFF112233.toInt(), p.categories[0].color)
        assertEquals(0xFF455A64.toInt(), p.categories[1].color)
        assertEquals(0x80112233.toInt(), p.categories[2].color)
    }

    @Test fun addingLeavesOutRulesThatAlreadyDoTheSame() {
        val parsed = ok(RuleExchange.export(cats, rules, 0))
        val again = RuleExchange.plan(rules, cats, parsed, replace = false)
        assertEquals(0, again.toAdd.size)
        assertEquals(3, again.duplicates)
        // a new rule plus one that exists
        val extra = RuleExchange.Parsed(
            listOf(RuleExchange.CategorySpec("Delivery", 0xFF00FF00.toInt())),
            listOf(
                RuleExchange.RuleSpec("Hidden again", "spam", true, listOf(Condition(CondType.HIDDEN))),   // same as rule 2 (name and case don't matter)
                RuleExchange.RuleSpec("Courier", "Delivery", true, listOf(Condition(CondType.PREFIX, "1800"))),
            ),
            emptyList(),
        )
        val plan = RuleExchange.plan(rules, cats, extra, replace = false)
        assertEquals(listOf("Courier"), plan.toAdd.map { it.name })
        assertEquals(1, plan.duplicates)
        assertEquals(listOf("Delivery"), plan.newCategories.map { it.name })
    }

    @Test fun replacingIgnoresWhatIsThereAndRepeatsInsideTheFileCountOnce() {
        val spec = RuleExchange.RuleSpec("x", "Spam", true, listOf(Condition(CondType.HIDDEN)))
        val parsed = RuleExchange.Parsed(emptyList(), listOf(spec, spec.copy(name = "y")), emptyList())
        val plan = RuleExchange.plan(rules, cats, parsed, replace = true)
        assertEquals(1, plan.toAdd.size)
        assertEquals(1, plan.duplicates)
        assertTrue(plan.replace)
    }

    @Test fun aCategoryOnlyNamedByARuleGetsANeutralColour() {
        val parsed = RuleExchange.Parsed(emptyList(), listOf(RuleExchange.RuleSpec("r", "Mystery", true, listOf(Condition(CondType.HIDDEN)))), emptyList())
        val plan = RuleExchange.plan(emptyList(), emptyList(), parsed, replace = false)
        assertEquals("Mystery", plan.newCategories.single().name)
        assertEquals(0xFF455A64.toInt(), plan.newCategories.single().color)
    }

    @Test fun importedPatternsOnlySeeAShortSliceOfTheNumber() {
        val env = object : Env {
            override fun isKnown(number: String) = false
            override fun labelsOf(number: String) = emptySet<String>()
            override fun contactName(number: String): String? = null
            override fun stats(number: String, untilMs: Long, windowMs: Long) = ActionStats()
        }
        val r = Rule(1, "p", 1, true, 1, listOf(Condition(CondType.REGEX, "^140")))
        val rec = CallRecord(Source.HISTORY, "1400123456", null, Direction.IN, 0, null, null)
        assertNotNull(Engine.classify(rec, listOf(r), mapOf(1L to spam), emptyMap(), env).category)
        // a pattern that needs the end of a very long "number" cannot match past the cut-off
        val tail = Rule(2, "tail", 1, true, 2, listOf(Condition(CondType.REGEX, "9$")))
        val long = CallRecord(Source.HISTORY, "1".repeat(40) + "9", null, Direction.IN, 0, null, null)
        assertEquals(null, Engine.classify(long, listOf(tail), mapOf(1L to spam), emptyMap(), env).category)
    }
}

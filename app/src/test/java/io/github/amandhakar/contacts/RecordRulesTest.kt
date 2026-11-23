package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.data.RecordReason
import io.github.amandhakar.contacts.data.RecordRules
import io.github.amandhakar.contacts.data.RuleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordRulesTest {
    @Test fun nothingIsRecordedWhenAllRulesAreOff() {
        assertNull(RecordRules.decide(all = false, unknown = false, inList = false, isKnownContact = false))
        assertNull(RecordRules.decide(false, false, false, true))
    }

    @Test fun unknownRuleOnlyAppliesToNumbersNotInContacts() {
        assertEquals(RecordReason.UNKNOWN, RecordRules.decide(false, true, false, false))
        assertNull(RecordRules.decide(false, true, false, true))
    }

    @Test fun listAlwaysWinsAndNamesTheListAsTheReason() {
        assertEquals(RecordReason.LIST, RecordRules.decide(false, false, true, true))
        assertEquals(RecordReason.LIST, RecordRules.decide(true, true, true, false))
    }

    @Test fun recordAllRecordsKnownContactsToo() {
        assertEquals(RecordReason.ALL, RecordRules.decide(true, false, false, true))
    }

    @Test fun listSurvivesEncodingIncludingOddCharacters() {
        val list = listOf(
            RuleEntry("9876543210", "+91 98765 43210", "Asha | Rao"),
            RuleEntry("5550100", "555-0100", ""),
        )
        val back = RecordRules.parse(RecordRules.encode(list))
        assertEquals(2, back.size)
        assertEquals("9876543210", back[0].key)
        assertEquals("+91 98765 43210", back[0].number)
        assertEquals("Asha   Rao", back[0].label) // '|' is replaced so it can't break the format
        assertEquals("", back[1].label)
    }

    @Test fun emptyOrBrokenListParsesToNothing() {
        assertEquals(0, RecordRules.parse("").size)
        assertEquals(0, RecordRules.parse("garbage\n||").size)
    }
}

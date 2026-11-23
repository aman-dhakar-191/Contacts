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

class AccountFilterTest {
    private val rows = listOf(
        Triple(1L, "me@gmail.com", "com.google"),
        Triple(2L, "kiran@gmail.com", "com.google"),
        Triple(3L, null, null),
        Triple(3L, "WhatsApp", "com.whatsapp"), // a contact can have rows in several accounts
        Triple(4L, "WhatsApp", "com.whatsapp"),
    )

    @org.junit.Test fun nothingHiddenKeepsEveryContact() {
        org.junit.Assert.assertEquals(setOf(1L, 2L, 3L, 4L), io.github.amandhakar.contacts.data.AccountInfo.visibleContactIds(rows, emptySet()))
    }

    @org.junit.Test fun hidingAnAccountDropsContactsOnlyInThatAccount() {
        val hidden = setOf(io.github.amandhakar.contacts.data.AccountInfo.keyOf("kiran@gmail.com", "com.google"), io.github.amandhakar.contacts.data.AccountInfo.keyOf("WhatsApp", "com.whatsapp"))
        // 2 is only in Kiran's account, 4 only in WhatsApp; 3 also lives on the phone so it stays
        org.junit.Assert.assertEquals(setOf(1L, 3L), io.github.amandhakar.contacts.data.AccountInfo.visibleContactIds(rows, hidden))
    }

    @org.junit.Test fun phoneOnlyAccountHasItsOwnKey() {
        org.junit.Assert.assertEquals("|", io.github.amandhakar.contacts.data.AccountInfo.keyOf(null, null))
        org.junit.Assert.assertEquals("com.google|a@b.c", io.github.amandhakar.contacts.data.AccountInfo(name = "a@b.c", type = "com.google").key)
    }
}

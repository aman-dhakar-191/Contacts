package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.data.ContactSummary
import io.github.amandhakar.contacts.data.PhoneEntry
import io.github.amandhakar.contacts.ui.dialHits
import org.junit.Assert.assertEquals
import org.junit.Test

class DialHitsTest {
    private val aman = ContactSummary(4411, "k1", "Aman", null, false)
    private val other = ContactSummary(7, "k2", "Other", null, false)

    @Test fun sameNumberTwiceOnOneContactIsListedOnce() {
        // Crashed in 0.3.13: the same number saved twice gave two rows with the key "4411:+917008633195".
        val phones = mapOf(aman.id to listOf(PhoneEntry("+917008633195", "Mobile"), PhoneEntry("+917008633195", "Home")))
        val hits = dialHits("7008", listOf(aman), phones, 20)
        assertEquals(1, hits.size)
        assertEquals(1, hits.map { "${it.first.id}:${it.second.number}" }.toSet().size)
    }

    @Test fun differentFormattingOfTheSameNumberIsListedOnce() {
        val phones = mapOf(aman.id to listOf(PhoneEntry("+91 70086 33195", "Mobile"), PhoneEntry("07008633195", "Work")))
        assertEquals(1, dialHits("7008", listOf(aman), phones, 20).size)
    }

    @Test fun differentContactsWithTheSameNumberAreBothListed() {
        val phones = mapOf(aman.id to listOf(PhoneEntry("7008633195", "Mobile")), other.id to listOf(PhoneEntry("7008633195", "Mobile")))
        assertEquals(2, dialHits("7008", listOf(aman, other), phones, 20).size)
    }

    @Test fun shortInputGivesNoMatches() {
        val phones = mapOf(aman.id to listOf(PhoneEntry("7008633195", "Mobile")))
        assertEquals(0, dialHits("7", listOf(aman), phones, 20).size)
    }
}

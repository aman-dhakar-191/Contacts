package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.util.CallerLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerLabelTest {
    @Test fun splitsCarrierSpamAlertFromName() {
        val l = CallerLabels.parse("Vi Alert: Spam REENA PATEL")
        assertEquals("REENA PATEL", l.name)
        assertEquals("Vi Alert", l.source)
        assertEquals("Spam", l.category)
        assertEquals("Vi Alert · Spam", l.alert)
        assertEquals("Spam", l.alertShort)
    }

    @Test fun alertWithoutCategoryKeepsRestAsName() {
        val l = CallerLabels.parse("Vi Alert: Some Shop")
        assertEquals("Some Shop", l.name)
        assertEquals("Vi Alert", l.alert)
        assertEquals("Vi Alert", l.alertShort)
    }

    @Test fun alertOnlyHasNoName() {
        val l = CallerLabels.parse("Vi Alert: Spam")
        assertNull(l.name)
        assertEquals("Vi Alert · Spam", l.alert)
    }

    @Test fun categoryIsCaseInsensitiveAndSpacingTolerant() {
        val l = CallerLabels.parse("  vi alert :  SCAM   Raj Kumar ")
        assertEquals("Raj Kumar", l.name)
        assertEquals("Scam", l.category)
    }

    @Test fun ordinaryNamesAreLeftAlone() {
        for (n in listOf("REENA PATEL", "Alert Systems Ltd", "Spam Busters", "Alertness: Gym")) {
            val l = CallerLabels.parse(n)
            assertEquals(n, l.name)
            assertFalse(n, l.hasAlert)
        }
    }

    @Test fun categoryWordInsideANameIsNotStripped() {
        // Only a word right after "Alert:" counts as the category.
        val l = CallerLabels.parse("Vi Alert: Raj Spam Kumar")
        assertEquals("Raj Spam Kumar", l.name)
        assertTrue(l.hasAlert)
    }
}

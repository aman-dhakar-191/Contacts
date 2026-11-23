package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.data.RecordingsRepo
import io.github.amandhakar.contacts.util.PhoneUtil
import io.github.amandhakar.contacts.util.T9
import io.github.amandhakar.contacts.util.VCard
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun t9EncodesLetters() {
        assertEquals("563", T9.encode("Joe"))
        assertEquals("56 66", T9.encode("Jo Mo"))
    }

    @Test fun t9MatchesStartOfAnyWord() {
        assertTrue(T9.matches("Joe Smith", "563"))
        assertTrue(T9.matches("Joe Smith", "76484")) // "Smith" -> 7-6-4-8-4
        assertFalse(T9.matches("Joe Smith", "999"))
        assertTrue(T9.matches("Anyone", ""))
    }

    @Test fun phoneKeyIgnoresFormattingAndCountryCode() {
        assertEquals(PhoneUtil.key("+91 98765-43210"), PhoneUtil.key("09876543210"))
        assertEquals("12345", PhoneUtil.key("12345"))
    }

    @Test fun vcardParsesNamesPhonesAndEmails() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Asha Rao
            N:Rao;Asha;;;
            TEL;TYPE=CELL:+91 98765 43210
            item1.EMAIL;TYPE=INTERNET:asha@example.com
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            N:Smith;Joe;;;
            TEL:555-0100
            TEL:555-0100
            END:VCARD
        """.trimIndent()
        val cards = VCard.parse(text)
        assertEquals(2, cards.size)
        assertEquals("Asha Rao", cards[0].name)
        assertEquals(listOf("+91 98765 43210"), cards[0].phones)
        assertEquals(listOf("asha@example.com"), cards[0].emails)
        assertEquals("Joe Smith", cards[1].name)
        assertEquals(listOf("555-0100"), cards[1].phones) // duplicate removed
    }

    @Test fun vcardUnfoldsLongLines() {
        val cards = VCard.parse("BEGIN:VCARD\r\nFN:A very long\r\n  name\r\nEND:VCARD\r\n")
        assertEquals("A very long name", cards.single().name)
    }

    @Test fun recordingFileNamesRoundTrip() {
        val f = File("/tmp/1700000000000_in_mic_+919876543210.m4a")
        val r = RecordingsRepo.parse(f)
        assertNotNull(r)
        assertEquals(1700000000000L, r!!.date)
        assertTrue(r.incoming)
        assertFalse(r.bothSides)
        assertEquals("+919876543210", r.number)
        assertNull(RecordingsRepo.parse(File("/tmp/notes.txt")))
    }
}

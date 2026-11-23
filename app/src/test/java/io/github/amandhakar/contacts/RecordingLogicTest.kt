package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.call.ProbeResult
import io.github.amandhakar.contacts.call.ProbeStatus
import io.github.amandhakar.contacts.call.RecordingProbe
import io.github.amandhakar.contacts.data.RecSource
import io.github.amandhakar.contacts.util.AudioStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingLogicTest {
    @Test fun allZerosIsSilent() {
        val lv = AudioStats.level(ShortArray(1000))
        assertEquals(0, lv.peak)
        assertEquals(-90.0, lv.rmsDb, 0.0)
        assertTrue(AudioStats.isSilent(lv.peak))
    }

    @Test fun quietRoomIsNotSilent() {
        val noise = ShortArray(1000) { if (it % 2 == 0) 300 else -300 }
        val lv = AudioStats.level(noise)
        assertEquals(300, lv.peak)
        assertFalse(AudioStats.isSilent(lv.peak))
        assertTrue(lv.rmsDb < 0 && lv.rmsDb > -60)
    }

    @Test fun levelOnlyLooksAtCount() {
        val data = ShortArray(100) { 0 }.also { it[99] = 20000 }
        assertEquals(0, AudioStats.level(data, 50).peak)
        assertEquals(20000, AudioStats.level(data, 100).peak)
    }

    @Test fun wavHeaderIsValid() {
        val h = AudioStats.wavHeader(32000, 16000)
        assertEquals(44, h.size)
        assertEquals("RIFF", String(h, 0, 4))
        assertEquals("WAVE", String(h, 8, 4))
        assertEquals("data", String(h, 36, 4))
        val size = java.nio.ByteBuffer.wrap(h, 40, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        assertEquals(32000, size)
    }

    @Test fun chosenSourceGoesFirstWithFallbacks() {
        val c = RecSource.chain("UNPROCESSED")
        assertEquals(RecSource.UNPROCESSED, c.first())
        assertEquals(RecSource.AUTO_CHAIN.size, c.size)
        assertEquals(RecSource.AUTO_CHAIN, RecSource.chain("auto"))
        assertEquals(RecSource.AUTO_CHAIN, RecSource.chain("nonsense"))
    }

    private fun r(src: RecSource, st: ProbeStatus) = ProbeResult(src, st, 100, -30.0, false, null, "")

    @Test fun verdictSaysWhatWorks() {
        assertTrue(RecordingProbe.verdict(emptyList()).contains("No test"))
        assertTrue(RecordingProbe.verdict(listOf(r(RecSource.MIC, ProbeStatus.SILENT))).contains("Nothing was captured"))
        assertTrue(RecordingProbe.verdict(listOf(r(RecSource.MIC, ProbeStatus.SIGNAL))).contains("microphone"))
        assertTrue(RecordingProbe.verdict(listOf(r(RecSource.VOICE_CALL, ProbeStatus.SIGNAL))).contains("capture the call audio"))
    }
}

package io.github.amandhakar.contacts.util

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Measuring recorded audio, so we can tell real sound from the silence Android hands to blocked apps. */
object AudioStats {
    /** Peak amplitude (0..32767) below this counts as silence. A blocked input is exactly 0; a quiet room is far above 50. */
    const val SILENT_PEAK = 50

    data class Level(val peak: Int, val rmsDb: Double)

    fun level(samples: ShortArray, count: Int = samples.size): Level {
        if (count <= 0) return Level(0, -90.0)
        var peak = 0
        var sum = 0.0
        for (i in 0 until count) {
            val v = abs(samples[i].toInt())
            if (v > peak) peak = v
            sum += v.toDouble() * v
        }
        val rms = sqrt(sum / count)
        val db = if (rms < 1.0) -90.0 else 20 * log10(rms / 32768.0)
        return Level(peak, db)
    }

    fun isSilent(peak: Int) = peak < SILENT_PEAK

    /** 44-byte header for 16-bit mono PCM. */
    fun wavHeader(dataBytes: Int, sampleRate: Int): ByteArray {
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        h.putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(dataBytes)
        return h.array()
    }
}

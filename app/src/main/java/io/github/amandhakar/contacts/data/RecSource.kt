package io.github.amandhakar.contacts.data

import android.media.MediaRecorder

/** Audio inputs Android offers for a call, from most to least useful. */
enum class RecSource(val id: Int, val label: String, val capturesFarSide: Boolean) {
    VOICE_CALL(MediaRecorder.AudioSource.VOICE_CALL, "Call audio (both sides)", true),
    VOICE_DOWNLINK(MediaRecorder.AudioSource.VOICE_DOWNLINK, "Call audio, other side only", true),
    VOICE_UPLINK(MediaRecorder.AudioSource.VOICE_UPLINK, "Call audio, your side only", false),
    VOICE_COMMUNICATION(MediaRecorder.AudioSource.VOICE_COMMUNICATION, "Voice communication mic", false),
    VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION, "Voice recognition mic", false),
    UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED, "Unprocessed mic", false),
    MIC(MediaRecorder.AudioSource.MIC, "Microphone", false);

    companion object {
        /** What the recorder tries, in order, when no source was chosen. */
        val AUTO_CHAIN = listOf(VOICE_CALL, VOICE_RECOGNITION, VOICE_COMMUNICATION, UNPROCESSED, MIC)

        /** `preferred` is a saved enum name or "auto". The chosen source goes first, the rest follow as fallbacks. */
        fun chain(preferred: String): List<RecSource> {
            val p = entries.firstOrNull { it.name == preferred } ?: return AUTO_CHAIN
            return listOf(p) + AUTO_CHAIN.filter { it != p }
        }
    }
}

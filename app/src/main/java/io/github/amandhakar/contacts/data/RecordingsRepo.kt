package io.github.amandhakar.contacts.data

import android.content.Context
import io.github.amandhakar.contacts.util.RecordingDiagnostics
import java.io.File

/**
 * Recordings are plain files in the app's own storage. The name carries the metadata:
 * `<epochMillis>_<in|out>_<tag>_<number>.m4a`, where the tag is "both" (call audio), "mic" (older microphone recordings)
 * or "mic-<input>-<route>" (see [RecordingDiagnostics.captureTag]).
 */
class RecordingsRepo(private val ctx: Context) {
    private val dir: File get() = (ctx.getExternalFilesDir("recordings") ?: File(ctx.filesDir, "recordings")).also { it.mkdirs() }

    fun newFile(incoming: Boolean, bothSides: Boolean, number: String): File {
        val safe = number.filter { it.isDigit() || it == '+' }.ifEmpty { "unknown" }
        return File(dir, "${System.currentTimeMillis()}_${if (incoming) "in" else "out"}_${if (bothSides) "both" else "mic"}_$safe.m4a")
    }

    fun list(): List<RecordingEntry> =
        (dir.listFiles() ?: emptyArray()).mapNotNull { parse(it) }.sortedByDescending { it.date }

    /** Renames a recording to carry [tag]. Returns the new file, or the old one if the rename didn't work. */
    fun retag(f: File, tag: String): File {
        val name = RecordingDiagnostics.withTag(f.name, tag) ?: return f
        if (name == f.name) return f
        val to = File(f.parentFile, name)
        return if (f.renameTo(to)) to else f
    }

    fun delete(path: String) {
        File(path).takeIf { it.parentFile == dir }?.delete()
    }

    companion object {
        fun parse(f: File): RecordingEntry? {
            if (!f.name.endsWith(".m4a")) return null
            val p = f.name.removeSuffix(".m4a").split('_', limit = 4)
            if (p.size < 4) return null
            val date = p[0].toLongOrNull() ?: return null
            val capture = RecordingDiagnostics.parseCaptureTag(p[2])
            return RecordingEntry(
                f.absolutePath, date, p[1] == "in", p[3], capture.bothSides, f.length(), RecordingDiagnostics.captureLabel(capture),
            )
        }
    }
}

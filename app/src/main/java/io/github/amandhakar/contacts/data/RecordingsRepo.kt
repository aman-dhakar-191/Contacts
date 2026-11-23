package io.github.amandhakar.contacts.data

import android.content.Context
import java.io.File

/**
 * Recordings are plain files in the app's own storage. The name carries the metadata:
 * `<epochMillis>_<in|out>_<both|mic>_<number>.m4a`.
 */
class RecordingsRepo(private val ctx: Context) {
    private val dir: File get() = (ctx.getExternalFilesDir("recordings") ?: File(ctx.filesDir, "recordings")).also { it.mkdirs() }

    fun newFile(incoming: Boolean, bothSides: Boolean, number: String): File {
        val safe = number.filter { it.isDigit() || it == '+' }.ifEmpty { "unknown" }
        return File(dir, "${System.currentTimeMillis()}_${if (incoming) "in" else "out"}_${if (bothSides) "both" else "mic"}_$safe.m4a")
    }

    fun list(): List<RecordingEntry> =
        (dir.listFiles() ?: emptyArray()).mapNotNull { parse(it) }.sortedByDescending { it.date }

    fun delete(path: String) {
        File(path).takeIf { it.parentFile == dir }?.delete()
    }

    companion object {
        fun parse(f: File): RecordingEntry? {
            if (!f.name.endsWith(".m4a")) return null
            val p = f.name.removeSuffix(".m4a").split('_', limit = 4)
            if (p.size < 4) return null
            val date = p[0].toLongOrNull() ?: return null
            return RecordingEntry(f.absolutePath, date, p[1] == "in", p[3], p[2] == "both", f.length())
        }
    }
}

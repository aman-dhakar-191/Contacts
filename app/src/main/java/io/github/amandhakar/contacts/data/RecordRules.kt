package io.github.amandhakar.contacts.data

import android.content.Context
import io.github.amandhakar.contacts.util.PhoneUtil

enum class RecordReason(val label: String) {
    LIST("auto-record list"),
    ALL("all calls"),
    UNKNOWN("unknown number"),
}

data class RuleEntry(val key: String, val number: String, val label: String)

/** Decides which calls are recorded by themselves: everything, unknown numbers, or numbers on the auto-record list. */
class RecordRules(ctx: Context) {
    private val prefs = Prefs(ctx)

    fun entries(): List<RuleEntry> = parse(prefs.recordList)

    fun contains(number: String): Boolean {
        val k = PhoneUtil.key(number)
        return k.length >= MIN_DIGITS && entries().any { it.key == k }
    }

    /** Adds a number (with a name to show). Returns false if it is too short or already there. */
    fun add(number: String, label: String?): Boolean {
        val k = PhoneUtil.key(number)
        if (k.length < MIN_DIGITS) return false
        val list = entries()
        if (list.any { it.key == k }) return false
        prefs.recordList = encode(list + RuleEntry(k, number.trim(), label?.trim().orEmpty()))
        return true
    }

    fun remove(number: String) {
        val k = PhoneUtil.key(number)
        prefs.recordList = encode(entries().filter { it.key != k })
    }

    fun reason(number: String, isKnownContact: Boolean): RecordReason? =
        decide(prefs.autoRecord, prefs.recordUnknown, contains(number), isKnownContact)

    companion object {
        const val MIN_DIGITS = 3

        /** The list wins (so the reason shown is the most specific one), then "all", then "unknown". */
        fun decide(all: Boolean, unknown: Boolean, inList: Boolean, isKnownContact: Boolean): RecordReason? = when {
            inList -> RecordReason.LIST
            all -> RecordReason.ALL
            unknown && !isKnownContact -> RecordReason.UNKNOWN
            else -> null
        }

        fun encode(list: List<RuleEntry>): String =
            list.joinToString("\n") { "${it.key}|${it.number.clean()}|${it.label.clean()}" }

        fun parse(text: String): List<RuleEntry> = text.lineSequence().mapNotNull { line ->
            val p = line.split('|', limit = 3)
            if (p.size < 3 || p[0].isBlank()) null else RuleEntry(p[0], p[1], p[2])
        }.toList()

        private fun String.clean() = replace('|', ' ').replace('\n', ' ')
    }
}

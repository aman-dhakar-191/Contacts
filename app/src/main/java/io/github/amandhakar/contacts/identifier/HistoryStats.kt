package io.github.amandhakar.contacts.identifier

/**
 * The call log grouped by number, so "what did I do with this number" looks only at that number's calls instead of
 * walking the whole log (and recomputing every row's number key) for every question. Pure code, unit tested.
 */
class HistoryStats(entries: List<Entry>) {
    /** [key] is the last-10-digits form of the number; [type] is an Android call-log type. */
    data class Entry(val key: String, val date: Long, val type: Int)

    private val byKey: Map<String, List<Entry>> = entries.groupBy { it.key }

    fun stats(key: String, untilMs: Long, windowMs: Long): ActionStats {
        val list = byKey[key] ?: return ActionStats()
        var answered = 0; var outgoing = 0; var missed = 0; var declined = 0; var blocked = 0
        for (e in list) {
            if (e.date > untilMs || e.date <= untilMs - windowMs) continue
            when (e.type) {
                INCOMING -> answered++
                OUTGOING -> outgoing++
                MISSED -> missed++
                REJECTED -> declined++
                BLOCKED -> blocked++
            }
        }
        return ActionStats(answered, outgoing, missed, declined, blocked)
    }

    companion object {
        // android.provider.CallLog.Calls types
        const val INCOMING = 1
        const val OUTGOING = 2
        const val MISSED = 3
        const val REJECTED = 5
        const val BLOCKED = 6
    }
}

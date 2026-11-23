package io.github.amandhakar.contacts.data

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog

class CallLogRepo(private val ctx: Context) {
    fun load(limit: Int = 500): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE,
                    CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME,
                ),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    out += CallEntry(c.getLong(0), c.getString(1) ?: "", c.getInt(2), c.getLong(3), c.getLong(4), c.getString(5))
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }

    fun delete(id: Long) {
        try {
            ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID}=?", arrayOf(id.toString()))
        } catch (_: SecurityException) {
        }
    }

    fun clearAll() {
        try {
            ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (_: SecurityException) {
        }
    }

    fun markMissedRead() {
        try {
            val v = ContentValues().apply { put(CallLog.Calls.NEW, 0); put(CallLog.Calls.IS_READ, 1) }
            ctx.contentResolver.update(
                CallLog.Calls.CONTENT_URI, v,
                "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.IS_READ}=0", arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
            )
        } catch (_: SecurityException) {
        }
    }
}

package io.github.amandhakar.contacts.data

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

data class BlockedNumber(val id: Long, val number: String)

/** Android only lets the default phone app edit the system block list. */
class BlockedRepo(private val ctx: Context) {
    fun canBlock(): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(ctx)
    } catch (_: Exception) {
        false
    }

    fun list(): List<BlockedNumber> {
        val out = ArrayList<BlockedNumber>()
        try {
            ctx.contentResolver.query(
                BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumbers.COLUMN_ID, BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null,
            )?.use { c -> while (c.moveToNext()) out += BlockedNumber(c.getLong(0), c.getString(1) ?: "") }
        } catch (_: Exception) {
        }
        return out
    }

    fun block(number: String): Boolean = try {
        ctx.contentResolver.insert(
            BlockedNumbers.CONTENT_URI,
            ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
        ) != null
    } catch (_: Exception) {
        false
    }

    fun unblock(id: Long) {
        try {
            ctx.contentResolver.delete(BlockedNumbers.CONTENT_URI, "${BlockedNumbers.COLUMN_ID}=?", arrayOf(id.toString()))
        } catch (_: Exception) {
        }
    }

    fun isBlocked(number: String): Boolean = try {
        BlockedNumberContract.isBlocked(ctx, number)
    } catch (_: Exception) {
        false
    }
}

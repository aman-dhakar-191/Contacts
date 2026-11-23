package io.github.amandhakar.contacts.call

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast

object Dialer {
    fun accounts(ctx: Context): List<PhoneAccountHandle> = try {
        ctx.getSystemService(TelecomManager::class.java).callCapablePhoneAccounts
    } catch (_: SecurityException) {
        emptyList()
    }

    fun label(ctx: Context, h: PhoneAccountHandle): String = try {
        ctx.getSystemService(TelecomManager::class.java).getPhoneAccount(h)?.label?.toString() ?: "SIM"
    } catch (_: SecurityException) {
        "SIM"
    }

    fun call(ctx: Context, number: String, handle: PhoneAccountHandle? = null) {
        if (number.isBlank()) return
        try {
            val extras = Bundle()
            if (handle != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            ctx.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), extras)
        } catch (e: SecurityException) {
            io.github.amandhakar.contacts.util.Telemetry.event("call_blocked_no_permission")
            Toast.makeText(ctx, "Allow the Phone permission first", Toast.LENGTH_SHORT).show()
        }
    }
}

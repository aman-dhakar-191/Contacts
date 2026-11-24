package io.github.amandhakar.contacts.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import io.github.amandhakar.contacts.MainActivity
import io.github.amandhakar.contacts.R

object Notifications {
    // New ids: a channel's sound/importance can't be changed once created. Android itself rings for an incoming call,
    // so these channels are silent (the old "incoming" channel had its own sound and could ring twice).
    const val CH_INCOMING = "incoming_call"
    const val CH_ONGOING = "ongoing_call"
    const val CH_MISSED = "missed"
    const val CH_RECORDING = "recording"

    const val ID_INCOMING = 1
    const val ID_ONGOING = 2
    const val ID_MISSED = 3
    const val ID_RECORDING = 4

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("incoming")
        nm.deleteNotificationChannel("ongoing")
        nm.createNotificationChannel(silent(NotificationChannel(CH_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH)))
        nm.createNotificationChannel(silent(NotificationChannel(CH_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_DEFAULT)))
        nm.createNotificationChannel(NotificationChannel(CH_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_RECORDING, "Call recording", NotificationManager.IMPORTANCE_LOW))
    }

    private fun silent(c: NotificationChannel) = c.apply {
        setSound(null, null)
        enableVibration(false)
        setShowBadge(false)
    }

    private fun action(ctx: Context, action: String, request: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, request, Intent(ctx, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun canPost(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun callActivity(ctx: Context, answer: Boolean = false): PendingIntent {
        val i = Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(InCallActivity.EXTRA_ANSWER, answer)
        return PendingIntent.getActivity(ctx, if (answer) 11 else 10, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun incoming(ctx: Context, info: CallInfo) {
        if (!canPost(ctx)) return
        val who = info.name ?: info.number.ifBlank { "Unknown" }
        // The category goes in the headline (the part of a call popup that is always shown), not only the small text.
        val label = info.tag?.let { "${it.name} · $who" } ?: who
        val decline = PendingIntent.getBroadcast(
            ctx, 12, Intent(ctx, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_DECLINE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val person = Person.Builder().setName(label).setImportant(true).build()
        val n = NotificationCompat.Builder(ctx, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(label)
            .setContentText("Incoming call" + (info.tag?.let { " · ${it.name}" } ?: ""))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setFullScreenIntent(callActivity(ctx), true)
            .setContentIntent(callActivity(ctx))
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, callActivity(ctx, answer = true)))
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_INCOMING, n)
    }

    /**
     * The live notification for the call in progress: who, how long, and Speaker / Mute / End call buttons.
     * A plain ongoing notification (not CallStyle), so it needs no foreground service.
     */
    fun ongoing(ctx: Context, info: CallInfo, otherCalls: Int, speakerOn: Boolean, muted: Boolean) {
        if (!canPost(ctx)) return
        val label = info.name ?: info.number.ifBlank { "Unknown" }
        val status = when (info.state) {
            android.telecom.Call.STATE_HOLDING -> "On hold"
            android.telecom.Call.STATE_ACTIVE -> "Ongoing call"
            else -> "Calling…"
        } + if (otherCalls > 0) " · +$otherCalls more" else ""
        val live = info.state == android.telecom.Call.STATE_ACTIVE && info.connectTimeMillis > 0
        val b = NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            // Name alone as the title (names can be long); the category sits in the short chip text
            // and the second line, so it is never pushed out by a long name.
            .setContentTitle(label)
            .setContentText(info.tag?.let { "${it.name} · $status" } ?: status)
            .setLargeIcon(contactPhoto(ctx, info.photoUri))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(callActivity(ctx))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(if (speakerOn) R.drawable.ic_action_speaker_off else R.drawable.ic_action_speaker, if (speakerOn) "Speaker off" else "Speaker", action(ctx, CallActionReceiver.ACTION_SPEAKER, 31))
            .addAction(if (muted) R.drawable.ic_action_mic else R.drawable.ic_action_mic_off, if (muted) "Unmute" else "Mute", action(ctx, CallActionReceiver.ACTION_MUTE, 32))
            .addAction(R.drawable.ic_action_call_end, "End call", action(ctx, CallActionReceiver.ACTION_HANGUP, 33))
        if (live) b.setUsesChronometer(true).setWhen(info.connectTimeMillis).setShowWhen(true) else b.setShowWhen(false)
        // Android 16 "live update": asks the system to show this as a status-bar chip / island with the call timer.
        // Ignored on older Android, and when the user turned live updates off for the app.
        b.addExtras(Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            // Before the call is answered: the category (else the state) in the chip. Once live, no text, so the timer shows.
            val chip = if (live) null else info.tag?.name ?: (if (info.state == android.telecom.Call.STATE_HOLDING) "On hold" else "Calling")
            if (chip != null) putString("android.shortCriticalText", chip)
        })
        NotificationManagerCompat.from(ctx).notify(ID_ONGOING, b.build())
    }

    /** The contact's photo as a round bitmap for the notification's large icon, or null. */
    private fun contactPhoto(ctx: Context, uri: String?): android.graphics.Bitmap? {
        if (uri.isNullOrBlank()) return null
        return try {
            val src = ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
            val d = 128
            val side = minOf(src.width, src.height)
            val square = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = android.graphics.Bitmap.createScaledBitmap(square, d, d, true)
            val out = android.graphics.Bitmap.createBitmap(d, d, android.graphics.Bitmap.Config.ARGB_8888)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val c = android.graphics.Canvas(out)
            c.drawCircle(d / 2f, d / 2f, d / 2f, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            c.drawBitmap(scaled, 0f, 0f, paint)
            out
        } catch (_: Exception) { null }
    }

    /** Whether Android 16 live updates (the chip / island) are allowed for this app, and whether the current call got one. */
    fun liveUpdateStatus(ctx: Context): String {
        if (android.os.Build.VERSION.SDK_INT < 36) return "Needs Android 16 (this phone has Android ${android.os.Build.VERSION.RELEASE})"
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val allowed = try { nm.javaClass.getMethod("canPostPromotedNotifications").invoke(nm) as? Boolean } catch (_: Throwable) { null }
        val promoted = try {
            nm.activeNotifications.any { it.id == ID_ONGOING && (it.notification.flags and FLAG_PROMOTED_ONGOING) != 0 }
        } catch (_: Throwable) { false }
        return when {
            allowed == false -> "Off for this app. Turn it on in Android settings."
            promoted -> "On: the current call is shown as a live update."
            allowed == true -> "Allowed. Shown while a call is on."
            else -> "Unknown on this phone."
        }
    }

    /** Hidden platform flag that Android 16 sets on a notification it has promoted to a live update. */
    private const val FLAG_PROMOTED_ONGOING = 0x00040000

    fun cancelIncoming(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_INCOMING)
    fun cancelOngoing(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_ONGOING)

    fun missed(ctx: Context, number: String, name: String?) {
        if (!canPost(ctx)) return
        val label = name ?: number.ifBlank { "Unknown" }
        val open = PendingIntent.getActivity(
            ctx, 20, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_HISTORY, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(ctx, CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_missed)
            .setContentTitle("Missed call")
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (number.isNotBlank()) {
            val callBack = PendingIntent.getActivity(
                ctx, 21, Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(R.drawable.ic_action_call, "Call back", callBack)
        }
        NotificationManagerCompat.from(ctx).notify(ID_MISSED + (number.hashCode() and 0xFFFF) + 100, b.build())
    }

    fun recording(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CH_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle("Recording call")
            .setOngoing(true)
            .setContentIntent(callActivity(ctx))
            .build()
}

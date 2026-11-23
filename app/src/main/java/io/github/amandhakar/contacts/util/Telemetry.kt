package io.github.amandhakar.contacts.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.amandhakar.contacts.data.Prefs

/**
 * Crash reports (Crashlytics) and anonymous usage events (Analytics).
 *
 * Rules: never put a name, phone number, email, account name or any call content into an event, key, log line or
 * exception message. Only things like screen names, which recording input worked, counts and error types.
 * Every call is wrapped so that a Firebase problem (or a build without google-services.json) can never break calling.
 */
object Telemetry {
    private var app: Context? = null
    private var enabled = true

    fun init(ctx: Context) {
        app = ctx.applicationContext
        enabled = Prefs(ctx).telemetry
        apply()
    }

    fun isEnabled() = enabled

    fun setEnabled(on: Boolean) {
        enabled = on
        app?.let { Prefs(it).telemetry = on }
        apply()
    }

    private fun apply() = safe {
        val c = app ?: return@safe
        FirebaseAnalytics.getInstance(c).setAnalyticsCollectionEnabled(enabled)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    }

    /** `params` values: Boolean, numbers or short text (cut to 100 characters). */
    fun event(name: String, vararg params: Pair<String, Any?>) = safe {
        val c = app ?: return@safe
        if (!enabled) return@safe
        FirebaseAnalytics.getInstance(c).logEvent(name.take(40), bundle(params))
    }

    fun screen(name: String) = event(FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME to name)

    /** A problem we handled but want to know about. Shows in Crashlytics as a non-fatal issue. */
    fun nonFatal(where: String, t: Throwable) = safe {
        if (!enabled) return@safe
        val cr = FirebaseCrashlytics.getInstance()
        cr.log(where)
        cr.recordException(t)
    }

    /** A line that is attached to the next crash report, to show what happened just before. */
    fun breadcrumb(msg: String) = safe { if (enabled) FirebaseCrashlytics.getInstance().log(msg) }

    /** A fact attached to crash reports and usage, e.g. "default_dialer" = true. */
    fun fact(key: String, value: Any) = safe {
        val c = app ?: return@safe
        if (!enabled) return@safe
        FirebaseCrashlytics.getInstance().setCustomKey(key, value.toString().take(100))
        FirebaseAnalytics.getInstance(c).setUserProperty(key.take(24), value.toString().take(36))
    }

    /** Crashes the app on purpose so the Crashlytics setup can be checked. */
    fun testCrash(): Nothing = throw RuntimeException("Test crash from Settings")

    fun bundle(params: Array<out Pair<String, Any?>>): Bundle = Bundle().also { b ->
        for ((k, v) in params) {
            val key = k.take(40)
            when (v) {
                null -> {}
                is Boolean -> b.putString(key, v.toString())
                is Int -> b.putLong(key, v.toLong())
                is Long -> b.putLong(key, v)
                is Double -> b.putDouble(key, v)
                else -> b.putString(key, v.toString().take(100))
            }
        }
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (_: Throwable) {}
    }
}

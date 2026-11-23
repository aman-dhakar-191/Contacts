package io.github.amandhakar.contacts.call

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/** Does nothing. Having it enabled changes what some phones let the app record, so the recording test can try it. */
class AccessibilityHelper : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, AccessibilityHelper::class.java).flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}

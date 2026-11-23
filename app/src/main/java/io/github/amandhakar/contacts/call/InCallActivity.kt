package io.github.amandhakar.contacts.call

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.amandhakar.contacts.ui.ContactsTheme

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        handleIntent(intent)
        setContent { ContactsTheme { CallScreen(onClose = { finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            CallHub.ringing()?.let { CallHub.answer(it.call) }
            Notifications.cancelIncoming(this)
            i.removeExtra(EXTRA_ANSWER)
        }
    }

    companion object {
        const val EXTRA_ANSWER = "answer"
    }
}

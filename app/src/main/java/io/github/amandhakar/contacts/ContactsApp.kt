package io.github.amandhakar.contacts

import android.app.Application
import io.github.amandhakar.contacts.call.Notifications

class ContactsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

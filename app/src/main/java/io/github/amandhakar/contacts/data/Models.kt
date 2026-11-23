package io.github.amandhakar.contacts.data

data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
)

data class PhoneEntry(val number: String, val label: String, val type: Int = 2)

data class AccountInfo(val name: String?, val type: String?, val count: Int = 0) {
    val isGoogle get() = type == "com.google"
    val title get() = if (name == null) "Phone only (not synced)" else name
}

data class GroupInfo(val id: Long, val title: String, val accountName: String?, val accountType: String?)

data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    val phones: List<PhoneEntry>,
    val emails: List<String>,
    val groupIds: Set<Long>,
    val rawContactId: Long?,
    val account: AccountInfo?,
)

class ContactDraft(
    val id: Long?, // null = new contact
    val rawContactId: Long?,
    val name: String,
    val phones: List<Pair<Int, String>>, // phone type, number
    val emails: List<String>,
    val groupIds: Set<Long>,
    val account: AccountInfo?, // only used for new contacts
    val photo: ByteArray?, // new photo (JPEG), null = unchanged
    val removePhoto: Boolean = false,
)

data class CallerInfo(val contactId: Long, val name: String, val photoUri: String?)

data class CallEntry(
    val id: Long,
    val number: String,
    val type: Int,
    val date: Long,
    val durationSec: Long,
    val cachedName: String?,
)

data class RecordingEntry(
    val path: String,
    val date: Long,
    val incoming: Boolean,
    val number: String,
    val bothSides: Boolean,
    val sizeBytes: Long,
)

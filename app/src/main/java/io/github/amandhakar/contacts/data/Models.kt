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
    val title: String get() = name ?: "Phone only (not synced)"

    /** Stable id used to remember choices in settings; "|" is the phone-only account. */
    val key: String get() = keyOf(name, type)

    companion object {
        fun keyOf(name: String?, type: String?) = "${type ?: ""}|${name ?: ""}"

        /** Hidden set that shows only [shownKey] among [allKeys]; null shows everything. */
        fun hiddenFor(shownKey: String?, allKeys: Collection<String>): Set<String> =
            if (shownKey == null) emptySet() else allKeys.toSet() - shownKey

        /** The one account that is shown, or null if all or several are. */
        fun singleShown(hidden: Set<String>, allKeys: Collection<String>): String? {
            if (hidden.isEmpty()) return null
            return allKeys.distinct().filter { it !in hidden }.singleOrNull()
        }

        /** Contact ids that have at least one raw contact in a shown account. rows = (contactId, accountName, accountType). */
        fun visibleContactIds(rows: List<Triple<Long, String?, String?>>, hidden: Set<String>): Set<Long> =
            rows.filter { keyOf(it.second, it.third) !in hidden }.map { it.first }.toSet()
    }
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

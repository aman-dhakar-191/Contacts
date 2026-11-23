package io.github.amandhakar.contacts.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts
import io.github.amandhakar.contacts.util.PhoneUtil
import io.github.amandhakar.contacts.util.VCard
import java.io.OutputStream

/**
 * Uses the phone's own contacts database, so contacts are shared with every other app and
 * the Google account sync keeps them backed up. Nothing is copied into a private database.
 */
class ContactsRepo(private val ctx: Context) {
    private val cr get() = ctx.contentResolver

    fun contacts(): List<ContactSummary> {
        val out = ArrayList<ContactSummary>()
        try {
            cr.query(
                Contacts.CONTENT_URI,
                arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED),
                null, null, "${Contacts.SORT_KEY_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += ContactSummary(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3), c.getInt(4) == 1)
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }

    /** contactId -> phone numbers, for every contact. */
    fun allPhones(): Map<Long, List<PhoneEntry>> {
        val out = HashMap<Long, MutableList<PhoneEntry>>()
        try {
            cr.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.TYPE, Phone.LABEL),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(2)
                    val label = Phone.getTypeLabel(ctx.resources, type, c.getString(3)).toString()
                    out.getOrPut(c.getLong(0)) { ArrayList() } += PhoneEntry(c.getString(1) ?: "", label, type)
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }

    /** group id -> contact ids. */
    fun groupMembers(groupId: Long): Set<Long> {
        val out = HashSet<Long>()
        try {
            cr.query(
                Data.CONTENT_URI, arrayOf(Data.CONTACT_ID),
                "${Data.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?",
                arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()), null,
            )?.use { c -> while (c.moveToNext()) out += c.getLong(0) }
        } catch (_: SecurityException) {
        }
        return out
    }

    fun groups(): List<GroupInfo> {
        val out = ArrayList<GroupInfo>()
        try {
            cr.query(
                Groups.CONTENT_URI,
                arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_NAME, Groups.ACCOUNT_TYPE),
                "${Groups.DELETED}=0 AND ${Groups.GROUP_VISIBLE}=1", null, "${Groups.TITLE} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(1) ?: continue
                    out += GroupInfo(c.getLong(0), title, c.getString(2), c.getString(3))
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }

    fun createGroup(title: String, account: AccountInfo?): Boolean = try {
        val v = ContentValues().apply {
            put(Groups.TITLE, title)
            put(Groups.GROUP_VISIBLE, 1)
            put(Groups.ACCOUNT_NAME, account?.name)
            put(Groups.ACCOUNT_TYPE, account?.type)
        }
        cr.insert(Groups.CONTENT_URI, v) != null
    } catch (_: Exception) {
        false
    }

    fun accounts(): List<AccountInfo> {
        val counts = LinkedHashMap<Pair<String?, String?>, Int>()
        try {
            cr.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                "${RawContacts.DELETED}=0", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val k = c.getString(0) to c.getString(1)
                    counts[k] = (counts[k] ?: 0) + 1
                }
            }
        } catch (_: SecurityException) {
        }
        return counts.map { (k, n) -> AccountInfo(k.first, k.second, n) }
            .sortedWith(compareByDescending<AccountInfo> { it.isGoogle }.thenBy { it.name ?: "" })
    }

    fun detail(id: Long): ContactDetail? {
        var lookup = ""
        var name = ""
        var photo: String? = null
        var starred = false
        var found = false
        cr.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
            arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI, Contacts.STARRED),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                found = true
                lookup = c.getString(0) ?: ""; name = c.getString(1) ?: ""; photo = c.getString(2); starred = c.getInt(3) == 1
            }
        }
        if (!found) return null

        val phones = ArrayList<PhoneEntry>()
        val emails = ArrayList<String>()
        val groups = HashSet<Long>()
        cr.query(
            Data.CONTENT_URI, arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3),
            "${Data.CONTACT_ID}=?", arrayOf(id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val v = c.getString(1) ?: continue
                when (c.getString(0)) {
                    Phone.CONTENT_ITEM_TYPE -> {
                        val type = c.getInt(2)
                        phones += PhoneEntry(v, Phone.getTypeLabel(ctx.resources, type, c.getString(3)).toString(), type)
                    }
                    Email.CONTENT_ITEM_TYPE -> emails += v
                    GroupMembership.CONTENT_ITEM_TYPE -> v.toLongOrNull()?.let { groups += it }
                }
            }
        }
        var raw: Long? = null
        var account: AccountInfo? = null
        cr.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(id.toString()), null,
        )?.use { c ->
            if (c.moveToFirst()) {
                raw = c.getLong(0); account = AccountInfo(c.getString(1), c.getString(2))
            }
        }
        return ContactDetail(id, lookup, name, photo, starred, phones, emails.distinct(), groups, raw, account)
    }

    fun lookup(number: String): CallerInfo? {
        if (number.isBlank()) return null
        return try {
            cr.query(
                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(PhoneLookup._ID, PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) CallerInfo(c.getLong(0), c.getString(1) ?: number, c.getString(2)) else null }
        } catch (_: Exception) {
            null
        }
    }

    fun setStarred(id: Long, starred: Boolean) {
        val v = ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }
        cr.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), v, null, null)
    }

    fun delete(lookupKey: String) {
        cr.delete(Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), null, null)
    }

    fun save(d: ContactDraft): Boolean {
        val ops = ArrayList<ContentProviderOperation>()
        val isNew = d.id == null || d.rawContactId == null
        fun dataInsert(): ContentProviderOperation.Builder {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            return if (isNew) b.withValueBackReference(Data.RAW_CONTACT_ID, 0) else b.withValue(Data.RAW_CONTACT_ID, d.rawContactId)
        }
        if (isNew) {
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, d.account?.type)
                .withValue(RawContacts.ACCOUNT_NAME, d.account?.name)
                .build()
        } else {
            val mimes = mutableListOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, GroupMembership.CONTENT_ITEM_TYPE)
            if (d.photo != null || d.removePhoto) mimes += Photo.CONTENT_ITEM_TYPE
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (${mimes.joinToString(",") { "?" }})",
                    arrayOf(d.rawContactId.toString()) + mimes.toTypedArray(),
                ).build()
        }
        ops += dataInsert().withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, d.name).build()
        for ((type, number) in d.phones.filter { it.second.isNotBlank() }) {
            ops += dataInsert().withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, number.trim()).withValue(Phone.TYPE, type).build()
        }
        for (e in d.emails.filter { it.isNotBlank() }) {
            ops += dataInsert().withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, e.trim()).withValue(Email.TYPE, Email.TYPE_HOME).build()
        }
        for (g in d.groupIds) {
            ops += dataInsert().withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                .withValue(GroupMembership.GROUP_ROW_ID, g).build()
        }
        if (d.photo != null) {
            ops += dataInsert().withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE).withValue(Photo.PHOTO, d.photo).build()
        }
        return try {
            cr.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---------- duplicates ----------

    /** Groups of contacts that share a phone number or the same name. */
    fun duplicates(): List<List<ContactSummary>> {
        val all = contacts()
        val phones = allPhones()
        val parent = HashMap<Long, Long>()
        fun find(x: Long): Long {
            var r = x
            while (parent[r] != null && parent[r] != r) r = parent[r]!!
            parent[x] = r
            return r
        }
        fun union(a: Long, b: Long) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        all.forEach { parent[it.id] = it.id }
        val byKey = HashMap<String, Long>()
        for (c in all) {
            val keys = ArrayList<String>()
            val n = c.name.trim().lowercase()
            if (n.isNotEmpty() && n.any { it.isLetter() }) keys += "n:$n"
            phones[c.id]?.forEach { p -> PhoneUtil.key(p.number).takeIf { it.length >= 7 }?.let { keys += "p:$it" } }
            for (k in keys) {
                val seen = byKey[k]
                if (seen == null) byKey[k] = c.id else union(c.id, seen)
            }
        }
        return all.groupBy { find(it.id) }.values.filter { it.size > 1 }
    }

    fun merge(contactIds: List<Long>): Boolean {
        val raws = ArrayList<Long>()
        for (id in contactIds) {
            cr.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
                "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(id.toString()), null,
            )?.use { c -> while (c.moveToNext()) raws += c.getLong(0) }
        }
        if (raws.size < 2) return false
        return try {
            for (other in raws.drop(1)) {
                val v = ContentValues().apply {
                    put(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                    put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, raws[0])
                    put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, other)
                }
                cr.update(ContactsContract.AggregationExceptions.CONTENT_URI, v, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---------- vCard ----------

    fun exportVcf(lookupKeys: List<String>, out: OutputStream): Boolean {
        if (lookupKeys.isEmpty()) return false
        return try {
            val uri = Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(lookupKeys.joinToString(":")))
            cr.openInputStream(uri)?.use { it.copyTo(out) } != null
        } catch (_: Exception) {
            false
        }
    }

    /** Returns how many contacts were added. */
    fun importVcf(text: String, account: AccountInfo?): Int {
        var n = 0
        for (c in VCard.parse(text)) {
            val ok = save(
                ContactDraft(
                    id = null, rawContactId = null, name = c.name,
                    phones = c.phones.map { Phone.TYPE_MOBILE to it }, emails = c.emails,
                    groupIds = emptySet(), account = account, photo = null,
                ),
            )
            if (ok) n++
        }
        return n
    }
}

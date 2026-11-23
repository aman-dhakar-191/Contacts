package io.github.amandhakar.contacts

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amandhakar.contacts.data.AccountInfo
import io.github.amandhakar.contacts.data.BlockedRepo
import io.github.amandhakar.contacts.data.CallEntry
import io.github.amandhakar.contacts.data.CallLogRepo
import io.github.amandhakar.contacts.data.ContactDraft
import io.github.amandhakar.contacts.data.ContactSummary
import io.github.amandhakar.contacts.data.ContactsRepo
import io.github.amandhakar.contacts.data.GroupInfo
import io.github.amandhakar.contacts.data.PhoneEntry
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.data.RecordingsRepo
import io.github.amandhakar.contacts.util.PhoneUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data class Detail(val id: Long) : Screen
    data class Edit(val id: Long?, val number: String? = null) : Screen
    data object Settings : Screen
    data object Blocked : Screen
    data object Recordings : Screen
    data object Merge : Screen
    data object RecordTest : Screen
    data object RecordList : Screen
    data object Accounts : Screen
}

val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.WRITE_CALL_LOG,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ANSWER_PHONE_CALLS,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.POST_NOTIFICATIONS,
)

const val DISPLAY_ALL = "all"
const val DISPLAY_CUSTOM = "custom"

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    val contactsRepo = ContactsRepo(app)
    val callLogRepo = CallLogRepo(app)
    val blockedRepo = BlockedRepo(app)
    val recordingsRepo = RecordingsRepo(app)
    val prefs = Prefs(app)

    private val _contacts = MutableStateFlow<List<ContactSummary>>(emptyList())
    val contacts: StateFlow<List<ContactSummary>> = _contacts
    private val _phones = MutableStateFlow<Map<Long, List<PhoneEntry>>>(emptyMap())
    val phones: StateFlow<Map<Long, List<PhoneEntry>>> = _phones
    private val _history = MutableStateFlow<List<CallEntry>>(emptyList())
    val history: StateFlow<List<CallEntry>> = _history
    private val _groups = MutableStateFlow<List<GroupInfo>>(emptyList())
    val groups: StateFlow<List<GroupInfo>> = _groups
    private val _accounts = MutableStateFlow<List<AccountInfo>>(emptyList())
    val accounts: StateFlow<List<AccountInfo>> = _accounts
    private val _numberIndex = MutableStateFlow<Map<String, ContactSummary>>(emptyMap())
    /** last-10-digits of a number -> the contact that owns it. */
    val numberIndex: StateFlow<Map<String, ContactSummary>> = _numberIndex
    private val _groupMembers = MutableStateFlow<Set<Long>>(emptySet())
    val groupMembers: StateFlow<Set<Long>> = _groupMembers

    private val _totalPeople = MutableStateFlow(0)
    /** Every contact on the phone, ignoring which accounts are shown. */
    val totalPeople: StateFlow<Int> = _totalPeople

    val stack = mutableStateListOf<Screen>()
    var tab by mutableStateOf(2)
    var dialPrefill by mutableStateOf<String?>(null)
    var selectedGroup by mutableStateOf<String?>(null) // label title; same-named labels from several accounts are one filter
    var permissionsOk by mutableStateOf(false)
    var skipPermissions by mutableStateOf(false)
    var isDefaultDialer by mutableStateOf(false)
    var speedDialVersion by mutableStateOf(0)

    private var observing = false
    private val handler = Handler(Looper.getMainLooper())
    private val reloadRunnable = Runnable { reload() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(reloadRunnable)
            handler.postDelayed(reloadRunnable, 400)
        }
    }

    fun push(s: Screen) { stack += s }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

    /** Called on start and after the permission dialog. */
    fun onPermissions() {
        permissionsOk = granted(Manifest.permission.READ_CONTACTS) && granted(Manifest.permission.READ_CALL_LOG) &&
            granted(Manifest.permission.CALL_PHONE)
        refreshRole()
        if (granted(Manifest.permission.READ_CONTACTS)) {
            if (!observing) {
                observing = true
                try {
                    app.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
                    if (granted(Manifest.permission.READ_CALL_LOG)) {
                        app.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
                    }
                } catch (_: SecurityException) {
                    observing = false
                }
            }
            reload()
        }
    }

    fun refreshRole() {
        isDefaultDialer = app.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val cs = contactsRepo.contacts()
            val ph = contactsRepo.allPhones()
            val byId = cs.associateBy { it.id }
            val idx = HashMap<String, ContactSummary>()
            for ((id, list) in ph) {
                val c = byId[id] ?: continue
                for (p in list) PhoneUtil.key(p.number).takeIf { it.isNotEmpty() }?.let { idx.putIfAbsent(it, c) }
            }
            _contacts.value = cs
            _phones.value = ph
            _numberIndex.value = idx
            _groups.value = contactsRepo.groups()
            _accounts.value = contactsRepo.accounts()
            _history.value = callLogRepo.load()
            _totalPeople.value = contactsRepo.countAll()
            refreshMembers()
        }
    }

    fun selectGroup(title: String?) {
        selectedGroup = title
        refreshMembers()
    }

    private fun refreshMembers() {
        val t = selectedGroup
        if (t == null) { _groupMembers.value = emptySet(); return }
        viewModelScope.launch(Dispatchers.IO) {
            _groupMembers.value = _groups.value.filter { it.title == t }.flatMap { contactsRepo.groupMembers(it.id) }.toSet()
        }
    }

    fun setStarred(id: Long, starred: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { contactsRepo.setStarred(id, starred); reload() }
    }

    fun saveContact(draft: ContactDraft, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { contactsRepo.save(draft) }
            reload(); done(ok)
        }
    }

    fun deleteContact(lookupKey: String) {
        viewModelScope.launch(Dispatchers.IO) { contactsRepo.delete(lookupKey); reload() }
    }

    fun deleteCall(id: Long) { viewModelScope.launch(Dispatchers.IO) { callLogRepo.delete(id); reload() } }
    fun clearHistory() { viewModelScope.launch(Dispatchers.IO) { callLogRepo.clearAll(); reload() } }
    fun markMissedRead() { viewModelScope.launch(Dispatchers.IO) { callLogRepo.markMissedRead() } }

    /** Accounts offered in the pickers: Google accounts, and Phone (local). */
    fun choiceAccounts(): List<AccountInfo> {
        val accts = _accounts.value
        val local = accts.firstOrNull { it.name == null } ?: AccountInfo(null, null, 0)
        return accts.filter { it.isGoogle } + local
    }

    private fun allKeys() = (_accounts.value.map { it.key } + AccountInfo.keyOf(null, null)).distinct()

    /** [DISPLAY_ALL], the key of the single account shown, or [DISPLAY_CUSTOM] for any other mix. */
    fun displayChoice(): String {
        val hidden = prefs.hiddenAccounts
        if (hidden.isEmpty()) return DISPLAY_ALL
        return AccountInfo.singleShown(hidden, allKeys()) ?: DISPLAY_CUSTOM
    }

    /** Shows only this account's contacts, or everything when null. */
    fun showOnly(a: AccountInfo?) {
        prefs.hiddenAccounts = AccountInfo.hiddenFor(a?.key, allKeys())
        reload()
    }

    fun displaySummary(): String {
        val c = displayChoice()
        return when (c) {
            DISPLAY_ALL -> "All contacts"
            DISPLAY_CUSTOM -> "Custom"
            else -> choiceAccounts().firstOrNull { it.key == c }?.let { it.name ?: "Phone" } ?: "Custom"
        }
    }

    /** Where new contacts are saved: the chosen account, else the first Google account, else phone only. */
    fun defaultAccount(): AccountInfo? {
        val key = prefs.defaultAccount
        val accts = _accounts.value
        if (key == AccountInfo.keyOf(null, null)) return AccountInfo(null, null)
        return accts.firstOrNull { it.key == key && it.isGoogle } ?: accts.firstOrNull { it.isGoogle }
    }

    fun setDefaultAccount(a: AccountInfo) { prefs.defaultAccount = a.key; speedDialVersion++ }

    fun speedDial(slot: Int) = prefs.speedDial(slot)
    fun setSpeedDial(slot: Int, name: String, number: String) { prefs.setSpeedDial(slot, name, number); speedDialVersion++ }

    fun nameFor(number: String, cached: String?): String =
        _numberIndex.value[PhoneUtil.key(number)]?.name ?: cached?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unknown" }
}

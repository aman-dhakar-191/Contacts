package io.github.amandhakar.contacts

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.github.amandhakar.contacts.ui.AppRoot
import io.github.amandhakar.contacts.ui.ContactsTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermissions() }
    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshRole() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            ContactsTheme {
                AppRoot(
                    vm,
                    requestPermissions = { permLauncher.launch(permissionsToRequest(vm.bluetoothNames)) },
                    requestDefaultDialer = {
                        val rm = getSystemService(RoleManager::class.java)
                        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.getBooleanExtra(EXTRA_OPEN_HISTORY, false)) {
            vm.stack.clear(); vm.tab = 1; vm.markMissedRead()
        }
        val tel = i.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        if (tel != null) { vm.stack.clear(); vm.dialPrefill = tel; vm.tab = 3 }
        else if (i.action == Intent.ACTION_DIAL) { vm.stack.clear(); vm.tab = 3 }
    }

    companion object {
        const val EXTRA_OPEN_HISTORY = "open_history"
    }
}

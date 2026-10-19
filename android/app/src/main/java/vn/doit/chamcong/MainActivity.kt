package vn.doit.chamcong

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import vn.doit.chamcong.data.PunchType
import vn.doit.chamcong.ui.ConsentScreen
import vn.doit.chamcong.ui.PunchScreen
import vn.doit.chamcong.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: PunchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }

    private fun hasFineLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @androidx.compose.runtime.Composable
    private fun App() {
        var showSettings by remember { mutableStateOf(false) }
        var pendingType by remember { mutableStateOf<PunchType?>(null) }

        // Xin quyền vị trí đúng lúc bấm chấm công lần đầu, không xin khi vừa mở app
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
            pendingType?.let { vm.punch(it, fine) }
            pendingType = null
        }

        when {
            !vm.consentGiven -> ConsentScreen(onAccept = vm::giveConsent)
            showSettings -> SettingsScreen(
                serverUrl = vm.settings.serverUrl,
                token = vm.settings.token,
                deviceId = vm.settings.deviceId,
                onSave = vm::saveServer,
                onWithdrawConsent = { vm.withdrawConsent(); showSettings = false },
                onBack = { showSettings = false },
            )
            else -> PunchScreen(
                demoMode = vm.settings.demoMode,
                busy = vm.busy,
                result = vm.lastResult,
                error = vm.error,
                onPunch = { type ->
                    if (hasFineLocation()) {
                        vm.punch(type, true)
                    } else {
                        pendingType = type
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                onOpenSettings = { showSettings = true },
            )
        }
    }
}

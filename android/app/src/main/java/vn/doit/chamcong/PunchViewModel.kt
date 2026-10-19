package vn.doit.chamcong

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import vn.doit.chamcong.data.AppSettings
import vn.doit.chamcong.data.DemoPunchApi
import vn.doit.chamcong.data.HttpPunchApi
import vn.doit.chamcong.data.LocationReader
import vn.doit.chamcong.data.PunchPayload
import vn.doit.chamcong.data.PunchResult
import vn.doit.chamcong.data.PunchType
import vn.doit.chamcong.data.WifiReader
import java.time.Instant

class PunchViewModel(app: Application) : AndroidViewModel(app) {

    val settings = AppSettings(app)
    private val location = LocationReader(app)
    private val wifi = WifiReader(app)

    var consentGiven by mutableStateOf(settings.consentVersion >= AppSettings.CURRENT_CONSENT_VERSION)
        private set
    var busy by mutableStateOf(false)
        private set
    var lastResult by mutableStateOf<PunchResult?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun giveConsent() {
        settings.consentVersion = AppSettings.CURRENT_CONSENT_VERSION
        consentGiven = true
    }

    fun withdrawConsent() {
        settings.consentVersion = 0
        consentGiven = false
        lastResult = null
    }

    fun saveServer(url: String, token: String) {
        settings.serverUrl = url
        settings.token = token
    }

    /** Gọi sau khi đã có quyền vị trí (hoặc người dùng từ chối – khi đó vẫn thử bằng WiFi/IP). */
    fun punch(type: PunchType, hasLocationPermission: Boolean) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val loc = if (hasLocationPermission) location.current() else null
                val bssid = if (hasLocationPermission) wifi.currentBssid() else null
                val payload = PunchPayload(
                    type = type,
                    clientTimeIso = Instant.now().toString(),
                    deviceId = settings.deviceId,
                    location = loc,
                    wifiBssid = bssid,
                )
                val api = if (settings.demoMode) DemoPunchApi() else HttpPunchApi(settings.serverUrl, settings.token)
                lastResult = api.punch(payload)
            } catch (e: Exception) {
                error = e.message ?: "Không kết nối được server"
            } finally {
                busy = false
            }
        }
    }
}

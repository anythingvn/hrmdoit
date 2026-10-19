package vn.doit.chamcong.data

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Đọc BSSID (địa chỉ MAC điểm phát) của WiFi đang kết nối.
 * Android chỉ trả BSSID thật khi app có quyền vị trí chính xác và định vị đang bật; nếu không sẽ trả giá trị
 * "02:00:00:00:00:00" – coi như không có.
 */
class WifiReader(context: Context) {

    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun currentBssid(): String? {
        @Suppress("DEPRECATION")
        val bssid = wifi.connectionInfo?.bssid ?: return null
        return bssid.lowercase().takeUnless { it == REDACTED || it.isBlank() }
    }

    private companion object {
        const val REDACTED = "02:00:00:00:00:00"
    }
}

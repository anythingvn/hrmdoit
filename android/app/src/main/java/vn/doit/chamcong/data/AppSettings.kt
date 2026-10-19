package vn.doit.chamcong.data

import android.content.Context
import java.util.UUID

/**
 * Cài đặt lưu trên máy: mã thiết bị, đồng ý thu thập vị trí, địa chỉ server, token đăng nhập.
 * Mã thiết bị là UUID tự sinh lần đầu – không dùng IMEI hay số điện thoại.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("doit_cham_cong", Context.MODE_PRIVATE)

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }

    var consentVersion: Int
        get() = prefs.getInt(KEY_CONSENT, 0)
        set(v) = prefs.edit().putInt(KEY_CONSENT, v).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SERVER, v.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TOKEN, v.trim()).apply()

    /** Chưa nhập server → chế độ thử: đọc vị trí thật nhưng không gửi đi đâu. */
    val demoMode: Boolean get() = serverUrl.isBlank()

    companion object {
        /** Tăng số này khi nội dung thông báo thu thập vị trí thay đổi → app hỏi lại sự đồng ý. */
        const val CURRENT_CONSENT_VERSION = 1
        private const val KEY_DEVICE = "device_id"
        private const val KEY_CONSENT = "consent_version"
        private const val KEY_SERVER = "server_url"
        private const val KEY_TOKEN = "token"
    }
}

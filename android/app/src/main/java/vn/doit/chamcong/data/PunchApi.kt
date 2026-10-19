package vn.doit.chamcong.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

interface PunchApi {
    suspend fun punch(payload: PunchPayload): PunchResult
}

/** Gọi POST {server}/api/attendance/punches (README mục 6). */
class HttpPunchApi(private val baseUrl: String, private val token: String) : PunchApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun punch(payload: PunchPayload): PunchResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/attendance/punches")
            .header("Authorization", "Bearer $token")
            .post(toJson(payload).toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException(if (resp.code == 401) "Phiên đăng nhập hết hạn, hãy đăng nhập lại" else "Server lỗi ${resp.code}")
            }
            parse(JSONObject(body))
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun toJson(p: PunchPayload): JSONObject = JSONObject().apply {
            put("type", p.type.name)
            put("clientTime", p.clientTimeIso)
            put("deviceId", p.deviceId)
            p.location?.let {
                put("latitude", it.latitude)
                put("longitude", it.longitude)
                put("accuracy", it.accuracyMeters.toDouble())
                put("mock", it.isMock)
            }
            p.wifiBssid?.let { put("wifiBssid", it) }
        }

        fun parse(o: JSONObject) = PunchResult(
            status = o.getString("status"),
            method = o.optString("method", "NONE"),
            site = o.optString("site").takeIf { it.isNotBlank() },
            message = o.optString("message"),
            serverTime = o.optString("serverTime").takeIf { it.isNotBlank() },
        )
    }
}

/**
 * Chế độ thử khi chưa có server: không gửi gì đi, chỉ hiện lại những gì app đọc được để kiểm tra
 * quyền vị trí, độ chính xác GPS, WiFi và cảnh báo vị trí giả lập.
 */
class DemoPunchApi : PunchApi {
    override suspend fun punch(payload: PunchPayload): PunchResult {
        val loc = payload.location
        val details = buildList {
            add(if (loc == null) "không lấy được vị trí" else "vị trí %.5f, %.5f (sai số %.0f m)".format(loc.latitude, loc.longitude, loc.accuracyMeters))
            if (loc?.isMock == true) add("CẢNH BÁO: vị trí giả lập – server thật sẽ từ chối")
            add(if (payload.wifiBssid == null) "không đọc được WiFi" else "WiFi ${payload.wifiBssid}")
        }
        return PunchResult(
            status = "DEMO",
            method = "NONE",
            site = null,
            message = "Chế độ thử – chưa gửi lên server: " + details.joinToString("; "),
            serverTime = null,
        )
    }
}

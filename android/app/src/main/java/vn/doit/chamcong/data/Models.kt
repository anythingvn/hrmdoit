package vn.doit.chamcong.data

/** Loại chấm công – khớp PunchRequest.Type phía server. */
enum class PunchType { IN, OUT }

/** Vị trí đọc được lúc bấm chấm công. */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val isMock: Boolean,
)

/** Dữ liệu gửi lên server. Mã nhân viên KHÔNG nằm ở đây – server lấy từ token đăng nhập. */
data class PunchPayload(
    val type: PunchType,
    val clientTimeIso: String,
    val deviceId: String,
    val location: LocationSnapshot?,
    val wifiBssid: String?,
)

/** Kết quả server trả về – khớp PunchVerdict. */
data class PunchResult(
    val status: String,      // ACCEPTED | NEEDS_REVIEW | REJECTED
    val method: String,      // WIFI | IP | GPS | REMOTE | NONE
    val site: String?,
    val message: String,
    val serverTime: String?,
) {
    val accepted: Boolean get() = status == "ACCEPTED"
}

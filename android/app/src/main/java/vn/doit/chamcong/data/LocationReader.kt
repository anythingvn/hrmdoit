package vn.doit.chamcong.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lấy vị trí MỘT LẦN lúc người dùng bấm chấm công. Không đăng ký cập nhật liên tục, không chạy nền.
 */
class LocationReader(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Trả null nếu không lấy được trong [timeoutMs] (tắt định vị, trong nhà kín...). Gọi khi đã có quyền vị trí. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 15_000): LocationSnapshot? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)          // không dùng vị trí cũ trong bộ nhớ đệm
            .setDurationMillis(timeoutMs)
            .build()
        val cts = CancellationTokenSource()
        val location: Location? = withTimeoutOrNull(timeoutMs + 2_000) {
            client.getCurrentLocation(request, cts.token).await()
        }
        if (location == null) {
            cts.cancel()
            return null
        }
        return LocationSnapshot(location.latitude, location.longitude, location.accuracy, isMock(location))
    }

    private fun isMock(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
}

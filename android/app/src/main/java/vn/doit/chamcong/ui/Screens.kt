package vn.doit.chamcong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.doit.chamcong.data.PunchResult
import vn.doit.chamcong.data.PunchType

/**
 * Thông báo thu thập vị trí – hiện lần đầu mở app. Nội dung cần bộ phận pháp lý/HR duyệt trước khi phát hành;
 * khi sửa nội dung thì tăng AppSettings.CURRENT_CONSENT_VERSION để app hỏi lại.
 */
@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Thông báo về dữ liệu vị trí", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ứng dụng chấm công của Công ty TNHH Công nghệ và Truyền thông DO IT cần biết bạn đang ở đâu " +
                "để xác nhận bạn có mặt tại nơi làm việc."
        )
        Bullet("Vị trí và WiFi chỉ được đọc đúng lúc bạn bấm \"Chấm vào\" hoặc \"Chấm ra\". Ứng dụng không theo dõi vị trí khi chạy nền.")
        Bullet("Dữ liệu gửi đi gồm: tọa độ, độ chính xác GPS, WiFi đang kết nối, mã thiết bị do ứng dụng tự tạo và giờ bấm.")
        Bullet("Dữ liệu chỉ dùng để tính công. Người được xem: bạn, quản lý trực tiếp và bộ phận Nhân sự.")
        Bullet("Tọa độ gốc được xóa sau thời hạn lưu theo quy định của Công ty; bảng công chỉ giữ kết quả chấm công.")
        Bullet("Bạn có thể rút lại đồng ý trong phần Cài đặt và liên hệ bộ phận Nhân sự để chấm công theo cách khác.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("Tôi đã đọc và đồng ý") }
    }
}

@Composable
private fun Bullet(text: String) {
    Row { Text("•  "); Text(text) }
}

@Composable
fun PunchScreen(
    demoMode: Boolean,
    busy: Boolean,
    result: PunchResult?,
    error: String?,
    onPunch: (PunchType) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("DOIT Chấm công", style = MaterialTheme.typography.headlineSmall)
        if (demoMode) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5))) {
                Text(
                    "Chế độ thử: chưa cấu hình server. Ứng dụng đọc vị trí thật nhưng không gửi đi đâu.",
                    Modifier.padding(12.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onPunch(PunchType.IN) }, enabled = !busy, modifier = Modifier.weight(1f).height(64.dp)) {
                Text("Chấm vào")
            }
            Button(onClick = { onPunch(PunchType.OUT) }, enabled = !busy, modifier = Modifier.weight(1f).height(64.dp)) {
                Text("Chấm ra")
            }
        }
        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.height(24.dp))
                Text("Đang lấy vị trí…")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { ResultCard(it) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings) { Text("Cài đặt") }
    }
}

@Composable
private fun ResultCard(r: PunchResult) {
    val (bg, title) = when (r.status) {
        "ACCEPTED" -> Color(0xFFE6F4EA) to "Thành công"
        "NEEDS_REVIEW" -> Color(0xFFFFF4E5) to "Chờ quản lý duyệt"
        "REJECTED" -> Color(0xFFFDECEA) to "Không chấm công được"
        else -> Color(0xFFEFEFEF) to "Kết quả thử"
    }
    Card(colors = CardDefaults.cardColors(containerColor = bg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(r.message)
            r.serverTime?.let { Text("Giờ ghi nhận: $it", style = MaterialTheme.typography.bodySmall) }
            if (r.status == "REJECTED" || r.status == "NEEDS_REVIEW") {
                Text(
                    "Nếu bạn thực sự đang ở nơi làm việc, hãy gửi giải trình cho quản lý trên hệ thống HRM.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    serverUrl: String,
    token: String,
    deviceId: String,
    onSave: (String, String) -> Unit,
    onWithdrawConsent: () -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf(serverUrl) }
    var tok by remember { mutableStateOf(token) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cài đặt", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(url, { url = it }, label = { Text("Địa chỉ server (để trống = chế độ thử)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(tok, { tok = it }, label = { Text("Token đăng nhập") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Mã thiết bị: $deviceId", style = MaterialTheme.typography.bodySmall)
        Text("Gửi mã này cho HR để gắn điện thoại với tài khoản của bạn.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onSave(url, tok); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Lưu") }
        OutlinedButton(onClick = onWithdrawConsent, modifier = Modifier.fillMaxWidth()) { Text("Rút lại đồng ý thu thập vị trí") }
        TextButton(onClick = onBack) { Text("Quay lại") }
    }
}

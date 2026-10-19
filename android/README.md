# DOIT Chấm công – app Android

App chấm công bằng GPS/WiFi, gọi vào phần kiểm tra chấm công phía server (README gốc, mục 6).

> **Trạng thái:** chưa build lần nào. Môi trường phát triển không có Android SDK và không tải được thư viện từ Google Maven. Mở bằng Android Studio để build; nếu lỗi, gửi lại log.

## Mở và chạy

1. Android Studio → **Open** → chọn thư mục `android/` (không phải thư mục gốc của repo).
2. Android Studio tự tải Gradle 8.9 và thư viện. Nếu được hỏi nâng cấp phiên bản Android Gradle Plugin, có thể đồng ý.
3. Chạy trên điện thoại thật. Máy ảo không có GPS và WiFi thật, chỉ dùng được để xem giao diện.

## App làm gì

- **Lần đầu mở:** hiện thông báo về dữ liệu vị trí, phải bấm "Tôi đã đọc và đồng ý" mới dùng được. Có thể rút lại đồng ý trong Cài đặt.
- **Bấm Chấm vào / Chấm ra:**
  - lần đầu, app mới xin quyền vị trí;
  - lấy vị trí **một lần** (độ chính xác cao, không dùng vị trí cũ, chờ tối đa 15 giây);
  - đọc BSSID WiFi đang kết nối và cờ vị trí giả lập;
  - gửi lên server.
- **Không xin quyền vị trí nền** (`ACCESS_BACKGROUND_LOCATION`) và không chạy dịch vụ nền.
- **Hiện kết quả theo màu:** thành công / chờ duyệt / bị từ chối, kèm lời nhắc gửi giải trình.
- **Mã thiết bị** là UUID app tự sinh, không dùng IMEI. Nhân viên gửi mã này cho HR để gắn điện thoại với tài khoản.

## Chế độ thử

Khi chưa nhập địa chỉ server trong Cài đặt, app vẫn đọc vị trí và WiFi thật nhưng **không gửi đi đâu**, chỉ hiện lại kết quả: tọa độ, sai số, BSSID, cảnh báo vị trí giả lập. Dùng chế độ này để:
- kiểm tra quyền vị trí và độ chính xác GPS tại văn phòng;
- **lấy BSSID các điểm phát WiFi văn phòng** để khai báo điểm chấm công;
- thử ứng dụng giả lập vị trí xem có bị phát hiện không.

## Kết nối server

App gọi `POST {server}/api/attendance/punches` với header `Authorization: Bearer <token>` (định dạng JSON xem README gốc, mục 6). Server chưa có endpoint này: cần dựng khi làm ứng dụng JHipster chính. Endpoint đó sẽ lấy mã nhân viên từ token, đọc IP từ request, gọi `PunchValidator` rồi lưu kết quả.

Token hiện nhập tay trong Cài đặt cho mục đích thử. Bản chính thức nên có màn hình đăng nhập (OAuth2/JWT của JHipster) và lưu token trong `EncryptedSharedPreferences`.

## Phiên bản

| Thành phần | Phiên bản |
|---|---|
| Gradle / Android Gradle Plugin | 8.9 / 8.7.3 |
| Kotlin + Compose compiler | 2.0.21 |
| Compose BOM | 2024.12.01 |
| play-services-location | 21.3.0 |
| OkHttp | 4.12.0 |
| minSdk / targetSdk | 26 (Android 8.0) / 35 |

Các phiên bản trên được chọn cho tương thích với nhau, không phải bản mới nhất. Android Studio có thể gợi ý nâng cấp.

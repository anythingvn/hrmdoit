package vn.doit.hr.attendance;

import java.time.Instant;

/**
 * Một lần bấm chấm công gửi từ app.
 *
 * @param deviceId mã thiết bị app tự sinh lần đầu cài (không dùng IMEI)
 * @param clientTime giờ trên điện thoại – chỉ để phát hiện lệch giờ; giờ ghi nhận luôn là giờ server
 * @param location tọa độ lúc bấm; null nếu không lấy được
 * @param accuracyMeters sai số GPS do hệ điều hành báo (bán kính 68%)
 * @param mockLocation hệ điều hành báo vị trí đến từ ứng dụng giả lập
 * @param wifiBssid BSSID WiFi đang kết nối (nếu có)
 * @param clientIp IP public server nhìn thấy (server điền, không lấy từ app)
 */
public record PunchRequest(
        String employeeCode,
        String deviceId,
        Type type,
        Instant clientTime,
        GeoPoint location,
        Double accuracyMeters,
        boolean mockLocation,
        String wifiBssid,
        String clientIp) {

    public enum Type { IN, OUT }
}

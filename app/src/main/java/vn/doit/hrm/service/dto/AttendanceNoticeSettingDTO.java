package vn.doit.hrm.service.dto;

/**
 * Cài đặt tự chấm công: nội dung thông báo thu thập vị trí, phiên bản nội dung, số ngày giữ tọa độ/IP. Khi gửi lên ({@code PUT}),
 * {@code noticeVersion} bị bỏ qua: hệ thống tự tăng khi nội dung đổi.
 */
public record AttendanceNoticeSettingDTO(String noticeText, Integer noticeVersion, Integer locationRetentionDays) {}

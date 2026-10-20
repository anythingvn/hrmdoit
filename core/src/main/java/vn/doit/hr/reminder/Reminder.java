package vn.doit.hr.reminder;

import java.time.LocalDate;
import java.util.Set;
import vn.doit.hr.reminder.ReminderPolicy.Recipient;

/**
 * Một lời nhắc cần gửi hôm nay.
 *
 * @param eventKey khóa của sự kiện (loại + mã NV + ngày sự kiện); đổi ngày (vd gia hạn HĐ) là sự kiện mới
 * @param step mốc đã kích hoạt: số ngày nhắc trước (≥ 0) với sự kiện sắp tới, hoặc −(số ngày quá hạn) với nhắc quá hạn.
 *             Lưu cặp (eventKey, step) vào nhật ký để không gửi lặp.
 * @param daysLeft số ngày còn lại tới sự kiện (âm nếu đã qua)
 */
public record Reminder(
        ReminderType type,
        String employeeCode,
        String employeeName,
        LocalDate eventDate,
        long daysLeft,
        boolean urgent,
        Set<Recipient> recipients,
        String message,
        String eventKey,
        int step) {
}

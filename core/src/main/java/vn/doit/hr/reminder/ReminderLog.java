package vn.doit.hr.reminder;

import java.util.OptionalInt;

/**
 * Nhật ký các lời nhắc đã gửi (bảng {@code reminder_log(event_key, step, sent_at)}).
 *
 * <p>Với sự kiện sắp tới, chỉ cần biết <b>mốc nhỏ nhất đã gửi</b>: mốc lớn hơn coi như đã qua.
 * Nhờ vậy nếu server tắt vài ngày, lần chạy kế tiếp chỉ gửi <b>một</b> lời nhắc gấp nhất thay vì dồn cả loạt.
 */
public interface ReminderLog {

    /** Mốc nhắc trước (step ≥ 0) nhỏ nhất đã gửi cho sự kiện. */
    OptionalInt smallestLeadSent(String eventKey);

    /** Đã gửi nhắc quá hạn cho ngày quá hạn thứ {@code overdueDay} chưa. */
    boolean overdueSent(String eventKey, int overdueDay);
}

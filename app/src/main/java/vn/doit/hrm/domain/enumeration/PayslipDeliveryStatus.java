package vn.doit.hrm.domain.enumeration;

/** Trạng thái một lượt gửi phiếu lương: đang chờ gửi ở luồng nền, đã gửi, lỗi (có lý do). */
public enum PayslipDeliveryStatus {
    PENDING,
    SENT,
    FAILED,
}

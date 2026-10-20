package vn.doit.hrm.domain.enumeration;

/** Trạng thái một lần cho nghỉ việc. */
public enum TerminationRecordStatus {
    /** Đã hẹn ngày nghỉ (ngày làm việc cuối từ hôm nay trở đi), chưa áp dụng. */
    SCHEDULED,
    /** Đã áp dụng: nhân viên đã nghỉ, tài khoản bị khóa. */
    APPLIED,
    /** Lịch hẹn đã bị hủy trước khi áp dụng. */
    CANCELLED,
    /** Đã hoàn tác sau khi áp dụng. */
    UNDONE,
}

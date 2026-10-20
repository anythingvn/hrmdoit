package vn.doit.hr.attendance;

/** Thao tác chấm công / duyệt bị từ chối; thông báo hiển thị thẳng cho người dùng. */
public class AttendanceRuleException extends RuntimeException {

    public AttendanceRuleException(String message) {
        super(message);
    }
}

package vn.doit.hr.assignment;

/** Ghi lịch sử bị từ chối vì dữ liệu không hợp lệ hoặc chồng lấn; thông báo hiển thị thẳng cho HR. */
public class AssignmentConflictException extends RuntimeException {

    public AssignmentConflictException(String message) {
        super(message);
    }
}

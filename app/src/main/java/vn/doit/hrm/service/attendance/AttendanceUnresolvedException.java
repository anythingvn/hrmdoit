package vn.doit.hrm.service.attendance;

import java.time.LocalDate;
import java.util.List;
import vn.doit.hrm.service.EmployeeRuleException;

/**
 * Chốt bảng công khi còn ô đỏ (thiếu giờ, chưa nhập) mà không gửi {@code force=true}: 400 {@code attendanceunresolved}. Câu báo lỗi liệt
 * kê các ô (như {@code deleteblocked}); danh sách có cấu trúc đi kèm thuộc tính {@code unresolved} để giao diện hiện lại.
 */
public class AttendanceUnresolvedException extends EmployeeRuleException {

    /** Ô đỏ của một người: các ngày thiếu giờ hoặc chưa nhập. */
    public record Unresolved(Long employeeId, String code, String name, List<LocalDate> dates) {}

    private final transient List<Unresolved> unresolved;

    public AttendanceUnresolvedException(String message, List<Unresolved> unresolved) {
        super(message, "attendanceunresolved");
        this.unresolved = List.copyOf(unresolved);
    }

    public List<Unresolved> getUnresolved() {
        return unresolved;
    }
}

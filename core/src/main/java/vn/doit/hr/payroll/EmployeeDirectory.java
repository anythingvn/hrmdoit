package vn.doit.hr.payroll;

import java.util.Optional;

/**
 * Danh bạ nhân viên trong hệ thống (OrangeHRM hoặc bảng employee của bạn).
 * Dùng để kiểm tra mã nhân viên trong file Excel có tồn tại không.
 */
@FunctionalInterface
public interface EmployeeDirectory {

    /** Trả về họ tên đang lưu trong hệ thống nếu mã tồn tại. */
    Optional<String> findNameByCode(String employeeCode);

    /** Lý do không thuộc diện đóng BHXH bắt buộc (vd "Đang hưởng lương hưu"); rỗng = thuộc diện đóng. */
    default Optional<String> insuranceExemption(String employeeCode) {
        return Optional.empty();
    }
}

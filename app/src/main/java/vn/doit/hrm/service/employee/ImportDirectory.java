package vn.doit.hrm.service.employee;

import java.util.List;
import java.util.Optional;

/** Tra cứu dữ liệu hệ thống cho bước kiểm tra file nhập nhân viên (service cài đặt trên repository). */
public interface ImportDirectory {

    /** Mọi mã nhân viên bắt đầu bằng tiền tố. */
    List<String> codesStartingWith(String prefix);

    Optional<Long> employeeIdByCode(String code);

    /** Tìm theo số CCCD đã cắt khoảng trắng (kể cả người đã nghỉ). */
    Optional<Long> employeeIdByIdNumber(String trimmedIdNumber);

    List<DepartmentRef> departments();

    record DepartmentRef(Long id, String name, boolean active) {}
}

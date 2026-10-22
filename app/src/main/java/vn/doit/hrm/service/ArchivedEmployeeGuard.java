package vn.doit.hrm.service;

import org.springframework.stereotype.Component;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.repository.EmployeeRepository;

/**
 * Hồ sơ đã lưu trữ chỉ xem: mọi thao tác sửa hồ sơ hoặc bản ghi con (hợp đồng, lương, phụ cấp, tài liệu, nơi làm, quyết định,
 * đơn, điều chỉnh phép, nghỉ việc, tài khoản) gọi guard này và nhận lỗi 400 {@code employeearchived}.
 */
@Component
public class ArchivedEmployeeGuard {

    public static final String MESSAGE = "Hồ sơ đã lưu trữ, khôi phục trước khi sửa";

    public static final String KEY = "employeearchived";

    private final EmployeeRepository employeeRepository;

    public ArchivedEmployeeGuard(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public void requireActive(Employee employee) {
        if (employee != null && employee.isArchived()) {
            throw new EmployeeRuleException(MESSAGE, KEY);
        }
    }

    /** {@code employeeId} null hoặc không tồn tại thì bỏ qua (nơi gọi tự báo lỗi không tìm thấy). */
    public void requireActive(Long employeeId) {
        if (employeeId != null && employeeRepository.existsByIdAndArchivedAtIsNotNull(employeeId)) {
            throw new EmployeeRuleException(MESSAGE, KEY);
        }
    }
}

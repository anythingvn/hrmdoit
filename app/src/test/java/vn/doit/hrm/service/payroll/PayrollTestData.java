package vn.doit.hrm.service.payroll;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;

/** Dựng các hồ sơ của file mẫu và dọn mọi dữ liệu bảng lương sau test (cơ sở dữ liệu test dùng chung). */
@TestComponent
public class PayrollTestData {

    private final EmployeeRepository employeeRepository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final List<Long> created = new ArrayList<>();

    public PayrollTestData(EmployeeRepository employeeRepository, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.employeeRepository = employeeRepository;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** Tạo các hồ sơ có sẵn; trả mã → id. */
    public Map<String, Long> seedProfiles() {
        Map<String, Long> ids = new HashMap<>();
        tx.executeWithoutResult(s -> {
            for (String[] p : PayrollFixture.PROFILES) {
                ids.put(p[0], save(p[0], p[1], PersonType.NHAN_VIEN));
            }
        });
        return ids;
    }

    /** Hồ sơ người hợp đồng dịch vụ có sẵn (mã tự sinh khi lưu qua service; ở đây đặt tay). */
    public Long seedService(String code, String fullName) {
        return tx.execute(s -> save(code, fullName, PersonType.DICH_VU));
    }

    public void track(Long employeeId) {
        created.add(employeeId);
    }

    /** Xóa dữ liệu bảng lương và các hồ sơ đã tạo trong test. */
    public void clean() {
        // JHipster tắt auto-commit: xóa trong một transaction để thực sự được commit.
        tx.executeWithoutResult(s -> deleteAll());
        tx.executeWithoutResult(s -> created.forEach(id -> employeeRepository.findById(id).ifPresent(employeeRepository::delete)));
        created.clear();
    }

    private void deleteAll() {
        for (String table : List.of(
            "payroll_line",
            "payroll_version",
            "payroll_period_event",
            "payroll_period",
            "payroll_import_mapping",
            "payroll_import"
        )) {
            if (exists(table)) {
                jdbc.update("delete from " + table);
            }
        }
    }

    private boolean exists(String table) {
        Integer n = jdbc.queryForObject("select count(*) from information_schema.tables where table_name = ?", Integer.class, table);
        return n != null && n > 0;
    }

    private Long save(String code, String fullName, PersonType type) {
        Employee e = employeeRepository.save(
            new Employee().code(code).fullName(fullName).personType(type).status(EmploymentStatus.DANG_LAM_VIEC).dependants(0)
        );
        created.add(e.getId());
        return e.getId();
    }
}

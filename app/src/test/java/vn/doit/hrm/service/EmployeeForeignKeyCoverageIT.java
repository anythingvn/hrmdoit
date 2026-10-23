package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.doit.hrm.IntegrationTest;

/**
 * Mọi khóa ngoại tới {@code employee} trong CSDL thật phải được {@link EmployeeDeletionService} xử lý. Thêm bảng mới trỏ tới nhân viên
 * mà quên dịch vụ xóa hẳn thì test này đỏ (thay vì xóa hỏng ở production).
 */
@IntegrationTest
class EmployeeForeignKeyCoverageIT {

    @Autowired
    private JdbcTemplate jdbc;

    private List<Map<String, Object>> employeeForeignKeys() {
        return jdbc.queryForList(
            "select kcu.table_name as table_name, kcu.column_name as column_name " +
                "from information_schema.table_constraints tc " +
                "join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name and kcu.table_schema = tc.table_schema " +
                "join information_schema.constraint_column_usage ccu on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema " +
                "where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = 'public' and ccu.table_name = 'employee' " +
                "and kcu.table_name not like '%\\_aud'"
        );
    }

    @Test
    void everyEmployeeForeignKeyIsHandled() {
        List<Map<String, Object>> fks = employeeForeignKeys();
        // Bảo đảm truy vấn thật sự đọc được khóa ngoại (không đạt "rỗng nên đúng").
        assertThat(fks).hasSizeGreaterThanOrEqualTo(15);
        Set<String> tables = new HashSet<>();
        Set<String> columns = new HashSet<>();
        for (Map<String, Object> fk : fks) {
            tables.add((String) fk.get("table_name"));
            columns.add(fk.get("table_name") + "." + fk.get("column_name"));
        }
        assertThat(EmployeeDeletionService.HANDLED_TABLES).as("bảng có khóa ngoại tới employee").containsAll(tables);
        Set<String> handledColumns = new HashSet<>(EmployeeDeletionService.NULLED_COLUMNS);
        handledColumns.addAll(EmployeeDeletionService.DELETED_COLUMNS);
        assertThat(handledColumns).as("cột khóa ngoại tới employee (đặt trống hoặc xóa dòng)").containsAll(columns);
        assertThat(tables).contains("employment_period", "payroll_line", "leave_request");
        assertThat(tables).contains("work_site_onsite_lead", "attendance_punch", "attendance_mark", "attendance_snapshot");
    }
}

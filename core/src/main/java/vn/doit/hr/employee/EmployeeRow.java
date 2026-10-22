package vn.doit.hr.employee;

import java.time.LocalDate;
import java.util.Map;

/**
 * Một dòng nhân viên đã đọc.
 *
 * @param excelRow số dòng trong Excel (1-based)
 * @param values giá trị theo cột; ô trống không có khóa; cột lựa chọn lưu nhãn chuẩn
 */
public record EmployeeRow(int excelRow, Map<EmployeeField, Object> values) {

    public String text(EmployeeField field) {
        return (String) values.get(field);
    }

    public LocalDate date(EmployeeField field) {
        return (LocalDate) values.get(field);
    }

    public Long integer(EmployeeField field) {
        return (Long) values.get(field);
    }
}

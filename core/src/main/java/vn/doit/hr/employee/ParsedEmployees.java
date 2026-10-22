package vn.doit.hr.employee;

import java.util.List;
import java.util.Map;

/**
 * Kết quả đọc sheet nhân viên.
 *
 * @param columns chỉ số cột (0-based) của từng trường tìm thấy
 */
public record ParsedEmployees(Map<EmployeeField, Integer> columns, List<EmployeeRow> rows,
                              List<EmployeeIssue> issues) {
}

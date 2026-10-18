package vn.doit.hr.payroll;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kết quả đọc một file bảng lương.
 *
 * @param periodInFile kỳ lương ghi ở tiêu đề file ("Tháng 08/2025"), nếu đọc được
 * @param columns cột (0-based) tìm thấy cho từng trường
 * @param lines các dòng nhân viên
 * @param issues lỗi phát hiện khi đọc (chưa gồm lỗi nghiệp vụ của {@link PayrollValidator})
 */
public record ParsedPayroll(
        Optional<YearMonth> periodInFile,
        Map<PayrollField, Integer> columns,
        List<PayrollLine> lines,
        List<Issue> issues) {
}

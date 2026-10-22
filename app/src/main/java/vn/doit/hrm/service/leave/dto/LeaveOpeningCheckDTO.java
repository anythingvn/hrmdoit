package vn.doit.hrm.service.leave.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả kiểm tra file số dư đầu kỳ.
 *
 * @param canSave không có lỗi nào
 */
public record LeaveOpeningCheckDTO(int rowCount, List<Row> rows, List<Issue> issues, long errorCount, boolean canSave) {
    /** Một dòng hợp lệ sẽ được ghi. */
    public record Row(
        int excelRow,
        String code,
        String fullName,
        BigDecimal annualRemaining,
        BigDecimal annualPreviousYear,
        BigDecimal compensatoryHours
    ) {}

    /** Lỗi của một dòng ({@code excelRow} = số dòng trong Excel). */
    public record Issue(int excelRow, String code, String message) {}
}

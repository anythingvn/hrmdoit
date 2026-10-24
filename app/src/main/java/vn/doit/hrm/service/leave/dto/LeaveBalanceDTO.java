package vn.doit.hrm.service.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Số dư của một người tại hôm nay.
 *
 * @param sick chỉ có ở số dư của chính mình
 * @param overtime chỉ có ở số dư của chính mình
 */
public record LeaveBalanceDTO(
    Long employeeId,
    String code,
    String fullName,
    String departmentName,
    Annual annual,
    Compensatory compensatory,
    Sick sick,
    Overtime overtime
) {
    /**
     * Phép năm (ngày).
     *
     * @param requestable số được xin: tổng làm tròn xuống 0,5 cộng số ngày được ứng
     * @param previousYearExpiry hạn dùng phần năm trước (null nếu phần đó không dương)
     * @param baseAnnualDays mức phép năm gốc của hồ sơ
     * @param seniorityYears số năm thâm niên tròn tính đến ngày cuối tháng này
     * @param seniorityExtraDays số ngày cộng thêm theo thâm niên (tháng này)
     * @param annualDays mức phép năm tháng này = gốc + phần thâm niên
     * @param advanceDays số ngày được ứng (theo cài đặt), đã gồm trong {@code requestable}
     */
    public record Annual(
        BigDecimal previousYear,
        BigDecimal currentYear,
        BigDecimal total,
        BigDecimal requestable,
        LocalDate previousYearExpiry,
        int baseAnnualDays,
        int seniorityYears,
        int seniorityExtraDays,
        int annualDays,
        int advanceDays
    ) {}

    /** Quỹ nghỉ bù (giờ). */
    public record Compensatory(BigDecimal previousYear, BigDecimal currentYear, BigDecimal total) {}

    /** Ngày nghỉ ốm đã nghỉ trong năm và hạn mức BHXH. */
    public record Sick(BigDecimal usedDays, int limitDays) {}

    /** Giờ làm thêm tháng này và giới hạn tháng. */
    public record Overtime(BigDecimal monthHours, int monthCap) {}
}

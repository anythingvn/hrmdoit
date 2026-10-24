package vn.doit.hrm.service.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cài đặt nghỉ phép.
 *
 * @param seniorityCycleYears chu kỳ thâm niên (năm, 1..10); bỏ trống khi sửa là giữ nguyên
 * @param seniorityExtraDays số ngày cộng mỗi chu kỳ (0..5, 0 là tắt); bỏ trống khi sửa là giữ nguyên
 */
public record LeaveSettingDTO(
    @NotNull @Min(0) @Max(5) Integer advanceDays,
    @NotNull @Min(1) @Max(60) Integer directorThresholdDays,
    @NotNull @Min(0) @Max(300) Integer otMonthCap,
    @NotNull @Min(0) @Max(3000) Integer otYearCap,
    @NotNull @Min(1) @Max(12) Integer carryExpiryMonth,
    @NotNull @Min(1) @Max(31) Integer carryExpiryDay,
    @NotNull @Min(0) @Max(366) Integer pastRequestDays,
    @NotNull @Min(1) @Max(28) Integer otConfirmDay,
    Integer seniorityCycleYears,
    Integer seniorityExtraDays
) {}

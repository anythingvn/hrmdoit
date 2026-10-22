package vn.doit.hrm.service.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Cài đặt nghỉ phép. */
public record LeaveSettingDTO(
    @NotNull @Min(0) @Max(5) Integer advanceDays,
    @NotNull @Min(1) @Max(60) Integer directorThresholdDays,
    @NotNull @Min(0) @Max(300) Integer otMonthCap,
    @NotNull @Min(0) @Max(3000) Integer otYearCap,
    @NotNull @Min(1) @Max(12) Integer carryExpiryMonth,
    @NotNull @Min(1) @Max(31) Integer carryExpiryDay,
    @NotNull @Min(0) @Max(366) Integer pastRequestDays,
    @NotNull @Min(1) @Max(28) Integer otConfirmDay
) {}

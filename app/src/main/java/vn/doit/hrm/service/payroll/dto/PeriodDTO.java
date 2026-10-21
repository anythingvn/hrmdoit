package vn.doit.hrm.service.payroll.dto;

import java.time.Instant;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;

/** Trạng thái một kỳ lương sau khi duyệt / mở lại. */
public record PeriodDTO(String period, PayrollPeriodStatus status, Integer approvedVersion, String approvedBy, Instant approvedAt) {}

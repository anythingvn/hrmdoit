package vn.doit.hrm.service.payroll.dto;

import java.time.Instant;
import java.util.List;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;

/**
 * Trạng thái một kỳ lương sau khi duyệt / mở lại. {@code warnings}: cảnh báo không chặn khi duyệt (vd bảng công tháng chưa chốt).
 */
public record PeriodDTO(
    String period,
    PayrollPeriodStatus status,
    Integer approvedVersion,
    String approvedBy,
    Instant approvedAt,
    List<String> warnings
) {}

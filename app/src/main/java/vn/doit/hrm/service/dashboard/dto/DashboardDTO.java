package vn.doit.hrm.service.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;

/** Số liệu trang Tổng quan. {@code payrollCost} chỉ có với Quản trị và Kế toán; vai trò khác không có trường này. */
public record DashboardDTO(
    long headcount,
    long onProbation,
    long contractsExpiring30,
    @JsonInclude(JsonInclude.Include.NON_NULL) PayrollCost payrollCost,
    List<Movement> movement
) {
    /** Kỳ lương gần nhất có dữ liệu, theo phiên bản chính thức. */
    public record PayrollCost(String period, PayrollPeriodStatus status, BigDecimal totalCost) {}

    /** {@code month} dạng yyyy-MM. */
    public record Movement(String month, int hires, int leavers, int closing) {}
}

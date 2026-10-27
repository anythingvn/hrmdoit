package vn.doit.hrm.service.payroll.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hrm.domain.enumeration.PayrollCalcStatus;

/**
 * Bản tính lương nháp: dòng, cột, tổng (cộng các dòng không lỗi) và điều kiện xác nhận. {@code excluded}: dòng đã bỏ (khôi phục
 * được); {@code notices}: cảnh báo chung của cả bản tính (vd chưa khai báo mức luật cho kỳ), cũng phải xác nhận như cảnh báo dòng.
 */
public record CalcDraftDTO(
    Long id,
    String period,
    PayrollCalcStatus status,
    List<CalcLineDTO> lines,
    Map<PayrollField, BigDecimal> totals,
    boolean canConfirm,
    boolean warningsAcknowledged,
    List<CalcColumnDTO> columns,
    List<CalcExcludedDTO> excluded,
    List<String> notices
) {}

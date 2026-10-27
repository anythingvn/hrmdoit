package vn.doit.hrm.service.payroll.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hrm.domain.enumeration.PayrollCalcStatus;

/** Bản tính lương nháp: dòng, cột, tổng (cộng các dòng không lỗi) và điều kiện xác nhận. */
public record CalcDraftDTO(
    Long id,
    String period,
    PayrollCalcStatus status,
    List<CalcLineDTO> lines,
    Map<PayrollField, BigDecimal> totals,
    boolean canConfirm,
    boolean warningsAcknowledged,
    List<CalcColumnDTO> columns
) {}

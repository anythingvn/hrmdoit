package vn.doit.hrm.service.payroll.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import vn.doit.hr.payroll.PayrollField;

/**
 * Một dòng của bản tính nháp. {@code amounts}: 36 khoản (rỗng khi dòng còn lỗi); {@code manual}: ô nhập tay; {@code overrides}: ô
 * tự tính đã sửa tay; {@code stale}: người không còn thuộc kỳ.
 */
public record CalcLineDTO(
    Long id,
    Long employeeId,
    String code,
    String fullName,
    String position,
    Map<PayrollField, BigDecimal> amounts,
    Map<PayrollField, BigDecimal> manual,
    Map<PayrollField, BigDecimal> overrides,
    List<String> errors,
    List<String> warnings,
    boolean stale
) {}

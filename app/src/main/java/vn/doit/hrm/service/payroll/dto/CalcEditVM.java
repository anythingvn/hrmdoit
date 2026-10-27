package vn.doit.hrm.service.payroll.dto;

import java.math.BigDecimal;
import java.util.Map;
import vn.doit.hr.payroll.PayrollField;

/** Sửa một dòng: map trường → số; giá trị null = bỏ ô nhập tay / bỏ sửa tay (về số tự tính). Thiếu map = không đổi. */
public record CalcEditVM(Map<PayrollField, BigDecimal> manual, Map<PayrollField, BigDecimal> overrides) {}

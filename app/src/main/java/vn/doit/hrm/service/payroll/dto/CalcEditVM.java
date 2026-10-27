package vn.doit.hrm.service.payroll.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Sửa một dòng: map tên trường → số; giá trị null = bỏ ô nhập tay / bỏ sửa tay (về số tự tính). Thiếu map = không đổi. Tên trường
 * lạ bị từ chối ở máy chủ bằng {@code calcfieldinvalid}.
 */
public record CalcEditVM(Map<String, BigDecimal> manual, Map<String, BigDecimal> overrides) {}

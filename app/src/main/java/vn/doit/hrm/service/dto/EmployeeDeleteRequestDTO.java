package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.Size;

/** Xóa hẳn: gõ lại mã nhân viên để xác nhận và lý do (bắt buộc, tối đa 500 ký tự). */
public record EmployeeDeleteRequestDTO(@Size(max = 50) String confirmCode, @Size(max = 500) String reason) {}

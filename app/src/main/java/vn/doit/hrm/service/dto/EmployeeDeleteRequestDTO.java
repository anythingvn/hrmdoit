package vn.doit.hrm.service.dto;

/** Xóa hẳn: gõ lại mã nhân viên để xác nhận và lý do (bắt buộc, tối đa 500 ký tự; dịch vụ kiểm tra sau khi kiểm tra quyền). */
public record EmployeeDeleteRequestDTO(String confirmCode, String reason) {}

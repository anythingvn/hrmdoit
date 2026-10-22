package vn.doit.hrm.service.leave.dto;

import jakarta.validation.constraints.Size;

/** Ghi chú kèm một bước của đơn (bắt buộc khi từ chối). */
public record LeaveDecisionDTO(@Size(max = 500) String note) {}

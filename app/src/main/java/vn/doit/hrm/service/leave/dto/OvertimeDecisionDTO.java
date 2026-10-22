package vn.doit.hrm.service.leave.dto;

import jakarta.validation.constraints.Size;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;

/** Một bước của đơn làm thêm giờ: hình thức (bắt buộc khi duyệt và khi đổi hình thức), ghi chú (bắt buộc khi từ chối). */
public record OvertimeDecisionDTO(OvertimeCompensation compensation, @Size(max = 500) String note) {}

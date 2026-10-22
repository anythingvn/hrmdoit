package vn.doit.hrm.service.leave.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Từ chối nhiều đơn với cùng một lý do (bắt buộc; thiếu → 400 {@code leavenoterequired}); mỗi đơn từ chối riêng, kết quả từng đơn như
 * {@link LeaveBulkApproveDTO.Result}.
 */
public record LeaveBulkRejectDTO(@NotEmpty @Size(max = 200) List<Long> ids, @Size(max = 500) String note) {}

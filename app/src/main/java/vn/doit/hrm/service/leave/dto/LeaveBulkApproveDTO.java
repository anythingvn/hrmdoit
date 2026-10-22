package vn.doit.hrm.service.leave.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Duyệt nhiều đơn; mỗi đơn duyệt riêng (đơn lỗi không chặn đơn khác). */
public record LeaveBulkApproveDTO(@NotEmpty @Size(max = 200) List<Long> ids, @Size(max = 500) String note) {
    /** Kết quả từng đơn: {@code errorKey} / {@code message} khi không duyệt được. */
    public record Result(Long id, boolean ok, String errorKey, String message) {}
}

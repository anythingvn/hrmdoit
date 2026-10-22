package vn.doit.hrm.service.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Pool;

/**
 * HR điều chỉnh tay (cộng hoặc trừ).
 *
 * @param bucket ngăn ghi vào; bỏ trống = năm nay
 * @param amount ngày (phép năm) hoặc giờ (nghỉ bù), khác 0, tối đa 2 chữ số thập phân
 */
public record LeaveAdjustmentDTO(
    @NotNull Long employeeId,
    @NotNull Pool pool,
    Bucket bucket,
    @NotNull BigDecimal amount,
    @NotBlank @Size(max = 500) String note
) {}

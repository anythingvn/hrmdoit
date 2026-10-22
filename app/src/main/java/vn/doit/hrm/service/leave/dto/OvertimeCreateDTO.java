package vn.doit.hrm.service.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/** Đăng ký làm thêm giờ: ngày làm, giờ bắt đầu – kết thúc (kết thúc nhỏ hơn bắt đầu = qua nửa đêm), nội dung công việc. */
public record OvertimeCreateDTO(
    @NotNull LocalDate workDate,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @Size(max = 1000) String content
) {}

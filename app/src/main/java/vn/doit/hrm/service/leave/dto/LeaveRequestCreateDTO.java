package vn.doit.hrm.service.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import vn.doit.hr.leave.DayPart;
import vn.doit.hr.leave.PersonalLeaveReason;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;

/** Gửi đơn nghỉ. Phần ngày mặc định cả ngày; đơn một ngày chỉ dùng {@code firstPart}. */
public record LeaveRequestCreateDTO(
    @NotNull LeaveRequestType type,
    PersonalLeaveReason personalReason,
    @NotNull LocalDate fromDate,
    @NotNull LocalDate toDate,
    DayPart firstPart,
    DayPart lastPart,
    @Size(max = 1000) String reasonText
) {}

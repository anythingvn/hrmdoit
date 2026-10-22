package vn.doit.hrm.service.leave.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;

/** Đơn làm thêm giờ. {@code compensation} null khi chưa duyệt; {@code actualHours} null khi chưa xác nhận. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OvertimeRequestDTO(
    Long id,
    Long employeeId,
    String employeeCode,
    String employeeName,
    LocalDate workDate,
    LocalTime startTime,
    LocalTime endTime,
    BigDecimal plannedHours,
    BigDecimal actualHours,
    int rate,
    boolean night,
    String content,
    OvertimeStatus status,
    OvertimeCompensation compensation,
    ApproverKind approverKind,
    Long approverEmployeeId,
    String approverName,
    String decidedBy,
    Instant decidedAt,
    String decisionNote,
    boolean onBehalf,
    Instant confirmedAt,
    boolean autoConfirmed,
    Instant createdAt,
    String submittedBy
) {}

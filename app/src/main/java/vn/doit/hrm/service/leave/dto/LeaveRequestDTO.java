package vn.doit.hrm.service.leave.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import vn.doit.hr.leave.DayPart;
import vn.doit.hr.leave.PersonalLeaveReason;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;

/**
 * Đơn nghỉ. {@code personalReason}, {@code reasonText}, {@code sickDocId} chỉ có khi người xem là người gửi, người duyệt của đơn,
 * HR hoặc Quản trị (null với người khác, không xuất ra JSON). {@code sickOverLimit} chỉ có khi gửi đơn nghỉ ốm. {@code balance}
 * chỉ có trong hộp duyệt.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaveRequestDTO(
    Long id,
    Long employeeId,
    String employeeCode,
    String employeeName,
    LeaveRequestType type,
    PersonalLeaveReason personalReason,
    LocalDate fromDate,
    LocalDate toDate,
    DayPart firstPart,
    DayPart lastPart,
    BigDecimal days,
    BigDecimal hours,
    String reasonText,
    LeaveRequestStatus status,
    ApproverKind approverKind,
    Long approverEmployeeId,
    String approverName,
    String decidedBy,
    Instant decidedAt,
    String decisionNote,
    boolean onBehalf,
    Long sickDocId,
    String systemNote,
    Instant createdAt,
    String submittedBy,
    Boolean sickOverLimit,
    RequesterBalance balance
) {
    /** Số dư người xin (hộp duyệt): phép năm (tổng, số được xin) và nghỉ bù (giờ). */
    public record RequesterBalance(BigDecimal annualTotal, BigDecimal annualRequestable, BigDecimal compensatoryHours) {}

    public LeaveRequestDTO withSickOverLimit(boolean over) {
        return new LeaveRequestDTO(id, employeeId, employeeCode, employeeName, type, personalReason, fromDate, toDate, firstPart, lastPart, days, hours, reasonText, status, approverKind, approverEmployeeId, approverName, decidedBy, decidedAt, decisionNote, onBehalf, sickDocId, systemNote, createdAt, submittedBy, over, balance);
    }

    public LeaveRequestDTO withBalance(RequesterBalance b) {
        return new LeaveRequestDTO(id, employeeId, employeeCode, employeeName, type, personalReason, fromDate, toDate, firstPart, lastPart, days, hours, reasonText, status, approverKind, approverEmployeeId, approverName, decidedBy, decidedAt, decisionNote, onBehalf, sickDocId, systemNote, createdAt, submittedBy, sickOverLimit, b);
    }
}

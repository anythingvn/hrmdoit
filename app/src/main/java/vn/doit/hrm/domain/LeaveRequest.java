package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.envers.Audited;
import vn.doit.hr.leave.DayPart;
import vn.doit.hr.leave.PersonalLeaveReason;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;

/**
 * Đơn nghỉ. Người duyệt được tính lúc gửi và lưu trên đơn ({@code approverKind}, {@code approverEmployee} – null khi BGĐ hoặc Quản
 * trị). Quỹ chỉ bị trừ khi duyệt (dòng sổ {@code DUYET_DON}). Mọi bước có lịch sử (Envers).
 */
@Entity
@Audited
@Table(name = "leave_request")
public class LeaveRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private LeaveRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "personal_reason", length = 20)
    private PersonalLeaveReason personalReason;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "first_part", length = 10, nullable = false)
    private DayPart firstPart;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_part", length = 10, nullable = false)
    private DayPart lastPart;

    /** Số ngày làm việc của đơn (lễ, cuối tuần không tính). */
    @Column(name = "days", precision = 5, scale = 2, nullable = false)
    private BigDecimal days;

    /** Số giờ nghỉ bù (= ngày × 8), chỉ với đơn nghỉ bù. */
    @Column(name = "hours", precision = 6, scale = 2)
    private BigDecimal hours;

    @Column(name = "reason_text", length = 1000)
    private String reasonText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private LeaveRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_kind", length = 20, nullable = false)
    private ApproverKind approverKind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_employee_id")
    private Employee approverEmployee;

    @Column(name = "decided_by", length = 50)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    /** Quyết định gần nhất do HR / Quản trị làm thay người duyệt. */
    @Column(name = "on_behalf", nullable = false)
    private boolean onBehalf;

    /** Giấy chứng nhận nghỉ hưởng BHXH (hồ sơ scan của nhân viên). */
    @Column(name = "sick_doc_id")
    private Long sickDocId;

    /** Ghi chú của hệ thống: chuyển người duyệt, rút ngắn khi nghỉ việc. */
    @Column(name = "system_note", length = 500)
    private String systemNote;

    /**
     * Khoảng ngày của đơn ngay trước lần đầu bị nghỉ việc rút ngắn / hủy, và thời điểm đó: tổng hợp kế toán giữ nguyên các tháng đã
     * duyệt lương trước thời điểm đó và đưa phần thay đổi vào tháng ghi (I2).
     */
    @Column(name = "termination_orig_to_date")
    private LocalDate terminationOrigToDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_orig_last_part", length = 10)
    private DayPart terminationOrigLastPart;

    @Column(name = "termination_orig_days", precision = 5, scale = 2)
    private BigDecimal terminationOrigDays;

    @Column(name = "termination_changed_at")
    private Instant terminationChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "submitted_by", length = 50, nullable = false)
    private String submittedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LeaveRequestType getType() {
        return type;
    }

    public void setType(LeaveRequestType type) {
        this.type = type;
    }

    public PersonalLeaveReason getPersonalReason() {
        return personalReason;
    }

    public void setPersonalReason(PersonalLeaveReason personalReason) {
        this.personalReason = personalReason;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public DayPart getFirstPart() {
        return firstPart;
    }

    public void setFirstPart(DayPart firstPart) {
        this.firstPart = firstPart;
    }

    public DayPart getLastPart() {
        return lastPart;
    }

    public void setLastPart(DayPart lastPart) {
        this.lastPart = lastPart;
    }

    public BigDecimal getDays() {
        return days;
    }

    public void setDays(BigDecimal days) {
        this.days = days;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    public LeaveRequestStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveRequestStatus status) {
        this.status = status;
    }

    public ApproverKind getApproverKind() {
        return approverKind;
    }

    public void setApproverKind(ApproverKind approverKind) {
        this.approverKind = approverKind;
    }

    public Employee getApproverEmployee() {
        return approverEmployee;
    }

    public void setApproverEmployee(Employee approverEmployee) {
        this.approverEmployee = approverEmployee;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public boolean isOnBehalf() {
        return onBehalf;
    }

    public void setOnBehalf(boolean onBehalf) {
        this.onBehalf = onBehalf;
    }

    public Long getSickDocId() {
        return sickDocId;
    }

    public void setSickDocId(Long sickDocId) {
        this.sickDocId = sickDocId;
    }

    public LocalDate getTerminationOrigToDate() {
        return terminationOrigToDate;
    }

    public void setTerminationOrigToDate(LocalDate terminationOrigToDate) {
        this.terminationOrigToDate = terminationOrigToDate;
    }

    public DayPart getTerminationOrigLastPart() {
        return terminationOrigLastPart;
    }

    public void setTerminationOrigLastPart(DayPart terminationOrigLastPart) {
        this.terminationOrigLastPart = terminationOrigLastPart;
    }

    public BigDecimal getTerminationOrigDays() {
        return terminationOrigDays;
    }

    public void setTerminationOrigDays(BigDecimal terminationOrigDays) {
        this.terminationOrigDays = terminationOrigDays;
    }

    public Instant getTerminationChangedAt() {
        return terminationChangedAt;
    }

    public void setTerminationChangedAt(Instant terminationChangedAt) {
        this.terminationChangedAt = terminationChangedAt;
    }

    public String getSystemNote() {
        return systemNote;
    }

    public void setSystemNote(String systemNote) {
        this.systemNote = systemNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    /** Quỹ bị trừ khi duyệt (null nếu loại đơn không trừ quỹ) và số lượng theo đơn vị của quỹ (ngày hoặc giờ). */
    public BigDecimal poolAmount() {
        if (type.pool() == null) {
            return null;
        }
        return type.pool() == vn.doit.hr.leave.Pool.NGHI_BU ? hours : days;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LeaveRequest other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "LeaveRequest{id=" + id + ", type=" + type + ", from=" + fromDate + ", to=" + toDate + ", status=" + status + "}";
    }
}

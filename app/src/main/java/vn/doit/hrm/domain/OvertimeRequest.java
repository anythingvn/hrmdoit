package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;

/**
 * Đơn làm thêm giờ (spec 4.2). Giờ, hệ số và cờ giờ đêm tính lúc đăng ký theo ngày làm. Người duyệt tính lúc gửi và lưu trên đơn
 * ({@code approverEmployee} null khi BGĐ hoặc Quản trị). Giờ nghỉ bù chỉ vào quỹ khi hoàn tất (dòng sổ {@code LAM_THEM}). Mọi bước có
 * lịch sử (Envers).
 */
@Entity
@Audited
@Table(name = "overtime_request")
public class OvertimeRequest implements Serializable {

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

    /** Ngày làm: giờ làm thêm thuộc tháng của ngày này (giới hạn, kỳ lương), kể cả phần qua nửa đêm. */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** Giờ kết thúc; nhỏ hơn giờ bắt đầu nghĩa là qua nửa đêm. */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "planned_hours", precision = 5, scale = 2, nullable = false)
    private BigDecimal plannedHours;

    /** Giờ thực tế người gửi xác nhận (hoặc giờ đăng ký khi tự xác nhận / không duyệt phần chênh). */
    @Column(name = "actual_hours", precision = 5, scale = 2)
    private BigDecimal actualHours;

    /** Hệ số %: 150 ngày làm việc, 200 ngày nghỉ hằng tuần, 300 ngày lễ. */
    @Column(name = "rate", nullable = false)
    private int rate;

    /** Có giờ trong khoảng 22:00–06:00 (kế toán xử lý). */
    @Column(name = "night", nullable = false)
    private boolean night;

    @Column(name = "content", length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OvertimeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "compensation", length = 10)
    private OvertimeCompensation compensation;

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

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** Hệ thống tự xác nhận khi quá hạn (giờ thực tế = giờ đăng ký). */
    @Column(name = "auto_confirmed", nullable = false)
    private boolean autoConfirmed;

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

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public BigDecimal getPlannedHours() {
        return plannedHours;
    }

    public void setPlannedHours(BigDecimal plannedHours) {
        this.plannedHours = plannedHours;
    }

    public BigDecimal getActualHours() {
        return actualHours;
    }

    public void setActualHours(BigDecimal actualHours) {
        this.actualHours = actualHours;
    }

    public int getRate() {
        return rate;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public boolean isNight() {
        return night;
    }

    public void setNight(boolean night) {
        this.night = night;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public OvertimeStatus getStatus() {
        return status;
    }

    public void setStatus(OvertimeStatus status) {
        this.status = status;
    }

    public OvertimeCompensation getCompensation() {
        return compensation;
    }

    public void setCompensation(OvertimeCompensation compensation) {
        this.compensation = compensation;
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

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public boolean isAutoConfirmed() {
        return autoConfirmed;
    }

    public void setAutoConfirmed(boolean autoConfirmed) {
        this.autoConfirmed = autoConfirmed;
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

    /** Giờ dùng cho giới hạn và quỹ nghỉ bù: giờ thực tế khi đã có, không thì giờ đăng ký. */
    public BigDecimal effectiveHours() {
        return actualHours != null && status == OvertimeStatus.HOAN_TAT ? actualHours : plannedHours;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OvertimeRequest other)) {
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
        return "OvertimeRequest{id=" + id + ", workDate=" + workDate + ", start=" + startTime + ", end=" + endTime + ", status=" + status + "}";
    }
}

package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcType;
import org.hibernate.envers.Audited;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;
import vn.doit.hrm.domain.enumeration.ExplanationStatus;

/**
 * Giải trình chấm công của nhân viên cho một ngày: giờ vào và/hoặc giờ ra đề xuất kèm lý do. Duyệt thì sinh lần chấm {@code GIAI_TRINH}
 * cho mỗi giờ đề xuất ({@link AttendancePunch#getExplanationId()}). Mỗi người mỗi ngày tối đa một giải trình {@code DANG_CHO}.
 */
@Entity
@Audited
@Table(name = "attendance_explanation")
public class AttendanceExplanation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @NotNull
    @Column(name = "work_day", nullable = false)
    private LocalDate workDay;

    /** Giờ đồng hồ, cùng cách lưu với {@link AttendancePunch} (không đổi múi giờ). */
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "proposed_in")
    private LocalTime proposedIn;

    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "proposed_out")
    private LocalTime proposedOut;

    @NotNull
    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private ExplanationStatus status = ExplanationStatus.DANG_CHO;

    @Column(name = "decided_by", length = 50)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getWorkDay() {
        return workDay;
    }

    public void setWorkDay(LocalDate workDay) {
        this.workDay = workDay;
    }

    public LocalTime getProposedIn() {
        return proposedIn;
    }

    public void setProposedIn(LocalTime proposedIn) {
        this.proposedIn = proposedIn;
    }

    public LocalTime getProposedOut() {
        return proposedOut;
    }

    public void setProposedOut(LocalTime proposedOut) {
        this.proposedOut = proposedOut;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public ExplanationStatus getStatus() {
        return status;
    }

    public void setStatus(ExplanationStatus status) {
        this.status = status;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttendanceExplanation)) {
            return false;
        }
        return id != null && id.equals(((AttendanceExplanation) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "AttendanceExplanation{id=" + id + ", employeeId=" + employeeId + ", workDay=" + workDay + ", status=" + status + "}";
    }
}

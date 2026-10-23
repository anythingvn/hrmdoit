package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;

/** Bảng công một tháng ({@code month} dạng yyyy-MM như {@code payroll_period.period}): đang mở hoặc đã chốt. */
@Entity
@Table(name = "attendance_month")
public class AttendanceMonth implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "month", length = 7, nullable = false, unique = true)
    private String month;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private AttendanceMonthStatus status;

    @Column(name = "closed_by", length = 50)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public AttendanceMonthStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceMonthStatus status) {
        this.status = status;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttendanceMonth)) {
            return false;
        }
        return id != null && id.equals(((AttendanceMonth) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "AttendanceMonth{id=" + id + ", month='" + month + "', status=" + status + "}";
    }
}

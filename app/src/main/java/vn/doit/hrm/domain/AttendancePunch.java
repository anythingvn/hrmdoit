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
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;

/** Một lần chấm vào hoặc ra của một người trong một ngày (gõ tay hoặc từ ứng dụng). */
@Entity
@Audited
@Table(name = "attendance_punch")
public class AttendancePunch implements Serializable {

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

    /**
     * Giờ đồng hồ (wall-clock) lưu qua {@link LocalTimeJdbcType}: không đổi múi giờ UTC như {@code overtime_request}; SQL thô đọc/ghi
     * cột này phải dùng nguyên giá trị, không dịch múi giờ.
     */
    @NotNull
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "punch_time", nullable = false)
    private LocalTime punchTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10, nullable = false)
    private AttendancePunchKind kind;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 10, nullable = false)
    private AttendancePunchSource source;

    @Column(name = "entered_by", length = 50)
    private String enteredBy;

    @Column(name = "entered_at")
    private Instant enteredAt;

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

    public LocalTime getPunchTime() {
        return punchTime;
    }

    public void setPunchTime(LocalTime punchTime) {
        this.punchTime = punchTime;
    }

    public AttendancePunchKind getKind() {
        return kind;
    }

    public void setKind(AttendancePunchKind kind) {
        this.kind = kind;
    }

    public AttendancePunchSource getSource() {
        return source;
    }

    public void setSource(AttendancePunchSource source) {
        this.source = source;
    }

    public String getEnteredBy() {
        return enteredBy;
    }

    public void setEnteredBy(String enteredBy) {
        this.enteredBy = enteredBy;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(Instant enteredAt) {
        this.enteredAt = enteredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttendancePunch)) {
            return false;
        }
        return id != null && id.equals(((AttendancePunch) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "AttendancePunch{id=" + id + ", employeeId=" + employeeId + ", workDay=" + workDay + ", punchTime=" + punchTime + ", kind=" + kind + "}"
        );
    }
}

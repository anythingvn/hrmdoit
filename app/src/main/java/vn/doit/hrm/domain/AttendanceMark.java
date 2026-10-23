package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.AttendanceMarkKind;

/** Đánh dấu một ngày của một người (hiện chỉ có vắng: 0 công); tối đa một dòng cho mỗi người mỗi ngày. */
@Entity
@Audited
@Table(name = "attendance_mark")
public class AttendanceMark implements Serializable {

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10, nullable = false)
    private AttendanceMarkKind kind;

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

    public AttendanceMarkKind getKind() {
        return kind;
    }

    public void setKind(AttendanceMarkKind kind) {
        this.kind = kind;
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
        if (!(o instanceof AttendanceMark)) {
            return false;
        }
        return id != null && id.equals(((AttendanceMark) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "AttendanceMark{id=" + id + ", employeeId=" + employeeId + ", workDay=" + workDay + ", kind=" + kind + "}";
    }
}

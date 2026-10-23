package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import vn.doit.hrm.domain.enumeration.AttendanceMonthAction;

/** Lịch sử chốt / mở lại bảng công một tháng (người làm, lúc nào, lý do mở lại). */
@Entity
@Table(name = "attendance_month_event")
public class AttendanceMonthEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "month", length = 7, nullable = false)
    private String month;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 10, nullable = false)
    private AttendanceMonthAction action;

    @Column(name = "actor", length = 50)
    private String actor;

    @NotNull
    @Column(name = "event_at", nullable = false)
    private Instant at;

    @Column(name = "reason", length = 500)
    private String reason;

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

    public AttendanceMonthAction getAction() {
        return action;
    }

    public void setAction(AttendanceMonthAction action) {
        this.action = action;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttendanceMonthEvent)) {
            return false;
        }
        return id != null && id.equals(((AttendanceMonthEvent) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "AttendanceMonthEvent{id=" + id + ", month='" + month + "', action=" + action + ", actor='" + actor + "', at=" + at + "}";
    }
}

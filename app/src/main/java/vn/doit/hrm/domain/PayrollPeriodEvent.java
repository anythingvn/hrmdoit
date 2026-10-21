package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import vn.doit.hrm.domain.enumeration.PayrollPeriodAction;
import org.hibernate.envers.Audited;

/** Lịch sử duyệt / mở lại một kỳ lương (phiên bản lúc đó, người làm, lý do mở lại). */
@Entity
@Audited
@Table(name = "payroll_period_event")
public class PayrollPeriodEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "period_id")
    private PayrollPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 10, nullable = false)
    private PayrollPeriodAction action;

    @Column(name = "version")
    private Integer version;

    @Column(name = "actor", length = 50)
    private String actor;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "reason", length = 500)
    private String reason;

    public Long getId() {
        return id;
    }

    public PayrollPeriod getPeriod() {
        return period;
    }

    public void setPeriod(PayrollPeriod period) {
        this.period = period;
    }

    public PayrollPeriodAction getAction() {
        return action;
    }

    public void setAction(PayrollPeriodAction action) {
        this.action = action;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Instant getEventAt() {
        return eventAt;
    }

    public void setEventAt(Instant eventAt) {
        this.eventAt = eventAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

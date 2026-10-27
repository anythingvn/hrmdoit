package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.PayrollCalcStatus;

/** Bản tính lương nháp của một kỳ; mỗi kỳ chỉ có một bản {@code DANG_LAM}. */
@Entity
@Audited
@Table(name = "payroll_calc_draft")
public class PayrollCalcDraft implements Serializable {

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
    @Column(name = "status", length = 15, nullable = false)
    private PayrollCalcStatus status;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "warnings_acknowledged", nullable = false)
    private boolean warningsAcknowledged;

    public Long getId() {
        return id;
    }

    public PayrollPeriod getPeriod() {
        return period;
    }

    public void setPeriod(PayrollPeriod period) {
        this.period = period;
    }

    public PayrollCalcStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollCalcStatus status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isWarningsAcknowledged() {
        return warningsAcknowledged;
    }

    public void setWarningsAcknowledged(boolean warningsAcknowledged) {
        this.warningsAcknowledged = warningsAcknowledged;
    }
}

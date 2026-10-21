package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import org.hibernate.envers.Audited;

/** Kỳ lương (yyyy-MM): trạng thái và phiên bản chính thức khi đã duyệt. Dòng này được khóa khi xác nhận, duyệt, mở lại. */
@Entity
@Audited
@Table(name = "payroll_period")
public class PayrollPeriod implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "period", length = 7, nullable = false, unique = true)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PayrollPeriodStatus status;

    @Column(name = "approved_version")
    private Integer approvedVersion;

    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public Long getId() {
        return id;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public PayrollPeriodStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollPeriodStatus status) {
        this.status = status;
    }

    public Integer getApprovedVersion() {
        return approvedVersion;
    }

    public void setApprovedVersion(Integer approvedVersion) {
        this.approvedVersion = approvedVersion;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }
}

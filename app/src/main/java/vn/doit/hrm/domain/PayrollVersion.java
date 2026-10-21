package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.Audited;

/**
 * Một phiên bản của kỳ lương (mỗi lần xác nhận import). Lưu bản chụp mốc luật đã dùng và lỗi/cảnh báo lúc xác nhận: luật đổi
 * sau đó không làm thay đổi phiên bản đã lưu.
 */
@Entity
@Audited
@Table(name = "payroll_version")
public class PayrollVersion implements Serializable {

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

    @Column(name = "version", nullable = false)
    private int version;

    @ManyToOne
    @JoinColumn(name = "import_id")
    private PayrollImport payrollImport;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "storage_path", length = 500, nullable = false)
    private String storagePath;

    @Column(name = "imported_by", length = 50)
    private String importedBy;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "rules_snapshot", columnDefinition = "text")
    private String rulesSnapshot;

    @Column(name = "issues_snapshot", columnDefinition = "text")
    private String issuesSnapshot;

    public Long getId() {
        return id;
    }

    public PayrollPeriod getPeriod() {
        return period;
    }

    public void setPeriod(PayrollPeriod period) {
        this.period = period;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public PayrollImport getPayrollImport() {
        return payrollImport;
    }

    public void setPayrollImport(PayrollImport payrollImport) {
        this.payrollImport = payrollImport;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getImportedBy() {
        return importedBy;
    }

    public void setImportedBy(String importedBy) {
        this.importedBy = importedBy;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public String getRulesSnapshot() {
        return rulesSnapshot;
    }

    public void setRulesSnapshot(String rulesSnapshot) {
        this.rulesSnapshot = rulesSnapshot;
    }

    public String getIssuesSnapshot() {
        return issuesSnapshot;
    }

    public void setIssuesSnapshot(String issuesSnapshot) {
        this.issuesSnapshot = issuesSnapshot;
    }
}

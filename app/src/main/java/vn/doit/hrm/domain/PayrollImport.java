package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;

/** Bản nháp một lần import bảng lương: file gốc (không đổi) và trạng thái; kết quả đọc được tính lại từ file mỗi lần cần. */
@Entity
@Audited
@Table(name = "payroll_import")
public class PayrollImport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "period", length = 7, nullable = false)
    private String period;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "storage_path", length = 500, nullable = false)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PayrollImportStatus status;

    @Column(name = "warnings_acknowledged", nullable = false)
    private boolean warningsAcknowledged;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
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

    public PayrollImportStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollImportStatus status) {
        this.status = status;
    }

    public boolean isWarningsAcknowledged() {
        return warningsAcknowledged;
    }

    public void setWarningsAcknowledged(boolean warningsAcknowledged) {
        this.warningsAcknowledged = warningsAcknowledged;
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
}

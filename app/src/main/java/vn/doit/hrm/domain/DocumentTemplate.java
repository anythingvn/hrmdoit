package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.TemplateKind;

/**
 * Một phiên bản mẫu văn bản do Quản trị/HR tải lên. Mỗi loại có tối đa một phiên bản {@code active}; không có phiên bản active
 * thì dùng mẫu gốc (phiên bản 0). Bản cũ vẫn giữ để quay lại.
 */
@Entity
@Audited
@Table(name = "document_template")
public class DocumentTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 30, nullable = false)
    private TemplateKind kind;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "storage_path", length = 500, nullable = false)
    private String storagePath;

    @Column(name = "uploaded_by", length = 50)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    public Long getId() {
        return id;
    }

    public TemplateKind getKind() {
        return kind;
    }

    public DocumentTemplate kind(TemplateKind kind) {
        this.kind = kind;
        return this;
    }

    public int getVersion() {
        return version;
    }

    public DocumentTemplate version(int version) {
        this.version = version;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public DocumentTemplate fileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public DocumentTemplate storagePath(String storagePath) {
        this.storagePath = storagePath;
        return this;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public DocumentTemplate uploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
        return this;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public DocumentTemplate uploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
        return this;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

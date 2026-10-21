package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.DocumentOwnerType;
import vn.doit.hrm.domain.enumeration.GeneratedFileKind;
import vn.doit.hrm.domain.enumeration.TemplateKind;

/**
 * Một file văn bản của hợp đồng / quyết định: mỗi lần sinh là một phiên bản .docx (kèm mẫu và phiên bản mẫu đã dùng),
 * bản PDF trỏ về .docx nguồn, và bản đã ký tải lên. Chứa CCCD, tài khoản ngân hàng: chỉ Quản trị, HR tải được.
 */
@Entity
@Audited
@Table(name = "generated_document")
public class GeneratedDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", length = 20, nullable = false)
    private DocumentOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10, nullable = false)
    private GeneratedFileKind kind;

    @Column(name = "storage_path", length = 500, nullable = false)
    private String storagePath;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_kind", length = 30)
    private TemplateKind templateKind;

    @Column(name = "template_version")
    private Integer templateVersion;

    /** Với PDF: id của .docx đã chuyển. */
    @Column(name = "source_document_id")
    private Long sourceDocumentId;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public DocumentOwnerType getOwnerType() {
        return ownerType;
    }

    public GeneratedDocument ownerType(DocumentOwnerType ownerType) {
        this.ownerType = ownerType;
        return this;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public GeneratedDocument ownerId(Long ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    public GeneratedFileKind getKind() {
        return kind;
    }

    public GeneratedDocument kind(GeneratedFileKind kind) {
        this.kind = kind;
        return this;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public GeneratedDocument storagePath(String storagePath) {
        this.storagePath = storagePath;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public GeneratedDocument fileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public TemplateKind getTemplateKind() {
        return templateKind;
    }

    public GeneratedDocument templateKind(TemplateKind templateKind) {
        this.templateKind = templateKind;
        return this;
    }

    public Integer getTemplateVersion() {
        return templateVersion;
    }

    public GeneratedDocument templateVersion(Integer templateVersion) {
        this.templateVersion = templateVersion;
        return this;
    }

    public Long getSourceDocumentId() {
        return sourceDocumentId;
    }

    public GeneratedDocument sourceDocumentId(Long sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public GeneratedDocument createdBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public GeneratedDocument createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}

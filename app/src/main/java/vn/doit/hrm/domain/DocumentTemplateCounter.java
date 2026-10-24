package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import vn.doit.hrm.domain.enumeration.TemplateKind;

/** Số phiên bản mẫu lớn nhất đã cấp cho một loại; chỉ tăng, kể cả khi mẫu bị xóa. */
@Entity
@Table(name = "document_template_counter")
public class DocumentTemplateCounter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 30, nullable = false)
    private TemplateKind kind;

    @Column(name = "last_version", nullable = false)
    private int lastVersion;

    protected DocumentTemplateCounter() {}

    public DocumentTemplateCounter(TemplateKind kind, int lastVersion) {
        this.kind = kind;
        this.lastVersion = lastVersion;
    }

    public TemplateKind getKind() {
        return kind;
    }

    public int getLastVersion() {
        return lastVersion;
    }

    public int next() {
        return ++lastVersion;
    }
}

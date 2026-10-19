package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Một lần thay đổi dữ liệu (revision của Envers), kèm tên đăng nhập người thực hiện.
 */
@Entity
@Table(name = "audit_revision")
@RevisionEntity(AuditRevisionListener.class)
public class AuditRevision implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @RevisionNumber
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @RevisionTimestamp
    @Column(name = "revision_at", nullable = false)
    private Instant revisionAt;

    @Column(name = "login", length = 50)
    private String login;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getRevisionAt() {
        return revisionAt;
    }

    public void setRevisionAt(Instant revisionAt) {
        this.revisionAt = revisionAt;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }
}

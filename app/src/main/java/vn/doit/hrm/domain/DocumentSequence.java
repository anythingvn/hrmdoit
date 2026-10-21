package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.DocumentType;

/**
 * Dãy số của một loại văn bản trong một năm. {@code nextValue}: số sẽ cấp tiếp (Quản trị đặt được);
 * {@code lastIssued}: số lớn nhất hệ thống đã cấp (0 = chưa cấp), không cho đặt {@code nextValue} lùi về số đã cấp.
 */
@Entity
@Audited
@Table(name = "document_sequence")
public class DocumentSequence implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", length = 20, nullable = false)
    private DocumentType type;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "next_value", nullable = false)
    private int nextValue;

    @Column(name = "last_issued", nullable = false)
    private int lastIssued;

    public Long getId() {
        return id;
    }

    public DocumentType getType() {
        return type;
    }

    public int getYear() {
        return year;
    }

    public int getNextValue() {
        return nextValue;
    }

    public void setNextValue(int nextValue) {
        this.nextValue = nextValue;
    }

    public int getLastIssued() {
        return lastIssued;
    }

    public void setLastIssued(int lastIssued) {
        this.lastIssued = lastIssued;
    }
}

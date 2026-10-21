package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import org.hibernate.envers.Audited;

/** Cấu hình mức luật (một dòng, id = 1): tháng cuối cùng đã biết chắc luật; kỳ sau đó chỉ "ước tính". */
@Entity
@Audited
@Table(name = "payroll_rule_setting")
public class PayrollRuleSetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "known_through", length = 7, nullable = false)
    private String knownThrough;

    public Long getId() {
        return id;
    }

    public String getKnownThrough() {
        return knownThrough;
    }

    public void setKnownThrough(String knownThrough) {
        this.knownThrough = knownThrough;
    }
}

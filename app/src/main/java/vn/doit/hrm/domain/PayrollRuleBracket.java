package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import org.hibernate.envers.Audited;

/** Một bậc thuế TNCN của mốc luật: phần thu nhập tính thuế tháng đến {@code upTo} (null = không giới hạn) chịu {@code rate}. */
@Entity
@Audited
@Table(name = "payroll_rule_bracket")
public class PayrollRuleBracket implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "milestone_id")
    private PayrollRuleMilestone milestone;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "up_to")
    private BigDecimal upTo;

    @Column(name = "rate", nullable = false)
    private BigDecimal rate;

    public PayrollRuleBracket() {}

    public PayrollRuleBracket(BigDecimal upTo, BigDecimal rate) {
        this.upTo = upTo;
        this.rate = rate;
    }

    public Long getId() {
        return id;
    }

    public PayrollRuleMilestone getMilestone() {
        return milestone;
    }

    void setMilestone(PayrollRuleMilestone milestone) {
        this.milestone = milestone;
    }

    public int getPosition() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }

    public BigDecimal getUpTo() {
        return upTo;
    }

    public BigDecimal getRate() {
        return rate;
    }
}

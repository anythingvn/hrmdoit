package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.envers.Audited;

/**
 * Một mốc mức luật định: áp dụng từ tháng {@code effectiveFrom} (yyyy-MM) đến trước mốc kế tiếp. Chỉ thêm, không sửa, không
 * xóa; mốc mới phải có hiệu lực sau kỳ lương đã duyệt gần nhất (luật đổi không tác động ngược).
 */
@Entity
@Audited
@Table(name = "payroll_rule_milestone")
public class PayrollRuleMilestone implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "effective_from", length = 7, nullable = false)
    private String effectiveFrom;

    @Column(name = "personal_deduction", nullable = false)
    private BigDecimal personalDeduction;

    @Column(name = "dependant_deduction", nullable = false)
    private BigDecimal dependantDeduction;

    @Column(name = "social_insurance_cap", nullable = false)
    private BigDecimal socialInsuranceCap;

    @Column(name = "unemployment_insurance_cap", nullable = false)
    private BigDecimal unemploymentInsuranceCap;

    @Column(name = "legal_basis", length = 500)
    private String legalBasis;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position")
    private List<PayrollRuleBracket> brackets = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public BigDecimal getPersonalDeduction() {
        return personalDeduction;
    }

    public void setPersonalDeduction(BigDecimal personalDeduction) {
        this.personalDeduction = personalDeduction;
    }

    public BigDecimal getDependantDeduction() {
        return dependantDeduction;
    }

    public void setDependantDeduction(BigDecimal dependantDeduction) {
        this.dependantDeduction = dependantDeduction;
    }

    public BigDecimal getSocialInsuranceCap() {
        return socialInsuranceCap;
    }

    public void setSocialInsuranceCap(BigDecimal socialInsuranceCap) {
        this.socialInsuranceCap = socialInsuranceCap;
    }

    public BigDecimal getUnemploymentInsuranceCap() {
        return unemploymentInsuranceCap;
    }

    public void setUnemploymentInsuranceCap(BigDecimal unemploymentInsuranceCap) {
        this.unemploymentInsuranceCap = unemploymentInsuranceCap;
    }

    public String getLegalBasis() {
        return legalBasis;
    }

    public void setLegalBasis(String legalBasis) {
        this.legalBasis = legalBasis;
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

    public List<PayrollRuleBracket> getBrackets() {
        return brackets;
    }

    public void addBracket(PayrollRuleBracket bracket) {
        bracket.setMilestone(this);
        bracket.setPosition(brackets.size());
        brackets.add(bracket);
    }
}

package vn.doit.hrm.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.SalaryBasis;

/**
 * A SalaryChange.
 */
@Entity
@Audited
@Table(name = "salary_change")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class SalaryChange implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false)
    private SalaryBasis basis;

    @Column(name = "gross_amount")
    private Long grossAmount;

    @Column(name = "review_cycle_months")
    private Integer reviewCycleMonths;

    @Column(name = "next_review_override")
    private LocalDate nextReviewOverride;

    @Size(max = 500)
    @Column(name = "note", length = 500)
    private String note;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "department", "manager", "user" }, allowSetters = true)
    private Employee employee;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public SalaryChange id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getEffectiveDate() {
        return this.effectiveDate;
    }

    public SalaryChange effectiveDate(LocalDate effectiveDate) {
        this.setEffectiveDate(effectiveDate);
        return this;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Long getAmount() {
        return this.amount;
    }

    public SalaryChange amount(Long amount) {
        this.setAmount(amount);
        return this;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public SalaryBasis getBasis() {
        return this.basis;
    }

    public SalaryChange basis(SalaryBasis basis) {
        this.setBasis(basis);
        return this;
    }

    public void setBasis(SalaryBasis basis) {
        this.basis = basis;
    }

    public Long getGrossAmount() {
        return this.grossAmount;
    }

    public SalaryChange grossAmount(Long grossAmount) {
        this.setGrossAmount(grossAmount);
        return this;
    }

    public void setGrossAmount(Long grossAmount) {
        this.grossAmount = grossAmount;
    }

    public Integer getReviewCycleMonths() {
        return this.reviewCycleMonths;
    }

    public SalaryChange reviewCycleMonths(Integer reviewCycleMonths) {
        this.setReviewCycleMonths(reviewCycleMonths);
        return this;
    }

    public void setReviewCycleMonths(Integer reviewCycleMonths) {
        this.reviewCycleMonths = reviewCycleMonths;
    }

    public LocalDate getNextReviewOverride() {
        return this.nextReviewOverride;
    }

    public SalaryChange nextReviewOverride(LocalDate nextReviewOverride) {
        this.setNextReviewOverride(nextReviewOverride);
        return this;
    }

    public void setNextReviewOverride(LocalDate nextReviewOverride) {
        this.nextReviewOverride = nextReviewOverride;
    }

    public String getNote() {
        return this.note;
    }

    public SalaryChange note(String note) {
        this.setNote(note);
        return this;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Employee getEmployee() {
        return this.employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public SalaryChange employee(Employee employee) {
        this.setEmployee(employee);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SalaryChange)) {
            return false;
        }
        return getId() != null && getId().equals(((SalaryChange) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "SalaryChange{" +
            "id=" + getId() +
            ", effectiveDate='" + getEffectiveDate() + "'" +
            ", amount=" + getAmount() +
            ", basis='" + getBasis() + "'" +
            ", grossAmount=" + getGrossAmount() +
            ", reviewCycleMonths=" + getReviewCycleMonths() +
            ", nextReviewOverride='" + getNextReviewOverride() + "'" +
            ", note='" + getNote() + "'" +
            "}";
    }
}

package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import vn.doit.hrm.domain.enumeration.SalaryBasis;

/**
 * A DTO for the {@link vn.doit.hrm.domain.SalaryChange} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class SalaryChangeDTO implements Serializable {

    private Long id;

    @NotNull
    private LocalDate effectiveDate;

    @NotNull
    private Long amount;

    @NotNull
    private SalaryBasis basis;

    private Long grossAmount;

    private Integer reviewCycleMonths;

    private LocalDate nextReviewOverride;

    @Size(max = 500)
    private String note;

    @NotNull
    private EmployeeDTO employee;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public SalaryBasis getBasis() {
        return basis;
    }

    public void setBasis(SalaryBasis basis) {
        this.basis = basis;
    }

    public Long getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(Long grossAmount) {
        this.grossAmount = grossAmount;
    }

    public Integer getReviewCycleMonths() {
        return reviewCycleMonths;
    }

    public void setReviewCycleMonths(Integer reviewCycleMonths) {
        this.reviewCycleMonths = reviewCycleMonths;
    }

    public LocalDate getNextReviewOverride() {
        return nextReviewOverride;
    }

    public void setNextReviewOverride(LocalDate nextReviewOverride) {
        this.nextReviewOverride = nextReviewOverride;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public EmployeeDTO getEmployee() {
        return employee;
    }

    public void setEmployee(EmployeeDTO employee) {
        this.employee = employee;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SalaryChangeDTO)) {
            return false;
        }

        SalaryChangeDTO salaryChangeDTO = (SalaryChangeDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, salaryChangeDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "SalaryChangeDTO{" +
            "id=" + getId() +
            ", effectiveDate='" + getEffectiveDate() + "'" +
            ", amount=" + getAmount() +
            ", basis='" + getBasis() + "'" +
            ", grossAmount=" + getGrossAmount() +
            ", reviewCycleMonths=" + getReviewCycleMonths() +
            ", nextReviewOverride='" + getNextReviewOverride() + "'" +
            ", note='" + getNote() + "'" +
            ", employee=" + getEmployee() +
            "}";
    }
}

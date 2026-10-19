package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A DTO for the {@link vn.doit.hrm.domain.Allowance} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AllowanceDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(max = 100)
    private String name;

    @NotNull
    private Long amountPerMonth;

    @NotNull
    private LocalDate fromDate;

    private LocalDate toDate;

    @NotNull
    private EmployeeDTO employee;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAmountPerMonth() {
        return amountPerMonth;
    }

    public void setAmountPerMonth(Long amountPerMonth) {
        this.amountPerMonth = amountPerMonth;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
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
        if (!(o instanceof AllowanceDTO)) {
            return false;
        }

        AllowanceDTO allowanceDTO = (AllowanceDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, allowanceDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AllowanceDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", amountPerMonth=" + getAmountPerMonth() +
            ", fromDate='" + getFromDate() + "'" +
            ", toDate='" + getToDate() + "'" +
            ", employee=" + getEmployee() +
            "}";
    }
}

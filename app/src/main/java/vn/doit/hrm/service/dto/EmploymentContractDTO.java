package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;

/**
 * A DTO for the {@link vn.doit.hrm.domain.EmploymentContract} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class EmploymentContractDTO implements Serializable {

    private Long id;

    @Size(max = 50)
    private String contractNo;

    @NotNull
    private ContractType type;

    private Integer termMonths;

    @NotNull
    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 100)
    private String position;

    private Long salary;

    private SalaryBasis salaryBasis;

    private Long grossAmount;

    @Size(max = 300)
    private String raisePolicy;

    @NotNull
    private ContractStatus status;

    private Long signedFileId;

    @NotNull
    private EmployeeDTO employee;

    private DepartmentDTO department;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContractNo() {
        return contractNo;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    public ContractType getType() {
        return type;
    }

    public void setType(ContractType type) {
        this.type = type;
    }

    public Integer getTermMonths() {
        return termMonths;
    }

    public void setTermMonths(Integer termMonths) {
        this.termMonths = termMonths;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Long getSalary() {
        return salary;
    }

    public void setSalary(Long salary) {
        this.salary = salary;
    }

    public SalaryBasis getSalaryBasis() {
        return salaryBasis;
    }

    public void setSalaryBasis(SalaryBasis salaryBasis) {
        this.salaryBasis = salaryBasis;
    }

    public Long getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(Long grossAmount) {
        this.grossAmount = grossAmount;
    }

    public String getRaisePolicy() {
        return raisePolicy;
    }

    public void setRaisePolicy(String raisePolicy) {
        this.raisePolicy = raisePolicy;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }

    public Long getSignedFileId() {
        return signedFileId;
    }

    public void setSignedFileId(Long signedFileId) {
        this.signedFileId = signedFileId;
    }

    public EmployeeDTO getEmployee() {
        return employee;
    }

    public void setEmployee(EmployeeDTO employee) {
        this.employee = employee;
    }

    public DepartmentDTO getDepartment() {
        return department;
    }

    public void setDepartment(DepartmentDTO department) {
        this.department = department;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmploymentContractDTO)) {
            return false;
        }

        EmploymentContractDTO employmentContractDTO = (EmploymentContractDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, employmentContractDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "EmploymentContractDTO{" +
            "id=" + getId() +
            ", contractNo='" + getContractNo() + "'" +
            ", type='" + getType() + "'" +
            ", termMonths=" + getTermMonths() +
            ", startDate='" + getStartDate() + "'" +
            ", endDate='" + getEndDate() + "'" +
            ", position='" + getPosition() + "'" +
            ", salary=" + getSalary() +
            ", salaryBasis='" + getSalaryBasis() + "'" +
            ", grossAmount=" + getGrossAmount() +
            ", raisePolicy='" + getRaisePolicy() + "'" +
            ", status='" + getStatus() + "'" +
            ", signedFileId=" + getSignedFileId() +
            ", employee=" + getEmployee() +
            ", department=" + getDepartment() +
            "}";
    }
}

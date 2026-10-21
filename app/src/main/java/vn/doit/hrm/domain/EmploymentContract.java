package vn.doit.hrm.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;

/**
 * A EmploymentContract.
 */
@Entity
@Audited
@Table(name = "employment_contract")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class EmploymentContract implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Size(max = 50)
    @Column(name = "contract_no", length = 50)
    private String contractNo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ContractType type;

    @Column(name = "term_months")
    private Integer termMonths;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Size(max = 100)
    @Column(name = "position", length = 100)
    private String position;

    @Column(name = "salary")
    private Long salary;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_basis")
    private SalaryBasis salaryBasis;

    @Column(name = "gross_amount")
    private Long grossAmount;

    @Size(max = 300)
    @Column(name = "raise_policy", length = 300)
    private String raisePolicy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContractStatus status;

    @Column(name = "signed_file_id")
    private Long signedFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "probation_result", length = 20)
    private vn.doit.hrm.domain.enumeration.ProbationResult probationResult;

    @Column(name = "probation_result_date")
    private LocalDate probationResultDate;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "department", "manager", "user" }, allowSetters = true)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public EmploymentContract id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContractNo() {
        return this.contractNo;
    }

    public EmploymentContract contractNo(String contractNo) {
        this.setContractNo(contractNo);
        return this;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    public ContractType getType() {
        return this.type;
    }

    public EmploymentContract type(ContractType type) {
        this.setType(type);
        return this;
    }

    public void setType(ContractType type) {
        this.type = type;
    }

    public Integer getTermMonths() {
        return this.termMonths;
    }

    public EmploymentContract termMonths(Integer termMonths) {
        this.setTermMonths(termMonths);
        return this;
    }

    public void setTermMonths(Integer termMonths) {
        this.termMonths = termMonths;
    }

    public LocalDate getStartDate() {
        return this.startDate;
    }

    public EmploymentContract startDate(LocalDate startDate) {
        this.setStartDate(startDate);
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return this.endDate;
    }

    public EmploymentContract endDate(LocalDate endDate) {
        this.setEndDate(endDate);
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getPosition() {
        return this.position;
    }

    public EmploymentContract position(String position) {
        this.setPosition(position);
        return this;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Long getSalary() {
        return this.salary;
    }

    public EmploymentContract salary(Long salary) {
        this.setSalary(salary);
        return this;
    }

    public void setSalary(Long salary) {
        this.salary = salary;
    }

    public SalaryBasis getSalaryBasis() {
        return this.salaryBasis;
    }

    public EmploymentContract salaryBasis(SalaryBasis salaryBasis) {
        this.setSalaryBasis(salaryBasis);
        return this;
    }

    public void setSalaryBasis(SalaryBasis salaryBasis) {
        this.salaryBasis = salaryBasis;
    }

    public Long getGrossAmount() {
        return this.grossAmount;
    }

    public EmploymentContract grossAmount(Long grossAmount) {
        this.setGrossAmount(grossAmount);
        return this;
    }

    public void setGrossAmount(Long grossAmount) {
        this.grossAmount = grossAmount;
    }

    public String getRaisePolicy() {
        return this.raisePolicy;
    }

    public EmploymentContract raisePolicy(String raisePolicy) {
        this.setRaisePolicy(raisePolicy);
        return this;
    }

    public void setRaisePolicy(String raisePolicy) {
        this.raisePolicy = raisePolicy;
    }

    public ContractStatus getStatus() {
        return this.status;
    }

    public EmploymentContract status(ContractStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }

    public Long getSignedFileId() {
        return this.signedFileId;
    }

    public EmploymentContract signedFileId(Long signedFileId) {
        this.setSignedFileId(signedFileId);
        return this;
    }

    public void setSignedFileId(Long signedFileId) {
        this.signedFileId = signedFileId;
    }

    public Employee getEmployee() {
        return this.employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public EmploymentContract employee(Employee employee) {
        this.setEmployee(employee);
        return this;
    }

    public Department getDepartment() {
        return this.department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public EmploymentContract department(Department department) {
        this.setDepartment(department);
        return this;
    }

    public vn.doit.hrm.domain.enumeration.ProbationResult getProbationResult() {
        return this.probationResult;
    }

    public EmploymentContract probationResult(vn.doit.hrm.domain.enumeration.ProbationResult probationResult) {
        this.setProbationResult(probationResult);
        return this;
    }

    public void setProbationResult(vn.doit.hrm.domain.enumeration.ProbationResult probationResult) {
        this.probationResult = probationResult;
    }

    public LocalDate getProbationResultDate() {
        return this.probationResultDate;
    }

    public EmploymentContract probationResultDate(LocalDate probationResultDate) {
        this.setProbationResultDate(probationResultDate);
        return this;
    }

    public void setProbationResultDate(LocalDate probationResultDate) {
        this.probationResultDate = probationResultDate;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmploymentContract)) {
            return false;
        }
        return getId() != null && getId().equals(((EmploymentContract) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "EmploymentContract{" +
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
            "}";
    }
}

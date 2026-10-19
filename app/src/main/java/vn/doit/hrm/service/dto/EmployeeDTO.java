package vn.doit.hrm.service.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.Gender;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationType;

/**
 * A DTO for the {@link vn.doit.hrm.domain.Employee} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class EmployeeDTO implements Serializable {

    private Long id;

    @Size(max = 20)
    private String code;

    @NotNull
    private PersonType personType;

    @NotNull
    @Size(max = 100)
    private String fullName;

    @Size(max = 400)
    private String searchText;

    private Gender gender;

    private LocalDate birthDate;

    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String phone;

    @Size(max = 300)
    private String residence;

    @Size(max = 100)
    private String education;

    @Size(max = 20)
    private String idNumber;

    private LocalDate idIssueDate;

    @Size(max = 200)
    private String idIssuePlace;

    @Size(max = 20)
    private String taxCode;

    @Size(max = 30)
    private String bankAccount;

    @Size(max = 100)
    private String bankName;

    @Size(max = 20)
    private String socialInsuranceNo;

    private LocalDate socialInsuranceStart;

    private Long socialInsuranceSalary;

    @Min(value = 0)
    private Integer dependants;

    @Size(max = 100)
    private String position;

    private EmploymentStatus status;

    private LocalDate joinDate;

    private LocalDate lastWorkingDay;

    private TerminationType terminationType;

    @Size(max = 500)
    private String terminationReason;

    @Lob
    private String note;

    private DepartmentDTO department;

    private EmployeeDTO manager;

    private UserDTO user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public PersonType getPersonType() {
        return personType;
    }

    public void setPersonType(PersonType personType) {
        this.personType = personType;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getResidence() {
        return residence;
    }

    public void setResidence(String residence) {
        this.residence = residence;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public LocalDate getIdIssueDate() {
        return idIssueDate;
    }

    public void setIdIssueDate(LocalDate idIssueDate) {
        this.idIssueDate = idIssueDate;
    }

    public String getIdIssuePlace() {
        return idIssuePlace;
    }

    public void setIdIssuePlace(String idIssuePlace) {
        this.idIssuePlace = idIssuePlace;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getSocialInsuranceNo() {
        return socialInsuranceNo;
    }

    public void setSocialInsuranceNo(String socialInsuranceNo) {
        this.socialInsuranceNo = socialInsuranceNo;
    }

    public LocalDate getSocialInsuranceStart() {
        return socialInsuranceStart;
    }

    public void setSocialInsuranceStart(LocalDate socialInsuranceStart) {
        this.socialInsuranceStart = socialInsuranceStart;
    }

    public Long getSocialInsuranceSalary() {
        return socialInsuranceSalary;
    }

    public void setSocialInsuranceSalary(Long socialInsuranceSalary) {
        this.socialInsuranceSalary = socialInsuranceSalary;
    }

    public Integer getDependants() {
        return dependants;
    }

    public void setDependants(Integer dependants) {
        this.dependants = dependants;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public EmploymentStatus getStatus() {
        return status;
    }

    public void setStatus(EmploymentStatus status) {
        this.status = status;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public LocalDate getLastWorkingDay() {
        return lastWorkingDay;
    }

    public void setLastWorkingDay(LocalDate lastWorkingDay) {
        this.lastWorkingDay = lastWorkingDay;
    }

    public TerminationType getTerminationType() {
        return terminationType;
    }

    public void setTerminationType(TerminationType terminationType) {
        this.terminationType = terminationType;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public DepartmentDTO getDepartment() {
        return department;
    }

    public void setDepartment(DepartmentDTO department) {
        this.department = department;
    }

    public EmployeeDTO getManager() {
        return manager;
    }

    public void setManager(EmployeeDTO manager) {
        this.manager = manager;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmployeeDTO)) {
            return false;
        }

        EmployeeDTO employeeDTO = (EmployeeDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, employeeDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "EmployeeDTO{" +
            "id=" + getId() +
            ", code='" + getCode() + "'" +
            ", personType='" + getPersonType() + "'" +
            ", fullName='" + getFullName() + "'" +
            ", searchText='" + getSearchText() + "'" +
            ", gender='" + getGender() + "'" +
            ", birthDate='" + getBirthDate() + "'" +
            ", email='" + getEmail() + "'" +
            ", phone='" + getPhone() + "'" +
            ", residence='" + getResidence() + "'" +
            ", education='" + getEducation() + "'" +
            ", idNumber='" + getIdNumber() + "'" +
            ", idIssueDate='" + getIdIssueDate() + "'" +
            ", idIssuePlace='" + getIdIssuePlace() + "'" +
            ", taxCode='" + getTaxCode() + "'" +
            ", bankAccount='" + getBankAccount() + "'" +
            ", bankName='" + getBankName() + "'" +
            ", socialInsuranceNo='" + getSocialInsuranceNo() + "'" +
            ", socialInsuranceStart='" + getSocialInsuranceStart() + "'" +
            ", socialInsuranceSalary=" + getSocialInsuranceSalary() +
            ", dependants=" + getDependants() +
            ", position='" + getPosition() + "'" +
            ", status='" + getStatus() + "'" +
            ", joinDate='" + getJoinDate() + "'" +
            ", lastWorkingDay='" + getLastWorkingDay() + "'" +
            ", terminationType='" + getTerminationType() + "'" +
            ", terminationReason='" + getTerminationReason() + "'" +
            ", note='" + getNote() + "'" +
            ", department=" + getDepartment() +
            ", manager=" + getManager() +
            ", user=" + getUser() +
            "}";
    }
}

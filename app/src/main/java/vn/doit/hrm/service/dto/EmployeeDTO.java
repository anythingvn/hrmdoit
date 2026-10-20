package vn.doit.hrm.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String idNumber;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private LocalDate idIssueDate;

    @Size(max = 200)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String idIssuePlace;

    @Size(max = 20)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String taxCode;

    @Size(max = 30)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String bankAccount;

    @Size(max = 100)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String bankName;

    @Size(max = 20)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String socialInsuranceNo;

    private LocalDate socialInsuranceStart;

    @Min(value = 0, message = "Số tiền không được âm")
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

    /** Ngày nghỉ việc; chỉ đọc qua DTO, đặt bằng /terminate (PUT/PATCH giữ nguyên). */
    private LocalDate terminationDate;

    @Lob
    private String note;

    private DepartmentDTO department;

    private EmployeeDTO manager;

    private UserDTO user;

    /** Chỉ có khi xem chi tiết (GET /api/employees/{id}). */
    private vn.doit.hrm.service.CurrentSalary currentSalary;

    private vn.doit.hrm.domain.enumeration.ContractType currentContractType;

    /** Nơi làm việc hiện tại (chỉ đọc): tên site, hoặc đơn vị, hoặc nhãn loại; trống nếu không có hoặc đã nghỉ. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String currentSiteName;

    /** Tên đăng nhập của tài khoản gắn với hồ sơ (chỉ đọc; đổi qua /api/employees/{id}/account). */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String userLogin;

    /** Ngày làm việc cuối của lịch nghỉ việc đã hẹn (chưa áp dụng); null nếu không có (nhãn "Sắp nghỉ"). Chỉ đọc. */
    private LocalDate scheduledTermination;

    /** Lịch nghỉ việc quá hạn mà chưa áp dụng được (job đã báo lỗi, hoặc đã quá hạn trọn một ngày). Chỉ đọc. */
    private Boolean scheduledTerminationOverdue;

    /** Lý do job chưa áp dụng được lịch nghỉ việc quá hạn (nếu có). Chỉ đọc. */
    private String scheduledTerminationError;

    /** Ghi chú sau thao tác nghỉ việc (vd hoàn tác nhưng không mở lại tài khoản đã bỏ gắn). Chỉ có trong phản hồi của thao tác. */
    private String terminationNotice;

    public Boolean getScheduledTerminationOverdue() {
        return scheduledTerminationOverdue;
    }

    public void setScheduledTerminationOverdue(Boolean scheduledTerminationOverdue) {
        this.scheduledTerminationOverdue = scheduledTerminationOverdue;
    }

    public String getScheduledTerminationError() {
        return scheduledTerminationError;
    }

    public void setScheduledTerminationError(String scheduledTerminationError) {
        this.scheduledTerminationError = scheduledTerminationError;
    }

    public String getTerminationNotice() {
        return terminationNotice;
    }

    public void setTerminationNotice(String terminationNotice) {
        this.terminationNotice = terminationNotice;
    }

    /** Có lần cho nghỉ việc đã áp dụng để hoàn tác (chỉ có ở trang chi tiết). Chỉ đọc. */
    private Boolean terminationUndoable;

    public LocalDate getScheduledTermination() {
        return scheduledTermination;
    }

    public void setScheduledTermination(LocalDate scheduledTermination) {
        this.scheduledTermination = scheduledTermination;
    }

    public Boolean getTerminationUndoable() {
        return terminationUndoable;
    }

    public void setTerminationUndoable(Boolean terminationUndoable) {
        this.terminationUndoable = terminationUndoable;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String getCurrentSiteName() {
        return currentSiteName;
    }

    public void setCurrentSiteName(String currentSiteName) {
        this.currentSiteName = currentSiteName;
    }

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

    public LocalDate getTerminationDate() {
        return terminationDate;
    }

    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = terminationDate;
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

    public vn.doit.hrm.service.CurrentSalary getCurrentSalary() {
        return currentSalary;
    }

    public void setCurrentSalary(vn.doit.hrm.service.CurrentSalary currentSalary) {
        this.currentSalary = currentSalary;
    }

    public vn.doit.hrm.domain.enumeration.ContractType getCurrentContractType() {
        return currentContractType;
    }

    public void setCurrentContractType(vn.doit.hrm.domain.enumeration.ContractType currentContractType) {
        this.currentContractType = currentContractType;
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
            ", socialInsuranceStart='" + getSocialInsuranceStart() + "'" +
            ", socialInsuranceSalary=" + getSocialInsuranceSalary() +
            ", dependants=" + getDependants() +
            ", position='" + getPosition() + "'" +
            ", status='" + getStatus() + "'" +
            ", joinDate='" + getJoinDate() + "'" +
            ", lastWorkingDay='" + getLastWorkingDay() + "'" +
            ", terminationType='" + getTerminationType() + "'" +
            ", terminationReason='" + getTerminationReason() + "'" +
            ", terminationDate='" + getTerminationDate() + "'" +
            ", note='" + getNote() + "'" +
            ", department=" + getDepartment() +
            ", manager=" + getManager() +
            ", user=" + getUser() +
            "}";
    }
}

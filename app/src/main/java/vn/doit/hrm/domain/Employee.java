package vn.doit.hrm.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.Gender;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationType;

/**
 * A Employee.
 */
@Entity
@Audited
@Table(name = "employee")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Employee implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 20)
    @Column(name = "code", length = 20, nullable = false, unique = true)
    private String code;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false)
    private PersonType personType;

    @NotNull
    @Size(max = 100)
    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    @Size(max = 400)
    @Column(name = "search_text", length = 400)
    private String searchText;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;

    @Size(max = 20)
    @Column(name = "phone", length = 20)
    private String phone;

    @Size(max = 300)
    @Column(name = "residence", length = 300)
    private String residence;

    @Size(max = 100)
    @Column(name = "education", length = 100)
    private String education;

    @Size(max = 20)
    @Column(name = "id_number", length = 20)
    private String idNumber;

    @Column(name = "id_issue_date")
    private LocalDate idIssueDate;

    @Size(max = 200)
    @Column(name = "id_issue_place", length = 200)
    private String idIssuePlace;

    @Size(max = 20)
    @Column(name = "tax_code", length = 20)
    private String taxCode;

    /** Có bản cam kết 08 (thu nhập thấp, không khấu trừ thuế); null chỉ khi dữ liệu gửi lên bỏ trống, khi lưu được thay. */
    @Column(name = "pit_commitment_08", nullable = false)
    private Boolean pitCommitment08 = Boolean.FALSE;

    @Size(max = 30)
    @Column(name = "bank_account", length = 30)
    private String bankAccount;

    @Size(max = 100)
    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Size(max = 20)
    @Column(name = "social_insurance_no", length = 20)
    private String socialInsuranceNo;

    @Column(name = "social_insurance_start")
    private LocalDate socialInsuranceStart;

    @Column(name = "social_insurance_salary")
    private Long socialInsuranceSalary;

    @NotNull
    @Min(value = 0)
    @Column(name = "dependants", nullable = false)
    private Integer dependants;

    @Size(max = 100)
    @Column(name = "position", length = 100)
    private String position;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmploymentStatus status;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Column(name = "last_working_day")
    private LocalDate lastWorkingDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_type")
    private TerminationType terminationType;

    @Size(max = 500)
    @Column(name = "termination_reason", length = 500)
    private String terminationReason;

    /** Ngày nghỉ việc (Excel cột AQ), thường là ngày sau ngày làm việc cuối; chỉ đặt qua /terminate. */
    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Lob
    @Column(name = "note")
    private String note;

    /** Lý do không thuộc diện đóng BHXH bắt buộc; null = thuộc diện đóng. */
    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_exemption", length = 30)
    private vn.doit.hrm.domain.enumeration.InsuranceExemption insuranceExemption;

    @Column(name = "insurance_exemption_note", length = 500)
    private String insuranceExemptionNote;

    /** Mức phép năm gốc (12/14/16 ngày), chưa cộng thâm niên. */
    @NotNull
    @Column(name = "annual_leave_days", nullable = false)
    private Integer annualLeaveDays = 12;

    /** Số năm đóng BHXH trước khi vào công ty (tính hạn mức nghỉ ốm). */
    @NotNull
    @Column(name = "prior_social_insurance_years", nullable = false)
    private Integer priorSocialInsuranceYears = 0;

    /** Thời điểm đưa vào lưu trữ; null = hồ sơ đang dùng. Hồ sơ đã lưu trữ chỉ xem, không sửa. */
    @Column(name = "archived_at")
    private Instant archivedAt;

    @Size(max = 50)
    @Column(name = "archived_by", length = 50)
    private String archivedBy;

    @Size(max = 500)
    @Column(name = "archive_note", length = 500)
    private String archiveNote;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "department", "manager", "user" }, allowSetters = true)
    private Employee manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private User user;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Instant getArchivedAt() {
        return this.archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public String getArchivedBy() {
        return this.archivedBy;
    }

    public void setArchivedBy(String archivedBy) {
        this.archivedBy = archivedBy;
    }

    public String getArchiveNote() {
        return this.archiveNote;
    }

    public void setArchiveNote(String archiveNote) {
        this.archiveNote = archiveNote;
    }

    public boolean isArchived() {
        return this.archivedAt != null;
    }

    public Long getId() {
        return this.id;
    }

    public Employee id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return this.code;
    }

    public Employee code(String code) {
        this.setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public PersonType getPersonType() {
        return this.personType;
    }

    public Employee personType(PersonType personType) {
        this.setPersonType(personType);
        return this;
    }

    public void setPersonType(PersonType personType) {
        this.personType = personType;
    }

    public String getFullName() {
        return this.fullName;
    }

    public Employee fullName(String fullName) {
        this.setFullName(fullName);
        return this;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getSearchText() {
        return this.searchText;
    }

    public Employee searchText(String searchText) {
        this.setSearchText(searchText);
        return this;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public Gender getGender() {
        return this.gender;
    }

    public Employee gender(Gender gender) {
        this.setGender(gender);
        return this;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return this.birthDate;
    }

    public Employee birthDate(LocalDate birthDate) {
        this.setBirthDate(birthDate);
        return this;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getEmail() {
        return this.email;
    }

    public Employee email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return this.phone;
    }

    public Employee phone(String phone) {
        this.setPhone(phone);
        return this;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getResidence() {
        return this.residence;
    }

    public Employee residence(String residence) {
        this.setResidence(residence);
        return this;
    }

    public void setResidence(String residence) {
        this.residence = residence;
    }

    public String getEducation() {
        return this.education;
    }

    public Employee education(String education) {
        this.setEducation(education);
        return this;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getIdNumber() {
        return this.idNumber;
    }

    public Employee idNumber(String idNumber) {
        this.setIdNumber(idNumber);
        return this;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public LocalDate getIdIssueDate() {
        return this.idIssueDate;
    }

    public Employee idIssueDate(LocalDate idIssueDate) {
        this.setIdIssueDate(idIssueDate);
        return this;
    }

    public void setIdIssueDate(LocalDate idIssueDate) {
        this.idIssueDate = idIssueDate;
    }

    public String getIdIssuePlace() {
        return this.idIssuePlace;
    }

    public Employee idIssuePlace(String idIssuePlace) {
        this.setIdIssuePlace(idIssuePlace);
        return this;
    }

    public void setIdIssuePlace(String idIssuePlace) {
        this.idIssuePlace = idIssuePlace;
    }

    public String getTaxCode() {
        return this.taxCode;
    }

    public Employee taxCode(String taxCode) {
        this.setTaxCode(taxCode);
        return this;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    public Boolean getPitCommitment08() {
        return this.pitCommitment08;
    }

    public void setPitCommitment08(Boolean pitCommitment08) {
        this.pitCommitment08 = pitCommitment08;
    }

    public String getBankAccount() {
        return this.bankAccount;
    }

    public Employee bankAccount(String bankAccount) {
        this.setBankAccount(bankAccount);
        return this;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getBankName() {
        return this.bankName;
    }

    public Employee bankName(String bankName) {
        this.setBankName(bankName);
        return this;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getSocialInsuranceNo() {
        return this.socialInsuranceNo;
    }

    public Employee socialInsuranceNo(String socialInsuranceNo) {
        this.setSocialInsuranceNo(socialInsuranceNo);
        return this;
    }

    public void setSocialInsuranceNo(String socialInsuranceNo) {
        this.socialInsuranceNo = socialInsuranceNo;
    }

    public LocalDate getSocialInsuranceStart() {
        return this.socialInsuranceStart;
    }

    public Employee socialInsuranceStart(LocalDate socialInsuranceStart) {
        this.setSocialInsuranceStart(socialInsuranceStart);
        return this;
    }

    public void setSocialInsuranceStart(LocalDate socialInsuranceStart) {
        this.socialInsuranceStart = socialInsuranceStart;
    }

    public Long getSocialInsuranceSalary() {
        return this.socialInsuranceSalary;
    }

    public Employee socialInsuranceSalary(Long socialInsuranceSalary) {
        this.setSocialInsuranceSalary(socialInsuranceSalary);
        return this;
    }

    public void setSocialInsuranceSalary(Long socialInsuranceSalary) {
        this.socialInsuranceSalary = socialInsuranceSalary;
    }

    public Integer getDependants() {
        return this.dependants;
    }

    public Employee dependants(Integer dependants) {
        this.setDependants(dependants);
        return this;
    }

    public void setDependants(Integer dependants) {
        this.dependants = dependants;
    }

    public String getPosition() {
        return this.position;
    }

    public Employee position(String position) {
        this.setPosition(position);
        return this;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public EmploymentStatus getStatus() {
        return this.status;
    }

    public Employee status(EmploymentStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(EmploymentStatus status) {
        this.status = status;
    }

    public LocalDate getJoinDate() {
        return this.joinDate;
    }

    public Employee joinDate(LocalDate joinDate) {
        this.setJoinDate(joinDate);
        return this;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public LocalDate getLastWorkingDay() {
        return this.lastWorkingDay;
    }

    public Employee lastWorkingDay(LocalDate lastWorkingDay) {
        this.setLastWorkingDay(lastWorkingDay);
        return this;
    }

    public void setLastWorkingDay(LocalDate lastWorkingDay) {
        this.lastWorkingDay = lastWorkingDay;
    }

    public TerminationType getTerminationType() {
        return this.terminationType;
    }

    public Employee terminationType(TerminationType terminationType) {
        this.setTerminationType(terminationType);
        return this;
    }

    public void setTerminationType(TerminationType terminationType) {
        this.terminationType = terminationType;
    }

    public String getTerminationReason() {
        return this.terminationReason;
    }

    public Employee terminationReason(String terminationReason) {
        this.setTerminationReason(terminationReason);
        return this;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public LocalDate getTerminationDate() {
        return this.terminationDate;
    }

    public Employee terminationDate(LocalDate terminationDate) {
        this.setTerminationDate(terminationDate);
        return this;
    }

    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = terminationDate;
    }

    public String getNote() {
        return this.note;
    }

    public Employee note(String note) {
        this.setNote(note);
        return this;
    }

    public vn.doit.hrm.domain.enumeration.InsuranceExemption getInsuranceExemption() {
        return this.insuranceExemption;
    }

    public void setInsuranceExemption(vn.doit.hrm.domain.enumeration.InsuranceExemption insuranceExemption) {
        this.insuranceExemption = insuranceExemption;
    }

    public Employee insuranceExemption(vn.doit.hrm.domain.enumeration.InsuranceExemption insuranceExemption) {
        this.setInsuranceExemption(insuranceExemption);
        return this;
    }

    public String getInsuranceExemptionNote() {
        return this.insuranceExemptionNote;
    }

    public void setInsuranceExemptionNote(String insuranceExemptionNote) {
        this.insuranceExemptionNote = insuranceExemptionNote;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Department getDepartment() {
        return this.department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Employee department(Department department) {
        this.setDepartment(department);
        return this;
    }

    public Employee getManager() {
        return this.manager;
    }

    public void setManager(Employee employee) {
        this.manager = employee;
    }

    public Employee manager(Employee employee) {
        this.setManager(employee);
        return this;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Employee user(User user) {
        this.setUser(user);
        return this;
    }

    public Integer getAnnualLeaveDays() {
        return annualLeaveDays;
    }

    public void setAnnualLeaveDays(Integer annualLeaveDays) {
        this.annualLeaveDays = annualLeaveDays;
    }

    public Integer getPriorSocialInsuranceYears() {
        return priorSocialInsuranceYears;
    }

    public void setPriorSocialInsuranceYears(Integer priorSocialInsuranceYears) {
        this.priorSocialInsuranceYears = priorSocialInsuranceYears;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Employee)) {
            return false;
        }
        return getId() != null && getId().equals(((Employee) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    /** Không in các trường nhạy cảm (CCCD, MST, tài khoản ngân hàng, số BHXH) để không lọt vào log. */
    // prettier-ignore
    @Override
    public String toString() {
        return "Employee{" +
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
            "}";
    }
}

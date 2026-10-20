package vn.doit.hrm.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.Gender;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationType;

/**
 * Criteria class for the {@link vn.doit.hrm.domain.Employee} entity. This class is used
 * in {@link vn.doit.hrm.web.rest.EmployeeResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /employees?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class EmployeeCriteria implements Serializable, Criteria {

    /**
     * Class for filtering PersonType
     */
    public static class PersonTypeFilter extends Filter<PersonType> {

        public PersonTypeFilter() {}

        public PersonTypeFilter(PersonTypeFilter filter) {
            super(filter);
        }

        @Override
        public PersonTypeFilter copy() {
            return new PersonTypeFilter(this);
        }
    }

    /**
     * Class for filtering Gender
     */
    public static class GenderFilter extends Filter<Gender> {

        public GenderFilter() {}

        public GenderFilter(GenderFilter filter) {
            super(filter);
        }

        @Override
        public GenderFilter copy() {
            return new GenderFilter(this);
        }
    }

    /**
     * Class for filtering EmploymentStatus
     */
    public static class EmploymentStatusFilter extends Filter<EmploymentStatus> {

        public EmploymentStatusFilter() {}

        public EmploymentStatusFilter(EmploymentStatusFilter filter) {
            super(filter);
        }

        @Override
        public EmploymentStatusFilter copy() {
            return new EmploymentStatusFilter(this);
        }
    }

    /**
     * Class for filtering TerminationType
     */
    public static class TerminationTypeFilter extends Filter<TerminationType> {

        public TerminationTypeFilter() {}

        public TerminationTypeFilter(TerminationTypeFilter filter) {
            super(filter);
        }

        @Override
        public TerminationTypeFilter copy() {
            return new TerminationTypeFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter code;

    private PersonTypeFilter personType;

    private StringFilter fullName;

    private StringFilter searchText;

    private GenderFilter gender;

    private LocalDateFilter birthDate;

    private StringFilter email;

    private StringFilter phone;

    private StringFilter residence;

    private StringFilter education;

    private StringFilter idNumber;

    private LocalDateFilter idIssueDate;

    private StringFilter idIssuePlace;

    private StringFilter taxCode;

    private StringFilter bankAccount;

    private StringFilter bankName;

    private StringFilter socialInsuranceNo;

    private LocalDateFilter socialInsuranceStart;

    private LongFilter socialInsuranceSalary;

    private IntegerFilter dependants;

    private StringFilter position;

    private EmploymentStatusFilter status;

    private LocalDateFilter joinDate;

    private LocalDateFilter lastWorkingDay;

    private TerminationTypeFilter terminationType;

    private StringFilter terminationReason;

    private LongFilter departmentId;

    private LongFilter managerId;

    private LongFilter userId;

    private StringFilter q;

    private LongFilter currentSiteId;

    private StringFilter currentContractType;

    private Boolean distinct;

    public EmployeeCriteria() {}

    public EmployeeCriteria(EmployeeCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.code = other.optionalCode().map(StringFilter::copy).orElse(null);
        this.personType = other.optionalPersonType().map(PersonTypeFilter::copy).orElse(null);
        this.fullName = other.optionalFullName().map(StringFilter::copy).orElse(null);
        this.searchText = other.optionalSearchText().map(StringFilter::copy).orElse(null);
        this.gender = other.optionalGender().map(GenderFilter::copy).orElse(null);
        this.birthDate = other.optionalBirthDate().map(LocalDateFilter::copy).orElse(null);
        this.email = other.optionalEmail().map(StringFilter::copy).orElse(null);
        this.phone = other.optionalPhone().map(StringFilter::copy).orElse(null);
        this.residence = other.optionalResidence().map(StringFilter::copy).orElse(null);
        this.education = other.optionalEducation().map(StringFilter::copy).orElse(null);
        this.idNumber = other.optionalIdNumber().map(StringFilter::copy).orElse(null);
        this.idIssueDate = other.optionalIdIssueDate().map(LocalDateFilter::copy).orElse(null);
        this.idIssuePlace = other.optionalIdIssuePlace().map(StringFilter::copy).orElse(null);
        this.taxCode = other.optionalTaxCode().map(StringFilter::copy).orElse(null);
        this.bankAccount = other.optionalBankAccount().map(StringFilter::copy).orElse(null);
        this.bankName = other.optionalBankName().map(StringFilter::copy).orElse(null);
        this.socialInsuranceNo = other.optionalSocialInsuranceNo().map(StringFilter::copy).orElse(null);
        this.socialInsuranceStart = other.optionalSocialInsuranceStart().map(LocalDateFilter::copy).orElse(null);
        this.socialInsuranceSalary = other.optionalSocialInsuranceSalary().map(LongFilter::copy).orElse(null);
        this.dependants = other.optionalDependants().map(IntegerFilter::copy).orElse(null);
        this.position = other.optionalPosition().map(StringFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(EmploymentStatusFilter::copy).orElse(null);
        this.joinDate = other.optionalJoinDate().map(LocalDateFilter::copy).orElse(null);
        this.lastWorkingDay = other.optionalLastWorkingDay().map(LocalDateFilter::copy).orElse(null);
        this.terminationType = other.optionalTerminationType().map(TerminationTypeFilter::copy).orElse(null);
        this.terminationReason = other.optionalTerminationReason().map(StringFilter::copy).orElse(null);
        this.departmentId = other.optionalDepartmentId().map(LongFilter::copy).orElse(null);
        this.managerId = other.optionalManagerId().map(LongFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(LongFilter::copy).orElse(null);
        this.q = other.q == null ? null : other.q.copy();
        this.currentSiteId = other.currentSiteId == null ? null : other.currentSiteId.copy();
        this.currentContractType = other.currentContractType == null ? null : other.currentContractType.copy();
        this.distinct = other.distinct;
    }

    @Override
    public EmployeeCriteria copy() {
        return new EmployeeCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public StringFilter getCode() {
        return code;
    }

    public Optional<StringFilter> optionalCode() {
        return Optional.ofNullable(code);
    }

    public StringFilter code() {
        if (code == null) {
            setCode(new StringFilter());
        }
        return code;
    }

    public void setCode(StringFilter code) {
        this.code = code;
    }

    public PersonTypeFilter getPersonType() {
        return personType;
    }

    public Optional<PersonTypeFilter> optionalPersonType() {
        return Optional.ofNullable(personType);
    }

    public PersonTypeFilter personType() {
        if (personType == null) {
            setPersonType(new PersonTypeFilter());
        }
        return personType;
    }

    public void setPersonType(PersonTypeFilter personType) {
        this.personType = personType;
    }

    public StringFilter getFullName() {
        return fullName;
    }

    public Optional<StringFilter> optionalFullName() {
        return Optional.ofNullable(fullName);
    }

    public StringFilter fullName() {
        if (fullName == null) {
            setFullName(new StringFilter());
        }
        return fullName;
    }

    public void setFullName(StringFilter fullName) {
        this.fullName = fullName;
    }

    public StringFilter getSearchText() {
        return searchText;
    }

    public Optional<StringFilter> optionalSearchText() {
        return Optional.ofNullable(searchText);
    }

    public StringFilter searchText() {
        if (searchText == null) {
            setSearchText(new StringFilter());
        }
        return searchText;
    }

    public void setSearchText(StringFilter searchText) {
        this.searchText = searchText;
    }

    public GenderFilter getGender() {
        return gender;
    }

    public Optional<GenderFilter> optionalGender() {
        return Optional.ofNullable(gender);
    }

    public GenderFilter gender() {
        if (gender == null) {
            setGender(new GenderFilter());
        }
        return gender;
    }

    public void setGender(GenderFilter gender) {
        this.gender = gender;
    }

    public LocalDateFilter getBirthDate() {
        return birthDate;
    }

    public Optional<LocalDateFilter> optionalBirthDate() {
        return Optional.ofNullable(birthDate);
    }

    public LocalDateFilter birthDate() {
        if (birthDate == null) {
            setBirthDate(new LocalDateFilter());
        }
        return birthDate;
    }

    public void setBirthDate(LocalDateFilter birthDate) {
        this.birthDate = birthDate;
    }

    public StringFilter getEmail() {
        return email;
    }

    public Optional<StringFilter> optionalEmail() {
        return Optional.ofNullable(email);
    }

    public StringFilter email() {
        if (email == null) {
            setEmail(new StringFilter());
        }
        return email;
    }

    public void setEmail(StringFilter email) {
        this.email = email;
    }

    public StringFilter getPhone() {
        return phone;
    }

    public Optional<StringFilter> optionalPhone() {
        return Optional.ofNullable(phone);
    }

    public StringFilter phone() {
        if (phone == null) {
            setPhone(new StringFilter());
        }
        return phone;
    }

    public void setPhone(StringFilter phone) {
        this.phone = phone;
    }

    public StringFilter getResidence() {
        return residence;
    }

    public Optional<StringFilter> optionalResidence() {
        return Optional.ofNullable(residence);
    }

    public StringFilter residence() {
        if (residence == null) {
            setResidence(new StringFilter());
        }
        return residence;
    }

    public void setResidence(StringFilter residence) {
        this.residence = residence;
    }

    public StringFilter getEducation() {
        return education;
    }

    public Optional<StringFilter> optionalEducation() {
        return Optional.ofNullable(education);
    }

    public StringFilter education() {
        if (education == null) {
            setEducation(new StringFilter());
        }
        return education;
    }

    public void setEducation(StringFilter education) {
        this.education = education;
    }

    public StringFilter getIdNumber() {
        return idNumber;
    }

    public Optional<StringFilter> optionalIdNumber() {
        return Optional.ofNullable(idNumber);
    }

    public StringFilter idNumber() {
        if (idNumber == null) {
            setIdNumber(new StringFilter());
        }
        return idNumber;
    }

    public void setIdNumber(StringFilter idNumber) {
        this.idNumber = idNumber;
    }

    public LocalDateFilter getIdIssueDate() {
        return idIssueDate;
    }

    public Optional<LocalDateFilter> optionalIdIssueDate() {
        return Optional.ofNullable(idIssueDate);
    }

    public LocalDateFilter idIssueDate() {
        if (idIssueDate == null) {
            setIdIssueDate(new LocalDateFilter());
        }
        return idIssueDate;
    }

    public void setIdIssueDate(LocalDateFilter idIssueDate) {
        this.idIssueDate = idIssueDate;
    }

    public StringFilter getIdIssuePlace() {
        return idIssuePlace;
    }

    public Optional<StringFilter> optionalIdIssuePlace() {
        return Optional.ofNullable(idIssuePlace);
    }

    public StringFilter idIssuePlace() {
        if (idIssuePlace == null) {
            setIdIssuePlace(new StringFilter());
        }
        return idIssuePlace;
    }

    public void setIdIssuePlace(StringFilter idIssuePlace) {
        this.idIssuePlace = idIssuePlace;
    }

    public StringFilter getTaxCode() {
        return taxCode;
    }

    public Optional<StringFilter> optionalTaxCode() {
        return Optional.ofNullable(taxCode);
    }

    public StringFilter taxCode() {
        if (taxCode == null) {
            setTaxCode(new StringFilter());
        }
        return taxCode;
    }

    public void setTaxCode(StringFilter taxCode) {
        this.taxCode = taxCode;
    }

    public StringFilter getBankAccount() {
        return bankAccount;
    }

    public Optional<StringFilter> optionalBankAccount() {
        return Optional.ofNullable(bankAccount);
    }

    public StringFilter bankAccount() {
        if (bankAccount == null) {
            setBankAccount(new StringFilter());
        }
        return bankAccount;
    }

    public void setBankAccount(StringFilter bankAccount) {
        this.bankAccount = bankAccount;
    }

    public StringFilter getBankName() {
        return bankName;
    }

    public Optional<StringFilter> optionalBankName() {
        return Optional.ofNullable(bankName);
    }

    public StringFilter bankName() {
        if (bankName == null) {
            setBankName(new StringFilter());
        }
        return bankName;
    }

    public void setBankName(StringFilter bankName) {
        this.bankName = bankName;
    }

    public StringFilter getSocialInsuranceNo() {
        return socialInsuranceNo;
    }

    public Optional<StringFilter> optionalSocialInsuranceNo() {
        return Optional.ofNullable(socialInsuranceNo);
    }

    public StringFilter socialInsuranceNo() {
        if (socialInsuranceNo == null) {
            setSocialInsuranceNo(new StringFilter());
        }
        return socialInsuranceNo;
    }

    public void setSocialInsuranceNo(StringFilter socialInsuranceNo) {
        this.socialInsuranceNo = socialInsuranceNo;
    }

    public LocalDateFilter getSocialInsuranceStart() {
        return socialInsuranceStart;
    }

    public Optional<LocalDateFilter> optionalSocialInsuranceStart() {
        return Optional.ofNullable(socialInsuranceStart);
    }

    public LocalDateFilter socialInsuranceStart() {
        if (socialInsuranceStart == null) {
            setSocialInsuranceStart(new LocalDateFilter());
        }
        return socialInsuranceStart;
    }

    public void setSocialInsuranceStart(LocalDateFilter socialInsuranceStart) {
        this.socialInsuranceStart = socialInsuranceStart;
    }

    public LongFilter getSocialInsuranceSalary() {
        return socialInsuranceSalary;
    }

    public Optional<LongFilter> optionalSocialInsuranceSalary() {
        return Optional.ofNullable(socialInsuranceSalary);
    }

    public LongFilter socialInsuranceSalary() {
        if (socialInsuranceSalary == null) {
            setSocialInsuranceSalary(new LongFilter());
        }
        return socialInsuranceSalary;
    }

    public void setSocialInsuranceSalary(LongFilter socialInsuranceSalary) {
        this.socialInsuranceSalary = socialInsuranceSalary;
    }

    public IntegerFilter getDependants() {
        return dependants;
    }

    public Optional<IntegerFilter> optionalDependants() {
        return Optional.ofNullable(dependants);
    }

    public IntegerFilter dependants() {
        if (dependants == null) {
            setDependants(new IntegerFilter());
        }
        return dependants;
    }

    public void setDependants(IntegerFilter dependants) {
        this.dependants = dependants;
    }

    public StringFilter getPosition() {
        return position;
    }

    public Optional<StringFilter> optionalPosition() {
        return Optional.ofNullable(position);
    }

    public StringFilter position() {
        if (position == null) {
            setPosition(new StringFilter());
        }
        return position;
    }

    public void setPosition(StringFilter position) {
        this.position = position;
    }

    public EmploymentStatusFilter getStatus() {
        return status;
    }

    public Optional<EmploymentStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public EmploymentStatusFilter status() {
        if (status == null) {
            setStatus(new EmploymentStatusFilter());
        }
        return status;
    }

    public void setStatus(EmploymentStatusFilter status) {
        this.status = status;
    }

    public LocalDateFilter getJoinDate() {
        return joinDate;
    }

    public Optional<LocalDateFilter> optionalJoinDate() {
        return Optional.ofNullable(joinDate);
    }

    public LocalDateFilter joinDate() {
        if (joinDate == null) {
            setJoinDate(new LocalDateFilter());
        }
        return joinDate;
    }

    public void setJoinDate(LocalDateFilter joinDate) {
        this.joinDate = joinDate;
    }

    public LocalDateFilter getLastWorkingDay() {
        return lastWorkingDay;
    }

    public Optional<LocalDateFilter> optionalLastWorkingDay() {
        return Optional.ofNullable(lastWorkingDay);
    }

    public LocalDateFilter lastWorkingDay() {
        if (lastWorkingDay == null) {
            setLastWorkingDay(new LocalDateFilter());
        }
        return lastWorkingDay;
    }

    public void setLastWorkingDay(LocalDateFilter lastWorkingDay) {
        this.lastWorkingDay = lastWorkingDay;
    }

    public TerminationTypeFilter getTerminationType() {
        return terminationType;
    }

    public Optional<TerminationTypeFilter> optionalTerminationType() {
        return Optional.ofNullable(terminationType);
    }

    public TerminationTypeFilter terminationType() {
        if (terminationType == null) {
            setTerminationType(new TerminationTypeFilter());
        }
        return terminationType;
    }

    public void setTerminationType(TerminationTypeFilter terminationType) {
        this.terminationType = terminationType;
    }

    public StringFilter getTerminationReason() {
        return terminationReason;
    }

    public Optional<StringFilter> optionalTerminationReason() {
        return Optional.ofNullable(terminationReason);
    }

    public StringFilter terminationReason() {
        if (terminationReason == null) {
            setTerminationReason(new StringFilter());
        }
        return terminationReason;
    }

    public void setTerminationReason(StringFilter terminationReason) {
        this.terminationReason = terminationReason;
    }

    public LongFilter getDepartmentId() {
        return departmentId;
    }

    public Optional<LongFilter> optionalDepartmentId() {
        return Optional.ofNullable(departmentId);
    }

    public LongFilter departmentId() {
        if (departmentId == null) {
            setDepartmentId(new LongFilter());
        }
        return departmentId;
    }

    public void setDepartmentId(LongFilter departmentId) {
        this.departmentId = departmentId;
    }

    public LongFilter getManagerId() {
        return managerId;
    }

    public Optional<LongFilter> optionalManagerId() {
        return Optional.ofNullable(managerId);
    }

    public LongFilter managerId() {
        if (managerId == null) {
            setManagerId(new LongFilter());
        }
        return managerId;
    }

    public void setManagerId(LongFilter managerId) {
        this.managerId = managerId;
    }

    public LongFilter getUserId() {
        return userId;
    }

    public Optional<LongFilter> optionalUserId() {
        return Optional.ofNullable(userId);
    }

    public LongFilter userId() {
        if (userId == null) {
            setUserId(new LongFilter());
        }
        return userId;
    }

    public void setUserId(LongFilter userId) {
        this.userId = userId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    public StringFilter getQ() {
        return q;
    }

    public StringFilter q() {
        if (q == null) {
            q = new StringFilter();
        }
        return q;
    }

    public void setQ(StringFilter q) {
        this.q = q;
    }

    public LongFilter getCurrentSiteId() {
        return currentSiteId;
    }

    public LongFilter currentSiteId() {
        if (currentSiteId == null) {
            currentSiteId = new LongFilter();
        }
        return currentSiteId;
    }

    public void setCurrentSiteId(LongFilter currentSiteId) {
        this.currentSiteId = currentSiteId;
    }

    public StringFilter getCurrentContractType() {
        return currentContractType;
    }

    public StringFilter currentContractType() {
        if (currentContractType == null) {
            currentContractType = new StringFilter();
        }
        return currentContractType;
    }

    public void setCurrentContractType(StringFilter currentContractType) {
        this.currentContractType = currentContractType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EmployeeCriteria that = (EmployeeCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(code, that.code) &&
            Objects.equals(personType, that.personType) &&
            Objects.equals(fullName, that.fullName) &&
            Objects.equals(searchText, that.searchText) &&
            Objects.equals(gender, that.gender) &&
            Objects.equals(birthDate, that.birthDate) &&
            Objects.equals(email, that.email) &&
            Objects.equals(phone, that.phone) &&
            Objects.equals(residence, that.residence) &&
            Objects.equals(education, that.education) &&
            Objects.equals(idNumber, that.idNumber) &&
            Objects.equals(idIssueDate, that.idIssueDate) &&
            Objects.equals(idIssuePlace, that.idIssuePlace) &&
            Objects.equals(taxCode, that.taxCode) &&
            Objects.equals(bankAccount, that.bankAccount) &&
            Objects.equals(bankName, that.bankName) &&
            Objects.equals(socialInsuranceNo, that.socialInsuranceNo) &&
            Objects.equals(socialInsuranceStart, that.socialInsuranceStart) &&
            Objects.equals(socialInsuranceSalary, that.socialInsuranceSalary) &&
            Objects.equals(dependants, that.dependants) &&
            Objects.equals(position, that.position) &&
            Objects.equals(status, that.status) &&
            Objects.equals(joinDate, that.joinDate) &&
            Objects.equals(lastWorkingDay, that.lastWorkingDay) &&
            Objects.equals(terminationType, that.terminationType) &&
            Objects.equals(terminationReason, that.terminationReason) &&
            Objects.equals(departmentId, that.departmentId) &&
            Objects.equals(managerId, that.managerId) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(q, that.q) &&
            Objects.equals(currentSiteId, that.currentSiteId) &&
            Objects.equals(currentContractType, that.currentContractType) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            code,
            personType,
            fullName,
            searchText,
            gender,
            birthDate,
            email,
            phone,
            residence,
            education,
            idNumber,
            idIssueDate,
            idIssuePlace,
            taxCode,
            bankAccount,
            bankName,
            socialInsuranceNo,
            socialInsuranceStart,
            socialInsuranceSalary,
            dependants,
            position,
            status,
            joinDate,
            lastWorkingDay,
            terminationType,
            terminationReason,
            departmentId,
            managerId,
            userId,
            q,
            currentSiteId,
            currentContractType,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "EmployeeCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalCode().map(f -> "code=" + f + ", ").orElse("") +
            optionalPersonType().map(f -> "personType=" + f + ", ").orElse("") +
            optionalFullName().map(f -> "fullName=" + f + ", ").orElse("") +
            optionalSearchText().map(f -> "searchText=" + f + ", ").orElse("") +
            optionalGender().map(f -> "gender=" + f + ", ").orElse("") +
            optionalBirthDate().map(f -> "birthDate=" + f + ", ").orElse("") +
            optionalEmail().map(f -> "email=" + f + ", ").orElse("") +
            optionalPhone().map(f -> "phone=" + f + ", ").orElse("") +
            optionalResidence().map(f -> "residence=" + f + ", ").orElse("") +
            optionalEducation().map(f -> "education=" + f + ", ").orElse("") +
            optionalIdNumber().map(f -> "idNumber=" + f + ", ").orElse("") +
            optionalIdIssueDate().map(f -> "idIssueDate=" + f + ", ").orElse("") +
            optionalIdIssuePlace().map(f -> "idIssuePlace=" + f + ", ").orElse("") +
            optionalTaxCode().map(f -> "taxCode=" + f + ", ").orElse("") +
            optionalBankAccount().map(f -> "bankAccount=" + f + ", ").orElse("") +
            optionalBankName().map(f -> "bankName=" + f + ", ").orElse("") +
            optionalSocialInsuranceNo().map(f -> "socialInsuranceNo=" + f + ", ").orElse("") +
            optionalSocialInsuranceStart().map(f -> "socialInsuranceStart=" + f + ", ").orElse("") +
            optionalSocialInsuranceSalary().map(f -> "socialInsuranceSalary=" + f + ", ").orElse("") +
            optionalDependants().map(f -> "dependants=" + f + ", ").orElse("") +
            optionalPosition().map(f -> "position=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalJoinDate().map(f -> "joinDate=" + f + ", ").orElse("") +
            optionalLastWorkingDay().map(f -> "lastWorkingDay=" + f + ", ").orElse("") +
            optionalTerminationType().map(f -> "terminationType=" + f + ", ").orElse("") +
            optionalTerminationReason().map(f -> "terminationReason=" + f + ", ").orElse("") +
            optionalDepartmentId().map(f -> "departmentId=" + f + ", ").orElse("") +
            optionalManagerId().map(f -> "managerId=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            (q != null ? "q=" + q + ", " : "") +
            (currentSiteId != null ? "currentSiteId=" + currentSiteId + ", " : "") +
            (currentContractType != null ? "currentContractType=" + currentContractType + ", " : "") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}

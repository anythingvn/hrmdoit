package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationType;

/**
 * Một lần làm việc trước của hồ sơ, ghi khi nhận làm lại người đã nghỉ. {@code terminationRecordId} là lần cho nghỉ việc kết thúc lần
 * làm việc đó (null với hồ sơ cũ không có lịch sử nghỉ việc); lần cho nghỉ đã có lần làm việc gắn kèm thì không hoàn tác được nữa.
 * {@code personType} là loại hồ sơ của lần làm việc đó (trước khi nhận làm lại đổi loại); null với dòng cũ, coi như NHAN_VIEN.
 */
@Entity
@Audited
@Table(name = "employment_period")
public class EmploymentPeriod implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @NotNull
    @Column(name = "last_working_day", nullable = false)
    private LocalDate lastWorkingDay;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_type")
    private TerminationType terminationType;

    @Column(name = "termination_reason", length = 500)
    private String terminationReason;

    @Column(name = "termination_record_id")
    private Long terminationRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type")
    private PersonType personType;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
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

    public LocalDate getTerminationDate() {
        return terminationDate;
    }

    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = terminationDate;
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

    public Long getTerminationRecordId() {
        return terminationRecordId;
    }

    public void setTerminationRecordId(Long terminationRecordId) {
        this.terminationRecordId = terminationRecordId;
    }

    public PersonType getPersonType() {
        return personType;
    }

    public void setPersonType(PersonType personType) {
        this.personType = personType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmploymentPeriod)) {
            return false;
        }
        return id != null && id.equals(((EmploymentPeriod) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "EmploymentPeriod{id=" +
            id +
            ", employeeId=" +
            employeeId +
            ", joinDate=" +
            joinDate +
            ", lastWorkingDay=" +
            lastWorkingDay +
            ", terminationRecordId=" +
            terminationRecordId +
            "}"
        );
    }
}

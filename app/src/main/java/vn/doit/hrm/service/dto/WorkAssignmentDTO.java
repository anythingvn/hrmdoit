package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/**
 * A DTO for the {@link vn.doit.hrm.domain.WorkAssignment} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WorkAssignmentDTO implements Serializable {

    private Long id;

    @NotNull
    private WorkLocationType type;

    @Size(max = 200)
    private String unit;

    @Size(max = 300)
    private String address;

    @NotNull
    private LocalDate fromDate;

    private LocalDate toDate;

    @Size(max = 50)
    private String decisionNo;

    @Size(max = 500)
    private String note;

    @NotNull
    private EmployeeDTO employee;

    private WorkSiteDTO site;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WorkLocationType getType() {
        return type;
    }

    public void setType(WorkLocationType type) {
        this.type = type;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public String getDecisionNo() {
        return decisionNo;
    }

    public void setDecisionNo(String decisionNo) {
        this.decisionNo = decisionNo;
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

    public WorkSiteDTO getSite() {
        return site;
    }

    public void setSite(WorkSiteDTO site) {
        this.site = site;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkAssignmentDTO)) {
            return false;
        }

        WorkAssignmentDTO workAssignmentDTO = (WorkAssignmentDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, workAssignmentDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "WorkAssignmentDTO{" +
            "id=" + getId() +
            ", type='" + getType() + "'" +
            ", unit='" + getUnit() + "'" +
            ", address='" + getAddress() + "'" +
            ", fromDate='" + getFromDate() + "'" +
            ", toDate='" + getToDate() + "'" +
            ", decisionNo='" + getDecisionNo() + "'" +
            ", note='" + getNote() + "'" +
            ", employee=" + getEmployee() +
            ", site=" + getSite() +
            "}";
    }
}

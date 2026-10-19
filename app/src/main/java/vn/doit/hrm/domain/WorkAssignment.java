package vn.doit.hrm.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/**
 * A WorkAssignment.
 */
@Entity
@Audited
@Table(name = "work_assignment")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WorkAssignment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private WorkLocationType type;

    @Size(max = 200)
    @Column(name = "unit", length = 200)
    private String unit;

    @Size(max = 300)
    @Column(name = "address", length = 300)
    private String address;

    @NotNull
    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Size(max = 50)
    @Column(name = "decision_no", length = 50)
    private String decisionNo;

    @Size(max = 500)
    @Column(name = "note", length = 500)
    private String note;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "department", "manager", "user" }, allowSetters = true)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    private WorkSite site;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public WorkAssignment id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WorkLocationType getType() {
        return this.type;
    }

    public WorkAssignment type(WorkLocationType type) {
        this.setType(type);
        return this;
    }

    public void setType(WorkLocationType type) {
        this.type = type;
    }

    public String getUnit() {
        return this.unit;
    }

    public WorkAssignment unit(String unit) {
        this.setUnit(unit);
        return this;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getAddress() {
        return this.address;
    }

    public WorkAssignment address(String address) {
        this.setAddress(address);
        return this;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getFromDate() {
        return this.fromDate;
    }

    public WorkAssignment fromDate(LocalDate fromDate) {
        this.setFromDate(fromDate);
        return this;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return this.toDate;
    }

    public WorkAssignment toDate(LocalDate toDate) {
        this.setToDate(toDate);
        return this;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public String getDecisionNo() {
        return this.decisionNo;
    }

    public WorkAssignment decisionNo(String decisionNo) {
        this.setDecisionNo(decisionNo);
        return this;
    }

    public void setDecisionNo(String decisionNo) {
        this.decisionNo = decisionNo;
    }

    public String getNote() {
        return this.note;
    }

    public WorkAssignment note(String note) {
        this.setNote(note);
        return this;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Employee getEmployee() {
        return this.employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public WorkAssignment employee(Employee employee) {
        this.setEmployee(employee);
        return this;
    }

    public WorkSite getSite() {
        return this.site;
    }

    public void setSite(WorkSite workSite) {
        this.site = workSite;
    }

    public WorkAssignment site(WorkSite workSite) {
        this.setSite(workSite);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkAssignment)) {
            return false;
        }
        return getId() != null && getId().equals(((WorkAssignment) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "WorkAssignment{" +
            "id=" + getId() +
            ", type='" + getType() + "'" +
            ", unit='" + getUnit() + "'" +
            ", address='" + getAddress() + "'" +
            ", fromDate='" + getFromDate() + "'" +
            ", toDate='" + getToDate() + "'" +
            ", decisionNo='" + getDecisionNo() + "'" +
            ", note='" + getNote() + "'" +
            "}";
    }
}

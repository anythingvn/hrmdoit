package vn.doit.hrm.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * A Allowance.
 */
@Entity
@Table(name = "allowance")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Allowance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 100)
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @NotNull
    @Column(name = "amount_per_month", nullable = false)
    private Long amountPerMonth;

    @NotNull
    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "department", "manager", "user" }, allowSetters = true)
    private Employee employee;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Allowance id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Allowance name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAmountPerMonth() {
        return this.amountPerMonth;
    }

    public Allowance amountPerMonth(Long amountPerMonth) {
        this.setAmountPerMonth(amountPerMonth);
        return this;
    }

    public void setAmountPerMonth(Long amountPerMonth) {
        this.amountPerMonth = amountPerMonth;
    }

    public LocalDate getFromDate() {
        return this.fromDate;
    }

    public Allowance fromDate(LocalDate fromDate) {
        this.setFromDate(fromDate);
        return this;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return this.toDate;
    }

    public Allowance toDate(LocalDate toDate) {
        this.setToDate(toDate);
        return this;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Employee getEmployee() {
        return this.employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Allowance employee(Employee employee) {
        this.setEmployee(employee);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Allowance)) {
            return false;
        }
        return getId() != null && getId().equals(((Allowance) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Allowance{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", amountPerMonth=" + getAmountPerMonth() +
            ", fromDate='" + getFromDate() + "'" +
            ", toDate='" + getToDate() + "'" +
            "}";
    }
}

package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/**
 * A WorkSite.
 */
@Entity
@Table(name = "work_site")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WorkSite implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 200)
    @Column(name = "name", length = 200, nullable = false, unique = true)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private WorkLocationType type;

    @Size(max = 300)
    @Column(name = "address", length = 300)
    private String address;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public WorkSite id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public WorkSite name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WorkLocationType getType() {
        return this.type;
    }

    public WorkSite type(WorkLocationType type) {
        this.setType(type);
        return this;
    }

    public void setType(WorkLocationType type) {
        this.type = type;
    }

    public String getAddress() {
        return this.address;
    }

    public WorkSite address(String address) {
        this.setAddress(address);
        return this;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Boolean getActive() {
        return this.active;
    }

    public WorkSite active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkSite)) {
            return false;
        }
        return getId() != null && getId().equals(((WorkSite) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "WorkSite{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", type='" + getType() + "'" +
            ", address='" + getAddress() + "'" +
            ", active='" + getActive() + "'" +
            "}";
    }
}

package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/**
 * A DTO for the {@link vn.doit.hrm.domain.WorkSite} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WorkSiteDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(max = 200)
    private String name;

    @NotNull
    private WorkLocationType type;

    @Size(max = 300)
    private String address;

    @NotNull
    private Boolean active;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WorkLocationType getType() {
        return type;
    }

    public void setType(WorkLocationType type) {
        this.type = type;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkSiteDTO)) {
            return false;
        }

        WorkSiteDTO workSiteDTO = (WorkSiteDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, workSiteDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "WorkSiteDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", type='" + getType() + "'" +
            ", address='" + getAddress() + "'" +
            ", active='" + getActive() + "'" +
            "}";
    }
}

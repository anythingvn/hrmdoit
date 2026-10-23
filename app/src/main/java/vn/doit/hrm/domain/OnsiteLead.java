package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/** Trưởng nhóm onsite của một khách hàng: gõ giờ cho người onsite ở khách hàng đó vào đúng ngày onsite. */
@Entity
@Table(name = "work_site_onsite_lead")
public class OnsiteLead implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "work_site_id", nullable = false)
    private Long workSiteId;

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    protected OnsiteLead() {}

    public OnsiteLead(Long workSiteId, Long employeeId) {
        this.workSiteId = workSiteId;
        this.employeeId = employeeId;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkSiteId() {
        return workSiteId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OnsiteLead)) {
            return false;
        }
        return id != null && id.equals(((OnsiteLead) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "OnsiteLead{id=" + id + ", workSiteId=" + workSiteId + ", employeeId=" + employeeId + "}";
    }
}

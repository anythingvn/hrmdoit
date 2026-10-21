package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import org.hibernate.envers.Audited;

/** Lịch làm việc (một dòng, id = 1): các ngày làm việc trong tuần, dạng "MONDAY,TUESDAY,...". */
@Entity
@Audited
@Table(name = "work_calendar_setting")
public class WorkCalendarSetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "working_days", length = 60, nullable = false)
    private String workingDays;

    public Long getId() {
        return id;
    }

    public String getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(String workingDays) {
        this.workingDays = workingDays;
    }
}

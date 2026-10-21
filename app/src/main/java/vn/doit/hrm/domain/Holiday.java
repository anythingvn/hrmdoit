package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import org.hibernate.envers.Audited;

/** Một ngày lễ (nghỉ): mốc nhắc rơi vào ngày này được dời về ngày làm việc liền trước, email không gửi. */
@Entity
@Audited
@Table(name = "holiday")
public class Holiday implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "day", nullable = false, unique = true)
    private LocalDate day;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    public Long getId() {
        return id;
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Nhân viên đã đồng ý thông báo thu thập vị trí khi tự chấm công (mỗi người một dòng): thời điểm và phiên bản nội dung lúc đồng ý.
 * Đổi nội dung thông báo không bắt đồng ý lại.
 */
@Entity
@Table(name = "attendance_consent")
public class AttendanceConsent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "employee_id", nullable = false, unique = true)
    private Long employeeId;

    @NotNull
    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @NotNull
    @Column(name = "notice_version", nullable = false)
    private Integer noticeVersion;

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

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Integer getNoticeVersion() {
        return noticeVersion;
    }

    public void setNoticeVersion(Integer noticeVersion) {
        this.noticeVersion = noticeVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttendanceConsent)) {
            return false;
        }
        return id != null && id.equals(((AttendanceConsent) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "AttendanceConsent{id=" + id + ", employeeId=" + employeeId + ", noticeVersion=" + noticeVersion + "}";
    }
}

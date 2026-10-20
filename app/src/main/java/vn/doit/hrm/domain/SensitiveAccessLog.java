package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

/**
 * Nhật ký mỗi lần xem dữ liệu nhạy cảm của nhân viên.
 */
@Entity
@Table(name = "sensitive_access_log")
public class SensitiveAccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @NotNull
    @Column(name = "viewer_login", length = 50, nullable = false)
    private String viewerLogin;

    @NotNull
    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    public SensitiveAccessLog() {}

    public SensitiveAccessLog(Long employeeId, String viewerLogin, Instant viewedAt) {
        this.employeeId = employeeId;
        this.viewerLogin = viewerLogin;
        this.viewedAt = viewedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getViewerLogin() {
        return viewerLogin;
    }

    public Instant getViewedAt() {
        return viewedAt;
    }
}

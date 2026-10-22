package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Nhật ký xóa hẳn nhân viên: người xóa, thời điểm, lý do, mã và họ tên. Chỉ thêm dòng, không sửa. Cố ý không lưu CCCD, tài khoản
 * ngân hàng, địa chỉ hay dữ liệu cá nhân khác; không khóa ngoại tới hồ sơ (hồ sơ đã bị xóa).
 */
@Entity
@Immutable
@Table(name = "employee_deletion_log")
public class EmployeeDeletionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "deleted_at", nullable = false, updatable = false)
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 50, nullable = false, updatable = false)
    private String deletedBy;

    @Column(name = "reason", length = 500, nullable = false, updatable = false)
    private String reason;

    @Column(name = "employee_code", length = 50, updatable = false)
    private String employeeCode;

    @Column(name = "employee_name", length = 255, updatable = false)
    private String employeeName;

    protected EmployeeDeletionLog() {}

    public EmployeeDeletionLog(Instant deletedAt, String deletedBy, String reason, String employeeCode, String employeeName) {
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.reason = reason;
        this.employeeCode = employeeCode;
        this.employeeName = employeeName;
    }

    public Long getId() {
        return id;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public String getReason() {
        return reason;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getEmployeeName() {
        return employeeName;
    }
}

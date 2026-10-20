package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.domain.enumeration.TerminationType;

/**
 * Một lần cho nghỉ việc: hẹn trước ({@code SCHEDULED}) hoặc đã áp dụng ({@code APPLIED}); hủy lịch ({@code CANCELLED});
 * hoàn tác ({@code UNDONE}). {@code snapshotJson} giữ các giá trị trước/sau khi áp dụng để hoàn tác đúng.
 */
@Entity
@Audited
@Table(name = "termination_record")
public class TerminationRecord implements Serializable {

    @Serial
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
    @Column(name = "last_working_day", nullable = false)
    private LocalDate lastWorkingDay;

    @NotNull
    @Column(name = "termination_date", nullable = false)
    private LocalDate terminationDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "termination_type", nullable = false)
    private TerminationType terminationType;

    @Column(name = "reason", length = 500)
    private String reason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TerminationRecordStatus status;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "undone_by", length = 50)
    private String undoneBy;

    @Column(name = "undone_at")
    private Instant undoneAt;

    @Column(name = "snapshot_json")
    private String snapshotJson;

    /** Lý do job chưa áp dụng được lịch đã đến hạn (lần chạy gần nhất); xóa khi áp dụng thành công. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** Giai đoạn "quay về" HR đã xóa ngay trước khi cho nghỉ (mô tả); hoàn tác không khôi phục được, chỉ báo lại. */
    @Column(name = "deleted_return_periods", length = 500)
    private String deletedReturnPeriods;

    public String getDeletedReturnPeriods() {
        return deletedReturnPeriods;
    }

    public void setDeletedReturnPeriods(String deletedReturnPeriods) {
        this.deletedReturnPeriods = deletedReturnPeriods;
    }

    /**
     * Lịch hẹn quá hạn mà chưa áp dụng được: job đã báo lỗi, hoặc đã qua trọn một ngày kể từ lúc đến hạn (job chạy 00:05 ngày sau
     * ngày làm việc cuối; không hiện nhãn quá hạn trong mấy phút trước khi job chạy).
     */
    public boolean isOverdue(LocalDate today) {
        return (
            status == TerminationRecordStatus.SCHEDULED &&
            lastWorkingDay != null &&
            (lastError != null || lastWorkingDay.isBefore(today.minusDays(1)))
        );
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

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

    public LocalDate getLastWorkingDay() {
        return lastWorkingDay;
    }

    public void setLastWorkingDay(LocalDate lastWorkingDay) {
        this.lastWorkingDay = lastWorkingDay;
    }

    public LocalDate getTerminationDate() {
        return terminationDate;
    }

    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = terminationDate;
    }

    public TerminationType getTerminationType() {
        return terminationType;
    }

    public void setTerminationType(TerminationType terminationType) {
        this.terminationType = terminationType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public TerminationRecordStatus getStatus() {
        return status;
    }

    public void setStatus(TerminationRecordStatus status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(String cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getUndoneBy() {
        return undoneBy;
    }

    public void setUndoneBy(String undoneBy) {
        this.undoneBy = undoneBy;
    }

    public Instant getUndoneAt() {
        return undoneAt;
    }

    public void setUndoneAt(Instant undoneAt) {
        this.undoneAt = undoneAt;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TerminationRecord)) {
            return false;
        }
        return getId() != null && getId().equals(((TerminationRecord) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "TerminationRecord{id=" +
            id +
            ", employeeId=" +
            employeeId +
            ", lastWorkingDay=" +
            lastWorkingDay +
            ", terminationDate=" +
            terminationDate +
            ", status=" +
            status +
            "}"
        );
    }
}

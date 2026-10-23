package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcType;
import org.hibernate.envers.Audited;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;

/**
 * Một bộ giờ làm có hiệu lực từ {@code effectiveFrom}: ngày D dùng bộ giờ có {@code effectiveFrom} lớn nhất nhưng không sau D.
 * {@code workSiteId} null là bộ giờ của công ty (có phút muộn cho phép và mốc nửa ngày); khác null là bộ giờ riêng của một khách
 * hàng (chỉ giờ vào, giờ ra, nghỉ trưa; phút muộn cho phép và mốc nửa ngày luôn lấy theo công ty).
 * Giờ lưu đúng giờ đồng hồ (kiểu {@code time}, không đổi múi giờ) để bộ giờ mặc định nạp từ Liquibase đọc ra đúng.
 */
@Entity
@Audited
@Table(name = "attendance_schedule")
public class AttendanceSchedule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @NotNull
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @NotNull
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "break_start", nullable = false)
    private LocalTime breakStart;

    @NotNull
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "break_end", nullable = false)
    private LocalTime breakEnd;

    @Column(name = "grace_minutes")
    private Integer graceMinutes;

    @Column(name = "half_day_threshold_minutes")
    private Integer halfDayThresholdMinutes;

    @Column(name = "work_site_id")
    private Long workSiteId;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public LocalTime getBreakStart() {
        return breakStart;
    }

    public void setBreakStart(LocalTime breakStart) {
        this.breakStart = breakStart;
    }

    public LocalTime getBreakEnd() {
        return breakEnd;
    }

    public void setBreakEnd(LocalTime breakEnd) {
        this.breakEnd = breakEnd;
    }

    public Integer getGraceMinutes() {
        return graceMinutes;
    }

    public void setGraceMinutes(Integer graceMinutes) {
        this.graceMinutes = graceMinutes;
    }

    public Integer getHalfDayThresholdMinutes() {
        return halfDayThresholdMinutes;
    }

    public void setHalfDayThresholdMinutes(Integer halfDayThresholdMinutes) {
        this.halfDayThresholdMinutes = halfDayThresholdMinutes;
    }

    public Long getWorkSiteId() {
        return workSiteId;
    }

    public void setWorkSiteId(Long workSiteId) {
        this.workSiteId = workSiteId;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttendanceSchedule)) {
            return false;
        }
        return id != null && id.equals(((AttendanceSchedule) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "AttendanceSchedule{id=" +
            id +
            ", effectiveFrom=" +
            effectiveFrom +
            ", startTime=" +
            startTime +
            ", endTime=" +
            endTime +
            ", workSiteId=" +
            workSiteId +
            "}"
        );
    }
}

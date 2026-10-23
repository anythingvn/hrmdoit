package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Ảnh chụp số công của một người lúc chốt bảng công tháng {@code month} (yyyy-MM); dùng cho file bảng lương và đối chiếu khi
 * nhập. Số ngày tính theo công (bội số 0,5); số lần và số phút là số nguyên.
 */
@Entity
@Table(name = "attendance_snapshot")
public class AttendanceSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "month", length = 7, nullable = false)
    private String month;

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "standard_days", precision = 6, scale = 2)
    private BigDecimal standardDays;

    @Column(name = "actual_days", precision = 6, scale = 2)
    private BigDecimal actualDays;

    @Column(name = "work_days", precision = 6, scale = 2)
    private BigDecimal workDays;

    @Column(name = "paid_leave_days", precision = 6, scale = 2)
    private BigDecimal paidLeaveDays;

    @Column(name = "unpaid_leave_days", precision = 6, scale = 2)
    private BigDecimal unpaidLeaveDays;

    @Column(name = "sick_days", precision = 6, scale = 2)
    private BigDecimal sickDays;

    @Column(name = "holiday_days", precision = 6, scale = 2)
    private BigDecimal holidayDays;

    @Column(name = "trip_days", precision = 6, scale = 2)
    private BigDecimal tripDays;

    @Column(name = "late_count")
    private Integer lateCount;

    @Column(name = "late_minutes")
    private Integer lateMinutes;

    @Column(name = "early_count")
    private Integer earlyCount;

    @Column(name = "half_days")
    private Integer halfDays;

    @Column(name = "absent_days")
    private Integer absentDays;

    @Column(name = "missing_days")
    private Integer missingDays;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public BigDecimal getStandardDays() {
        return standardDays;
    }

    public void setStandardDays(BigDecimal standardDays) {
        this.standardDays = standardDays;
    }

    public BigDecimal getActualDays() {
        return actualDays;
    }

    public void setActualDays(BigDecimal actualDays) {
        this.actualDays = actualDays;
    }

    public BigDecimal getWorkDays() {
        return workDays;
    }

    public void setWorkDays(BigDecimal workDays) {
        this.workDays = workDays;
    }

    public BigDecimal getPaidLeaveDays() {
        return paidLeaveDays;
    }

    public void setPaidLeaveDays(BigDecimal paidLeaveDays) {
        this.paidLeaveDays = paidLeaveDays;
    }

    public BigDecimal getUnpaidLeaveDays() {
        return unpaidLeaveDays;
    }

    public void setUnpaidLeaveDays(BigDecimal unpaidLeaveDays) {
        this.unpaidLeaveDays = unpaidLeaveDays;
    }

    public BigDecimal getSickDays() {
        return sickDays;
    }

    public void setSickDays(BigDecimal sickDays) {
        this.sickDays = sickDays;
    }

    public BigDecimal getHolidayDays() {
        return holidayDays;
    }

    public void setHolidayDays(BigDecimal holidayDays) {
        this.holidayDays = holidayDays;
    }

    public BigDecimal getTripDays() {
        return tripDays;
    }

    public void setTripDays(BigDecimal tripDays) {
        this.tripDays = tripDays;
    }

    public Integer getLateCount() {
        return lateCount;
    }

    public void setLateCount(Integer lateCount) {
        this.lateCount = lateCount;
    }

    public Integer getLateMinutes() {
        return lateMinutes;
    }

    public void setLateMinutes(Integer lateMinutes) {
        this.lateMinutes = lateMinutes;
    }

    public Integer getEarlyCount() {
        return earlyCount;
    }

    public void setEarlyCount(Integer earlyCount) {
        this.earlyCount = earlyCount;
    }

    public Integer getHalfDays() {
        return halfDays;
    }

    public void setHalfDays(Integer halfDays) {
        this.halfDays = halfDays;
    }

    public Integer getAbsentDays() {
        return absentDays;
    }

    public void setAbsentDays(Integer absentDays) {
        this.absentDays = absentDays;
    }

    public Integer getMissingDays() {
        return missingDays;
    }

    public void setMissingDays(Integer missingDays) {
        this.missingDays = missingDays;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttendanceSnapshot)) {
            return false;
        }
        return id != null && id.equals(((AttendanceSnapshot) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "AttendanceSnapshot{id=" + id + ", month='" + month + "', employeeId=" + employeeId + ", actualDays=" + actualDays + "}";
    }
}

package vn.doit.hrm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcType;
import org.hibernate.envers.Audited;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchMethod;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.AttendancePunchStatus;

/** Một lần chấm vào hoặc ra của một người trong một ngày (gõ tay hoặc từ ứng dụng). */
@Entity
@Audited
@Table(name = "attendance_punch")
public class AttendancePunch implements Serializable {

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
    @Column(name = "work_day", nullable = false)
    private LocalDate workDay;

    /**
     * Giờ đồng hồ (wall-clock) lưu qua {@link LocalTimeJdbcType}: không đổi múi giờ UTC như {@code overtime_request}; SQL thô đọc/ghi
     * cột này phải dùng nguyên giá trị, không dịch múi giờ.
     */
    @NotNull
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "punch_time", nullable = false)
    private LocalTime punchTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10, nullable = false)
    private AttendancePunchKind kind;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 10, nullable = false)
    private AttendancePunchSource source;

    @Column(name = "entered_by", length = 50)
    private String enteredBy;

    @Column(name = "entered_at")
    private Instant enteredAt;

    /** Gõ tay mặc định {@code CHAP_NHAN}; xem {@code SelfPunchService.counts} cho quy tắc được tính công. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AttendancePunchStatus status = AttendancePunchStatus.CHAP_NHAN;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 10)
    private AttendancePunchMethod method;

    /** Điểm chấm công đã đối chiếu (bảng {@code work_site}); null khi từ xa hoặc không đối chiếu được. */
    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "distance_m")
    private Integer distanceM;

    /** Tọa độ, sai số và IP: dữ liệu vị trí cá nhân, đặt về null sau {@code location_retention_days}; không ghi log. */
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_m", precision = 7, scale = 1)
    private BigDecimal accuracyM;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "decided_by", length = 50)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "explanation_id")
    private Long explanationId;

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

    public LocalDate getWorkDay() {
        return workDay;
    }

    public void setWorkDay(LocalDate workDay) {
        this.workDay = workDay;
    }

    public LocalTime getPunchTime() {
        return punchTime;
    }

    public void setPunchTime(LocalTime punchTime) {
        this.punchTime = punchTime;
    }

    public AttendancePunchKind getKind() {
        return kind;
    }

    public void setKind(AttendancePunchKind kind) {
        this.kind = kind;
    }

    public AttendancePunchSource getSource() {
        return source;
    }

    public void setSource(AttendancePunchSource source) {
        this.source = source;
    }

    public String getEnteredBy() {
        return enteredBy;
    }

    public void setEnteredBy(String enteredBy) {
        this.enteredBy = enteredBy;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(Instant enteredAt) {
        this.enteredAt = enteredAt;
    }

    public AttendancePunchStatus getStatus() {
        return status;
    }

    public void setStatus(AttendancePunchStatus status) {
        this.status = status;
    }

    public AttendancePunchMethod getMethod() {
        return method;
    }

    public void setMethod(AttendancePunchMethod method) {
        this.method = method;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public Integer getDistanceM() {
        return distanceM;
    }

    public void setDistanceM(Integer distanceM) {
        this.distanceM = distanceM;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getAccuracyM() {
        return accuracyM;
    }

    public void setAccuracyM(BigDecimal accuracyM) {
        this.accuracyM = accuracyM;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public Long getExplanationId() {
        return explanationId;
    }

    public void setExplanationId(Long explanationId) {
        this.explanationId = explanationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttendancePunch)) {
            return false;
        }
        return id != null && id.equals(((AttendancePunch) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "AttendancePunch{id=" + id + ", employeeId=" + employeeId + ", workDay=" + workDay + ", punchTime=" + punchTime + ", kind=" + kind + "}"
        );
    }
}

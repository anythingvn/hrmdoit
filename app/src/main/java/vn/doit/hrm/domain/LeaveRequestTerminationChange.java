package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;
import vn.doit.hr.leave.DayPart;

/**
 * Một lần nghỉ việc rút ngắn / hủy một đơn nghỉ (F3): lần cho nghỉ, thời điểm, và khoảng ngày của đơn ngay trước lần đổi đó (ngày bắt
 * đầu và buổi đầu không đổi khi rút ngắn nên lấy từ đơn). Khoảng ngay sau lần đổi là khoảng trước của lần đổi kế tiếp, hoặc khoảng hiện
 * tại của đơn nếu đây là lần cuối. Chỉ ghi thêm, không sửa, không xóa (như dòng sổ phép) nên không cần bảng lịch sử {@code _aud}.
 */
@Entity
@Immutable
@Table(name = "leave_request_termination_change")
public class LeaveRequestTerminationChange implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "leave_request_id", nullable = false, updatable = false)
    private Long leaveRequestId;

    @Column(name = "termination_record_id", updatable = false)
    private Long terminationRecordId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "before_to_date", nullable = false, updatable = false)
    private LocalDate beforeToDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_last_part", length = 10, updatable = false)
    private DayPart beforeLastPart;

    @Column(name = "before_days", precision = 5, scale = 2, updatable = false)
    private BigDecimal beforeDays;

    protected LeaveRequestTerminationChange() {}

    public LeaveRequestTerminationChange(
        Long leaveRequestId,
        Long terminationRecordId,
        Instant changedAt,
        LocalDate beforeToDate,
        DayPart beforeLastPart,
        BigDecimal beforeDays
    ) {
        this.leaveRequestId = leaveRequestId;
        this.terminationRecordId = terminationRecordId;
        this.changedAt = changedAt;
        this.beforeToDate = beforeToDate;
        this.beforeLastPart = beforeLastPart;
        this.beforeDays = beforeDays;
    }

    public Long getId() {
        return id;
    }

    public Long getLeaveRequestId() {
        return leaveRequestId;
    }

    public Long getTerminationRecordId() {
        return terminationRecordId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public LocalDate getBeforeToDate() {
        return beforeToDate;
    }

    public DayPart getBeforeLastPart() {
        return beforeLastPart;
    }

    public BigDecimal getBeforeDays() {
        return beforeDays;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LeaveRequestTerminationChange other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "LeaveRequestTerminationChange{id=" +
            id +
            ", leaveRequestId=" +
            leaveRequestId +
            ", terminationRecordId=" +
            terminationRecordId +
            ", changedAt=" +
            changedAt +
            ", beforeToDate=" +
            beforeToDate +
            "}"
        );
    }
}

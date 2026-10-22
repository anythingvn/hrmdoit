package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.domain.enumeration.LedgerReason;

/**
 * Một dòng sổ phép: không sửa, không xóa (muốn sửa thì ghi dòng đảo ngược). Số dương cộng, số âm trừ; quỹ phép năm tính bằng
 * ngày, quỹ nghỉ bù tính bằng giờ. {@code year} là năm của ngày hiệu lực, chỉ để tra cứu: số dư là tổng mọi dòng.
 */
@Entity
@Immutable
@Table(name = "leave_ledger_entry")
public class LeaveLedgerEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pool", length = 10, nullable = false, updatable = false)
    private Pool pool;

    @Enumerated(EnumType.STRING)
    @Column(name = "bucket", length = 10, nullable = false, updatable = false)
    private Bucket bucket;

    @Column(name = "year", nullable = false, updatable = false)
    private int year;

    @Column(name = "amount", precision = 7, scale = 2, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "effective_date", nullable = false, updatable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 20, nullable = false, updatable = false)
    private LedgerReason reason;

    @Column(name = "ref_type", length = 30, updatable = false)
    private String refType;

    @Column(name = "ref_id", updatable = false)
    private Long refId;

    @Column(name = "note", length = 500, updatable = false)
    private String note;

    @Column(name = "created_by", length = 100, nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LeaveLedgerEntry() {}

    public LeaveLedgerEntry(
        Long employeeId,
        Pool pool,
        Bucket bucket,
        BigDecimal amount,
        LocalDate effectiveDate,
        LedgerReason reason,
        String refType,
        Long refId,
        String note,
        String createdBy,
        Instant createdAt
    ) {
        this.employeeId = employeeId;
        this.pool = pool;
        this.bucket = bucket;
        this.year = effectiveDate.getYear();
        this.amount = amount;
        this.effectiveDate = effectiveDate;
        this.reason = reason;
        this.refType = refType;
        this.refId = refId;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Pool getPool() {
        return pool;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LedgerReason getReason() {
        return reason;
    }

    public String getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Dòng ở dạng lõi để tính số dư. */
    public Entry toEntry() {
        return new Entry(pool, bucket, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LeaveLedgerEntry other)) {
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
            "LeaveLedgerEntry{id=" +
            id +
            ", employeeId=" +
            employeeId +
            ", pool=" +
            pool +
            ", bucket=" +
            bucket +
            ", amount=" +
            amount +
            ", effectiveDate=" +
            effectiveDate +
            ", reason=" +
            reason +
            ", ref=" +
            refType +
            ":" +
            refId +
            "}"
        );
    }
}

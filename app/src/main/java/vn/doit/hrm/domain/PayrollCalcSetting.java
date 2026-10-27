package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.Audited;

/**
 * Bộ thông số tính lương áp dụng từ tháng {@code effectiveFrom} (yyyy-MM) đến trước bộ kế tiếp; nội dung lưu dạng JSON.
 * Bộ đã có kỳ đã duyệt trong khoảng hiệu lực thì không sửa, không xóa.
 */
@Entity
@Audited
@Table(name = "payroll_calc_setting")
public class PayrollCalcSetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "effective_from", length = 7, nullable = false, unique = true)
    private String effectiveFrom;

    @Column(name = "settings", columnDefinition = "text", nullable = false)
    private String settings;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
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
}

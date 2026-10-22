package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import org.hibernate.envers.Audited;

/** Cài đặt nghỉ phép (một dòng, id = 1). */
@Entity
@Audited
@Table(name = "leave_setting")
public class LeaveSetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    /** Số ngày phép năm được ứng trước (số dư thấp nhất = −advanceDays). */
    @Column(name = "advance_days", nullable = false)
    private int advanceDays;

    /** Nghỉ quá số ngày làm việc này thì giám đốc duyệt. */
    @Column(name = "director_threshold_days", nullable = false)
    private int directorThresholdDays;

    @Column(name = "ot_month_cap", nullable = false)
    private int otMonthCap;

    @Column(name = "ot_year_cap", nullable = false)
    private int otYearCap;

    /** Hạn dùng phép năm trước (tháng/ngày), mặc định 31/03. */
    @Column(name = "carry_expiry_month", nullable = false)
    private int carryExpiryMonth;

    @Column(name = "carry_expiry_day", nullable = false)
    private int carryExpiryDay;

    /** Được gửi đơn cho ngày đã qua trong số ngày này. */
    @Column(name = "past_request_days", nullable = false)
    private int pastRequestDays;

    /** Ngày trong tháng chốt xác nhận làm thêm giờ của tháng trước. */
    @Column(name = "ot_confirm_day", nullable = false)
    private int otConfirmDay;

    public Long getId() {
        return id;
    }

    public int getAdvanceDays() {
        return advanceDays;
    }

    public void setAdvanceDays(int advanceDays) {
        this.advanceDays = advanceDays;
    }

    public int getDirectorThresholdDays() {
        return directorThresholdDays;
    }

    public void setDirectorThresholdDays(int directorThresholdDays) {
        this.directorThresholdDays = directorThresholdDays;
    }

    public int getOtMonthCap() {
        return otMonthCap;
    }

    public void setOtMonthCap(int otMonthCap) {
        this.otMonthCap = otMonthCap;
    }

    public int getOtYearCap() {
        return otYearCap;
    }

    public void setOtYearCap(int otYearCap) {
        this.otYearCap = otYearCap;
    }

    public int getCarryExpiryMonth() {
        return carryExpiryMonth;
    }

    public void setCarryExpiryMonth(int carryExpiryMonth) {
        this.carryExpiryMonth = carryExpiryMonth;
    }

    public int getCarryExpiryDay() {
        return carryExpiryDay;
    }

    public void setCarryExpiryDay(int carryExpiryDay) {
        this.carryExpiryDay = carryExpiryDay;
    }

    public int getPastRequestDays() {
        return pastRequestDays;
    }

    public void setPastRequestDays(int pastRequestDays) {
        this.pastRequestDays = pastRequestDays;
    }

    public int getOtConfirmDay() {
        return otConfirmDay;
    }

    public void setOtConfirmDay(int otConfirmDay) {
        this.otConfirmDay = otConfirmDay;
    }
}

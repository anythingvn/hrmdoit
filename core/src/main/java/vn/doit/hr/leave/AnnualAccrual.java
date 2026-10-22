package vn.doit.hr.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import vn.doit.hr.reminder.WorkCalendar;

public final class AnnualAccrual {

    private AnnualAccrual() {}

    /** Mức năm: mức gốc + 1 cho mỗi lần đủ 5 năm tính đến ngày cuối tháng trước. */
    public static int annualDays(int baseRate, LocalDate joinDate, YearMonth month) {
        LocalDate asOf = month.minusMonths(1).atEndOfMonth();
        if (asOf.isBefore(joinDate)) {
            return baseRate;
        }
        return baseRate + Period.between(joinDate, asOf).getYears() / 5;
    }

    /** Phép cộng của tháng: mức năm / 12; 0 nếu thời gian làm trong tháng chưa tới 50% ngày làm việc. */
    public static BigDecimal monthly(int annualDays, LocalDate joinDate, LocalDate lastWorkingDay, YearMonth month, WorkCalendar cal) {
        int all = 0;
        int served = 0;
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            if (!cal.isWorkingDay(d)) {
                continue;
            }
            all++;
            if (!d.isBefore(joinDate) && (lastWorkingDay == null || !d.isAfter(lastWorkingDay))) {
                served++;
            }
        }
        if (all == 0 || served * 2 < all) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(annualDays).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
}

package vn.doit.hr.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import vn.doit.hr.reminder.WorkCalendar;

/**
 * Phép năm cộng hằng tháng và phần cộng thêm theo thâm niên. Thâm niên: đủ năm tính theo ngày làm việc thực tế, kể cả ngày đầu và
 * ngày cuối (vào 01/07/2022 thì hết ngày 30/06/2027 là đủ 5 năm).
 */
public final class AnnualAccrual {

    private AnnualAccrual() {}

    /** Mức năm: mức gốc + {@code extraDays} cho mỗi lần đủ {@code cycleYears} năm thâm niên. */
    public static int annualDays(int baseRate, int seniorityYears, int cycleYears, int extraDays) {
        if (cycleYears <= 0 || extraDays <= 0 || seniorityYears <= 0) {
            return baseRate;
        }
        return baseRate + (seniorityYears / cycleYears) * extraDays;
    }

    /**
     * Số năm thâm niên tròn tính đến {@code asOf}: cộng số ngày của từng khoảng (tính cả hai đầu, phần sau {@code asOf} bỏ qua, khoảng
     * nghỉ giữa các lần làm không tính), rồi đổi ra năm bằng {@code Period} từ ngày bắt đầu sớm nhất đến ngày đó cộng tổng số ngày.
     */
    public static int seniorityYears(List<DateRange> spans, LocalDate asOf) {
        LocalDate first = null;
        long days = 0;
        for (DateRange r : spans) {
            if (r.from() == null || r.from().isAfter(asOf)) {
                continue;
            }
            LocalDate end = r.to() == null || r.to().isAfter(asOf) ? asOf : r.to();
            if (end.isBefore(r.from())) {
                continue;
            }
            days += ChronoUnit.DAYS.between(r.from(), end) + 1;
            if (first == null || r.from().isBefore(first)) {
                first = r.from();
            }
        }
        if (first == null) {
            return 0;
        }
        return Period.between(first, first.plusDays(days)).getYears();
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

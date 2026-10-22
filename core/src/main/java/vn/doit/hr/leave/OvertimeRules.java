package vn.doit.hr.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import vn.doit.hr.reminder.WorkCalendar;

public final class OvertimeRules {

    private OvertimeRules() {}

    /** 300 ngày lễ, 200 ngày nghỉ hằng tuần, 150 ngày làm việc. */
    public static int rate(LocalDate day, WorkCalendar cal, boolean holiday) {
        if (holiday) {
            return 300;
        }
        return cal.isWorkingDay(day) ? 150 : 200;
    }

    /** Có phần nào trong khoảng 22:00–06:00. */
    public static boolean hasNightHours(LocalTime start, LocalTime end) {
        int s = start.toSecondOfDay() / 60;
        int e = end.toSecondOfDay() / 60;
        if (e <= s) {
            e += 24 * 60;
        }
        // Đêm trong trục phút: [0,360) , [1320,1800) , [2760,3240)
        int[][] nights = { { 0, 360 }, { 1320, 1800 }, { 2760, 3240 } };
        for (int[] n : nights) {
            if (s < n[1] && e > n[0]) {
                return true;
            }
        }
        return false;
    }

    public static BigDecimal hours(LocalTime start, LocalTime end) {
        Duration d = Duration.between(start, end);
        if (d.isNegative() || d.isZero()) {
            d = d.plusHours(24);
        }
        return BigDecimal.valueOf(d.toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public static void checkLimits(BigDecimal monthHours, BigDecimal yearHours, BigDecimal added, int monthCap, int yearCap) {
        if (monthHours.add(added).compareTo(BigDecimal.valueOf(monthCap)) > 0) {
            throw new OvertimeLimitException(
                "Vượt giới hạn làm thêm giờ trong tháng: đã dùng " + fmt(monthHours) + " giờ, thêm " + fmt(added) + " giờ, giới hạn " + monthCap + " giờ/tháng"
            );
        }
        if (yearHours.add(added).compareTo(BigDecimal.valueOf(yearCap)) > 0) {
            throw new OvertimeLimitException(
                "Vượt giới hạn làm thêm giờ trong năm: đã dùng " + fmt(yearHours) + " giờ, thêm " + fmt(added) + " giờ, giới hạn " + yearCap + " giờ/năm"
            );
        }
    }

    private static String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}

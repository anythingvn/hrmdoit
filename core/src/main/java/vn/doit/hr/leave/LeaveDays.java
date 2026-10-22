package vn.doit.hr.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import vn.doit.hr.reminder.WorkCalendar;

public final class LeaveDays {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private LeaveDays() {}

    /** Số ngày làm việc trong khoảng; buổi sáng/chiều tính 0,5. Ngày lễ và cuối tuần không tính. */
    public static BigDecimal count(LocalDate from, LocalDate to, DayPart firstDay, DayPart lastDay, WorkCalendar cal) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to < from");
        }
        if (from.equals(to)) {
            return cal.isWorkingDay(from) ? weight(firstDay) : BigDecimal.ZERO;
        }
        if (firstDay == DayPart.SANG) {
            throw new IllegalArgumentException("Ngày đầu của đơn nhiều ngày chỉ được là cả ngày hoặc buổi chiều");
        }
        if (lastDay == DayPart.CHIEU) {
            throw new IllegalArgumentException("Ngày cuối của đơn nhiều ngày chỉ được là cả ngày hoặc buổi sáng");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (!cal.isWorkingDay(d)) {
                continue;
            }
            DayPart part = d.equals(from) ? firstDay : d.equals(to) ? lastDay : DayPart.CA_NGAY;
            total = total.add(weight(part));
        }
        return total;
    }

    private static BigDecimal weight(DayPart p) {
        return p == DayPart.CA_NGAY ? BigDecimal.ONE : HALF;
    }
}

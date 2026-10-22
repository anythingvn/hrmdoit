package vn.doit.hr.leave;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import vn.doit.hr.reminder.WorkCalendar;

final class LeaveTestSupport {
    private LeaveTestSupport() {}

    static WorkCalendar cal() {
        return WorkCalendar.of(
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            Set.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 2))
        );
    }

    static BigDecimal d(String s) {
        return new BigDecimal(s);
    }
}

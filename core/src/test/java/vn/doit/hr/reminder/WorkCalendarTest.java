package vn.doit.hr.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkCalendarTest {

    private final WorkCalendar cal = WorkCalendar.of(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Set.of(LocalDate.of(2026, 9, 2)));

    @Test
    void mondayToFridayWithHolidays() {
        assertFalse(cal.isWorkingDay(LocalDate.of(2026, 10, 24)));
        assertTrue(cal.isWorkingDay(LocalDate.of(2026, 10, 23)));
        assertEquals(LocalDate.of(2026, 10, 23), cal.onOrBefore(LocalDate.of(2026, 10, 24)));
        assertEquals(LocalDate.of(2026, 9, 1), cal.onOrBefore(LocalDate.of(2026, 9, 2)));
    }
}

package vn.doit.hr.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static vn.doit.hr.leave.LeaveTestSupport.cal;
import static vn.doit.hr.leave.LeaveTestSupport.d;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LeaveDaysTest {

    @Test
    void spanningHolidaysAndWeekendCountsOnlyWorkingDays() {
        assertEquals(0, d("1").compareTo(LeaveDays.count(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 4), DayPart.CA_NGAY, DayPart.CA_NGAY, cal())));
    }

    @Test
    void halfDay() {
        LocalDate day = LocalDate.of(2026, 10, 22);
        assertEquals(0, d("0.5").compareTo(LeaveDays.count(day, day, DayPart.SANG, DayPart.CA_NGAY, cal())));
    }

    @Test
    void afternoonToNextFullDay() {
        assertEquals(0, d("1.5").compareTo(LeaveDays.count(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23), DayPart.CHIEU, DayPart.CA_NGAY, cal())));
    }

    @Test
    void saturdayIsZero() {
        LocalDate day = LocalDate.of(2026, 10, 24);
        assertEquals(0, d("0").compareTo(LeaveDays.count(day, day, DayPart.CA_NGAY, DayPart.CA_NGAY, cal())));
    }

    @Test
    void morningStartOnMultiDayRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            LeaveDays.count(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23), DayPart.SANG, DayPart.CA_NGAY, cal()));
    }

    @Test
    void afternoonEndOnMultiDayRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            LeaveDays.count(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23), DayPart.CA_NGAY, DayPart.CHIEU, cal()));
    }
}

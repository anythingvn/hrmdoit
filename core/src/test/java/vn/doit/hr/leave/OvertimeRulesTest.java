package vn.doit.hr.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.doit.hr.leave.LeaveTestSupport.cal;
import static vn.doit.hr.leave.LeaveTestSupport.d;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class OvertimeRulesTest {

    @Test
    void rates() {
        assertEquals(150, OvertimeRules.rate(LocalDate.of(2026, 10, 22), cal(), false));
        assertEquals(200, OvertimeRules.rate(LocalDate.of(2026, 10, 24), cal(), false));
        assertEquals(300, OvertimeRules.rate(LocalDate.of(2026, 9, 2), cal(), true));
    }

    @Test
    void hoursAndNight() {
        assertEquals(0, d("5.5").compareTo(OvertimeRules.hours(LocalTime.of(18, 0), LocalTime.of(23, 30))));
        assertTrue(OvertimeRules.hasNightHours(LocalTime.of(18, 0), LocalTime.of(23, 30)));
        assertEquals(0, d("4").compareTo(OvertimeRules.hours(LocalTime.of(22, 0), LocalTime.of(2, 0))));
        assertFalse(OvertimeRules.hasNightHours(LocalTime.of(18, 0), LocalTime.of(21, 0)));
        assertTrue(OvertimeRules.hasNightHours(LocalTime.of(5, 0), LocalTime.of(7, 0)));
    }

    @Test
    void limits() {
        OvertimeLimitException e = assertThrows(OvertimeLimitException.class, () -> OvertimeRules.checkLimits(d("38"), d("100"), d("3"), 40, 200));
        assertTrue(e.getMessage().contains("38"));
        assertTrue(e.getMessage().contains("40"));
        assertThrows(OvertimeLimitException.class, () -> OvertimeRules.checkLimits(d("10"), d("199"), d("2"), 40, 200));
        OvertimeRules.checkLimits(d("10"), d("100"), d("2"), 40, 200);
    }
}

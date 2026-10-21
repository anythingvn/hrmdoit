package vn.doit.hr.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Danh sách sự kiện sắp tới / quá hạn chưa xử lý cho trang Tổng quan (không phụ thuộc mốc nhắc và nhật ký). */
class ReminderUpcomingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private final ReminderEngine engine = new ReminderEngine(
        ReminderPolicy.defaults(),
        WorkCalendar.of(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Set.of())
    );

    private static EmployeeSnapshot emp(List<EmployeeSnapshot.Contract> contracts, LocalDate probationEnd, boolean result,
                                        LocalDate lastSalary, Integer cycle, LocalDate override, LocalDate birth, boolean active) {
        return new EmployeeSnapshot("NV1", "Nguyễn Văn A", active, birth, contracts, probationEnd, result, lastSalary, cycle, override);
    }

    private static EmployeeSnapshot.Contract fixed(LocalDate start, LocalDate end) {
        return new EmployeeSnapshot.Contract("HĐ-1", start, end);
    }

    private List<Reminder> up(EmployeeSnapshot e, int horizon) {
        return engine.upcoming(TODAY, List.of(e), horizon);
    }

    @Test
    void contractExpiryWithinHorizon() {
        EmployeeSnapshot e = emp(List.of(fixed(LocalDate.of(2025, 11, 21), LocalDate.of(2026, 11, 20))), null, false, null, null, null, null, true);
        List<Reminder> r = up(e, 30);
        assertEquals(1, r.size());
        assertEquals(ReminderType.CONTRACT_EXPIRY, r.get(0).type());
        assertEquals(29, r.get(0).daysLeft());
        assertTrue(up(e, 20).isEmpty());
    }

    @Test
    void newIndefiniteContractStopsExpiry() {
        EmployeeSnapshot e = emp(
            List.of(fixed(LocalDate.of(2025, 11, 21), LocalDate.of(2026, 11, 20)), new EmployeeSnapshot.Contract("HĐ-2", LocalDate.of(2026, 11, 21), null)),
            null, false, null, null, null, null, true
        );
        assertTrue(up(e, 30).isEmpty());
    }

    @Test
    void overdueContractComesFirst() {
        EmployeeSnapshot over = emp(List.of(fixed(LocalDate.of(2025, 10, 13), LocalDate.of(2026, 10, 12))), null, false, null, null, null, null, true);
        EmployeeSnapshot later = new EmployeeSnapshot("NV2", "Trần B", true, LocalDate.of(1990, 11, 5), List.of(), null, false, null, null, null);
        List<Reminder> r = engine.upcoming(TODAY, List.of(later, over), 30);
        assertEquals(ReminderType.CONTRACT_OVERDUE, r.get(0).type());
        assertTrue(r.get(0).urgent());
        assertEquals(-10, r.get(0).daysLeft());
        assertEquals(ReminderType.BIRTHDAY, r.get(1).type());
        assertEquals(LocalDate.of(2026, 11, 5), r.get(1).eventDate());
    }

    @Test
    void probationUntilResultRecorded() {
        assertEquals(ReminderType.PROBATION_END, up(emp(List.of(), LocalDate.of(2026, 10, 30), false, null, null, null, null, true), 30).get(0).type());
        assertTrue(up(emp(List.of(), LocalDate.of(2026, 10, 30), true, null, null, null, null, true), 30).isEmpty());
    }

    @Test
    void salaryReviewAndOverride() {
        assertEquals(
            LocalDate.of(2026, 11, 1),
            up(emp(List.of(), null, false, LocalDate.of(2025, 11, 1), 12, null, null, true), 30).get(0).eventDate()
        );
        assertTrue(up(emp(List.of(), null, false, LocalDate.of(2025, 11, 1), 12, LocalDate.of(2027, 2, 1), null, true), 30).isEmpty());
    }

    @Test
    void inactiveEmployeesIgnored() {
        assertTrue(up(emp(List.of(fixed(LocalDate.of(2025, 11, 21), LocalDate.of(2026, 11, 20))), null, false, null, null, null, LocalDate.of(1990, 11, 5), false), 30).isEmpty());
    }
}

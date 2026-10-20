package vn.doit.hr.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.doit.hr.reminder.EmployeeSnapshot.Contract;

class ReminderEngineTest {

    /** Nhật ký trong bộ nhớ, ghi như job thật sẽ ghi. */
    static final class MemoryLog implements ReminderLog {
        final Map<String, Integer> smallestLead = new HashMap<>();
        final Set<String> overdue = new HashSet<>();

        void record(List<Reminder> sent) {
            for (Reminder r : sent) {
                if (r.step() >= 0) {
                    smallestLead.merge(r.eventKey(), r.step(), Math::min);
                } else {
                    overdue.add(r.eventKey() + "#" + (-r.step()));
                }
            }
        }

        @Override
        public OptionalInt smallestLeadSent(String key) {
            Integer v = smallestLead.get(key);
            return v == null ? OptionalInt.empty() : OptionalInt.of(v);
        }

        @Override
        public boolean overdueSent(String key, int day) {
            return overdue.contains(key + "#" + day);
        }
    }

    private final ReminderEngine engine = new ReminderEngine(ReminderPolicy.defaults(),
            WorkCalendar.mondayToSaturday(Set.of(LocalDate.of(2026, 9, 2))));

    private static EmployeeSnapshot emp(String code, List<Contract> contracts) {
        return new EmployeeSnapshot(code, "NV " + code, true, null, contracts, null, false, null, null, null);
    }

    private static Contract fixed(String no, String start, String end) {
        return new Contract(no, LocalDate.parse(start), LocalDate.parse(end));
    }

    private List<Reminder> run(String today, MemoryLog log, EmployeeSnapshot... e) {
        List<Reminder> r = engine.run(LocalDate.parse(today), List.of(e), log);
        log.record(r);
        return r;
    }

    @Test
    void contractMilestonesFireOnceEach() {
        MemoryLog log = new MemoryLog();
        EmployeeSnapshot a = emp("DOIT2401", List.of(fixed("01", "2026-01-01", "2026-12-31")));
        assertEquals(0, run("2026-11-15", log, a).size());              // trước mốc 45 ngày
        List<Reminder> r = run("2026-11-16", log, a);                    // đúng 45 ngày, thứ Hai
        assertEquals(1, r.size());
        assertEquals(45, r.get(0).step());
        assertTrue(r.get(0).message().contains("còn 45 ngày"));
        assertEquals(0, run("2026-11-17", log, a).size());               // không lặp
        assertEquals(30, run("2026-12-01", log, a).get(0).step());
    }

    @Test
    void missedDaysSendOnlyTheMostUrgentMilestone() {
        MemoryLog log = new MemoryLog();
        EmployeeSnapshot a = emp("DOIT2402", List.of(fixed("02", "2026-01-01", "2026-12-31")));
        List<Reminder> r = run("2026-12-17", log, a);                    // server nghỉ: mốc 45, 30, 15 đều đã qua
        assertEquals(1, r.size());
        assertEquals(15, r.get(0).step());
        assertTrue(r.get(0).message().contains("còn 14 ngày"));
        assertEquals(0, run("2026-12-18", log, a).size());               // mốc 45/30 không bị gửi bù
        assertEquals(7, run("2026-12-24", log, a).get(0).step());
    }

    @Test
    void reminderOnSundayMovesToSaturday() {
        LocalDate end = LocalDate.of(2026, 11, 29);
        assertEquals(DayOfWeek.SUNDAY, end.getDayOfWeek());
        MemoryLog log = new MemoryLog();
        EmployeeSnapshot a = emp("DOIT2403", List.of(fixed("03", "2025-11-30", "2026-11-29")));
        log.smallestLead.put("CONTRACT_EXPIRY|DOIT2403|2026-11-29", 7);
        List<Reminder> sat = run("2026-11-28", log, a);
        assertEquals(1, sat.size());
        assertEquals(0, sat.get(0).step());
        assertEquals(1, sat.get(0).daysLeft());
    }

    @Test
    void secondFixedTermContractWarnsNextMustBeIndefinite() {
        MemoryLog log = new MemoryLog();
        EmployeeSnapshot a = emp("DOIT2505", List.of(
                fixed("04", "2024-10-01", "2025-09-30"), fixed("05", "2025-10-01", "2026-12-31")));
        String msg = run("2026-11-16", log, a).get(0).message();
        assertTrue(msg.contains("không xác định thời hạn"), msg);

        EmployeeSnapshot first = emp("DOIT2508", List.of(fixed("06", "2026-01-01", "2026-12-31")));
        assertTrue(!run("2026-11-16", new MemoryLog(), first).get(0).message().contains("LƯU Ý"));
    }

    @Test
    void overdueContractCountsDownThirtyDayWindow() {
        MemoryLog log = new MemoryLog();
        EmployeeSnapshot a = emp("DOIT2509", List.of(fixed("07", "2025-10-01", "2026-09-30")));
        List<Reminder> d1 = run("2026-10-01", log, a);
        assertEquals(ReminderType.CONTRACT_OVERDUE, d1.get(0).type());
        assertTrue(d1.get(0).message().contains("30/10/2026, còn 29 ngày"), d1.get(0).message());
        assertEquals(0, run("2026-10-02", log, a).size());
        assertEquals(0, run("2026-10-03", log, a).size());
        assertEquals(1, run("2026-10-04", log, a).size());               // ngày quá hạn thứ 4
        // lỡ ngày 7 → ngày 8 gửi bù mốc 7
        assertEquals(-7, run("2026-10-08", log, a).get(0).step());
        assertTrue(run("2026-10-30", log, a).get(0).message().contains("HÔM NAY là hạn cuối"));
        assertEquals(0, run("2026-10-31", log, a).size());
    }

    @Test
    void renewedOrIndefiniteContractStopsReminders() {
        EmployeeSnapshot renewed = emp("DOIT2513", List.of(
                fixed("08", "2025-10-01", "2026-09-30"), new Contract("09", LocalDate.parse("2026-10-01"), null)));
        assertEquals(0, run("2026-10-05", new MemoryLog(), renewed).size());
        EmployeeSnapshot left = new EmployeeSnapshot("DOIT2514", "NV", false, null,
                List.of(fixed("10", "2025-10-01", "2026-09-30")), null, false, null, null, null);
        assertEquals(0, run("2026-10-05", new MemoryLog(), left).size());
    }

    @Test
    void probationWithoutResultEscalatesDaily() {
        MemoryLog log = new MemoryLog();
        EmployeeSnapshot p = new EmployeeSnapshot("DOIT2601", "NV", true, null, List.of(),
                LocalDate.parse("2026-10-15"), false, null, null, null);
        List<Reminder> r = run("2026-10-08", log, p);
        assertEquals(7, r.get(0).step());
        assertTrue(r.get(0).message().contains("Điều 27"));
        assertEquals(ReminderType.PROBATION_RESULT_MISSING, run("2026-10-16", log, p).get(0).type());
        assertEquals(1, run("2026-10-17", log, p).size());               // lặp mỗi ngày
        EmployeeSnapshot done = new EmployeeSnapshot("DOIT2602", "NV", true, null, List.of(),
                LocalDate.parse("2026-10-15"), true, null, null, null);
        assertEquals(0, run("2026-10-16", new MemoryLog(), done).size());
    }

    @Test
    void salaryReviewFromLastChangePlusCycle() {
        EmployeeSnapshot s = new EmployeeSnapshot("DOIT2401", "NV", true, null, List.of(), null, false,
                LocalDate.parse("2025-11-01"), 12, null);
        List<Reminder> r = run("2026-10-02", new MemoryLog(), s);
        assertEquals(ReminderType.SALARY_REVIEW, r.get(0).type());
        assertEquals(LocalDate.parse("2026-11-01"), r.get(0).eventDate());
        assertEquals(ReminderType.SALARY_REVIEW_OVERDUE, run("2026-11-02", new MemoryLog(), s).get(0).type());
    }

    @Test
    void birthdayOnFeb29InNonLeapYear() {
        EmployeeSnapshot b = new EmployeeSnapshot("DOIT2402", "Nhân viên B", true, LocalDate.parse("1996-02-29"),
                List.of(), null, false, null, null, null);
        List<Reminder> r = run("2027-02-28", new MemoryLog(), b);
        assertEquals(LocalDate.parse("2027-02-28"), r.get(0).eventDate());
        assertTrue(r.get(0).message().contains("hôm nay"));
        assertTrue(!r.get(0).urgent());
    }

    @Test
    void digestGroupsPerEmailAndPutsUrgentFirst() {
        MemoryLog log = new MemoryLog();
        EmployeeSnapshot overdue = emp("DOIT2509", List.of(fixed("07", "2025-10-01", "2026-09-30")));
        EmployeeSnapshot bday = new EmployeeSnapshot("DOIT2401", "Nhân viên A", true, LocalDate.parse("1995-10-01"),
                List.of(), null, false, null, null, null);
        List<Reminder> r = engine.run(LocalDate.parse("2026-10-01"), List.of(bday, overdue), log);
        Map<String, List<Reminder>> mail = ReminderDigest.byEmail(r, (role, code) -> switch (role) {
            case HR -> List.of("hr@doit.com.vn");
            case MANAGER -> List.of("HR@doit.com.vn");               // trùng người → không nhân đôi
            case DIRECTOR -> List.of("giamdoc@doit.com.vn");
            default -> List.of();
        });
        assertEquals(Set.of("hr@doit.com.vn", "giamdoc@doit.com.vn"), mail.keySet());
        assertEquals(2, mail.get("hr@doit.com.vn").size());
        assertEquals(1, mail.get("giamdoc@doit.com.vn").size());
        String body = ReminderDigest.renderText(LocalDate.parse("2026-10-01"), mail.get("hr@doit.com.vn"));
        System.out.println(ReminderDigest.subject(LocalDate.parse("2026-10-01"), mail.get("hr@doit.com.vn")));
        System.out.println(body);
        assertTrue(body.indexOf("CẦN XỬ LÝ GẤP") < body.indexOf("SINH NHẬT"));
    }
}

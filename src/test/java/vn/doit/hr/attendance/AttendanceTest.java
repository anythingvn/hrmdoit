package vn.doit.hr.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.AssignmentHistory;
import vn.doit.hr.assignment.WorkLocationType;
import vn.doit.hr.attendance.PunchRequest.Type;
import vn.doit.hr.attendance.PunchVerdict.Method;
import vn.doit.hr.attendance.PunchVerdict.Status;
import vn.doit.hr.attendance.Timesheet.DayStatus;
import vn.doit.hr.attendance.Timesheet.Punch;

class AttendanceTest {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String NV = "DOIT2401";
    static final String DEVICE = "dev-001";

    static final GeoPoint OFFICE_CENTER = new GeoPoint(21.0278, 105.8360);
    static final WorkSite OFFICE = new WorkSite("HQ", "Văn phòng DOIT", OFFICE_CENTER, 100,
            Set.of("AA-BB-CC-DD-EE-01"), Set.of("203.0.113.10"));
    static final WorkSite ABC = new WorkSite("ABC", "Ngân hàng ABC", new GeoPoint(21.0285, 105.8542), 80, Set.of(), Set.of());
    static final WorkSite DANANG = new WorkSite("DN", "ABC Đà Nẵng", new GeoPoint(16.0544, 108.2022), 150, Set.of(), Set.of());

    static final SiteDirectory SITES = SiteDirectory.of(List.of(OFFICE),
            Map.of("Ngân hàng ABC", List.of(ABC), "Chi nhánh Đà Nẵng của ABC", List.of(DANANG)));

    static final PunchValidator VALIDATOR = new PunchValidator(PunchValidator.Policy.defaults(), SITES,
            code -> code.equals(NV) ? Optional.of(DEVICE) : Optional.empty());

    static Instant at(String date, String time) {
        return LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(VN).toInstant();
    }

    static AssignmentHistory history() {
        AssignmentHistory h = AssignmentHistory.of(NV, List.of(new Assignment(1, NV, WorkLocationType.VAN_PHONG, null, null,
                LocalDate.parse("2024-09-01"), null, null, null)));
        h = h.add(new Assignment(0, NV, WorkLocationType.KHACH_HANG, "Ngân hàng ABC", null,
                LocalDate.parse("2026-11-01"), LocalDate.parse("2027-01-31"), null, null)).history();
        return h.add(new Assignment(0, NV, WorkLocationType.CONG_TAC, "Chi nhánh Đà Nẵng của ABC", null,
                LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-12"), null, null)).history();
    }

    static PunchRequest gps(Instant t, GeoPoint p, double accuracy) {
        return new PunchRequest(NV, DEVICE, Type.IN, t, p, accuracy, false, null, "198.51.100.7");
    }

    static PunchVerdict check(PunchRequest r, Instant serverNow) {
        return VALIDATOR.validate(r, serverNow, history());
    }

    @Test
    void haversineMatchesReference() {
        assertEquals(1890.58, OFFICE_CENTER.distanceMeters(ABC.center()), 0.5);   // tính độc lập bằng Python
        assertEquals(100.08, OFFICE_CENTER.distanceMeters(new GeoPoint(21.0287, 105.8360)), 0.05);
    }

    @Test
    void gpsInsideBorderlineFarAndPoorAccuracy() {
        Instant t = at("2026-10-19", "07:58");
        PunchVerdict in = check(gps(t, new GeoPoint(21.0279, 105.8360), 15), t);
        assertEquals(Status.ACCEPTED, in.status());
        assertEquals(Method.GPS, in.method());
        assertEquals("Đã chấm công tại Văn phòng DOIT", in.message());

        PunchVerdict edge = check(gps(t, new GeoPoint(21.0287, 105.8360), 20), t);   // 100,08 m; cho phép 100 m; sai số 20 m
        assertEquals(Status.NEEDS_REVIEW, edge.status());
        assertTrue(edge.message().contains("sát ranh giới"), edge.message());

        PunchVerdict far = check(gps(t, ABC.center(), 10), t);                        // 1,89 km
        assertEquals(Status.REJECTED, far.status());
        assertTrue(far.message().contains("khoảng 1891 m"), far.message());

        PunchVerdict blurry = check(gps(t, new GeoPoint(21.0279, 105.8360), 250), t);
        assertEquals(Status.NEEDS_REVIEW, blurry.status());
        assertTrue(blurry.message().contains("sai số GPS 250 m"), blurry.message());
    }

    @Test
    void wifiOrIpBeatsBadGps() {
        Instant t = at("2026-10-19", "08:01");
        PunchRequest wifi = new PunchRequest(NV, DEVICE, Type.IN, t, ABC.center(), 500.0, false, "aa:bb:cc:dd:ee:01", null);
        PunchVerdict v = check(wifi, t);
        assertEquals(Status.ACCEPTED, v.status());
        assertEquals(Method.WIFI, v.method());

        PunchRequest ip = new PunchRequest(NV, DEVICE, Type.IN, t, null, null, false, null, "203.0.113.10");
        assertEquals(Method.IP, check(ip, t).method());
        assertEquals(Status.ACCEPTED, check(ip, t).status());

        PunchRequest nothing = new PunchRequest(NV, DEVICE, Type.IN, t, null, null, false, null, "198.51.100.7");
        assertEquals(Status.REJECTED, check(nothing, t).status());
    }

    @Test
    void antiFraudChecksComeFirst() {
        Instant t = at("2026-10-19", "08:00");
        GeoPoint inside = new GeoPoint(21.0279, 105.8360);
        PunchVerdict mock = check(new PunchRequest(NV, DEVICE, Type.IN, t, inside, 5.0, true, "aa:bb:cc:dd:ee:01", null), t);
        assertEquals(Status.REJECTED, mock.status());
        assertTrue(mock.message().contains("giả lập"));

        PunchVerdict otherPhone = check(new PunchRequest(NV, "dev-999", Type.IN, t, inside, 5.0, false, null, null), t);
        assertEquals(Status.REJECTED, otherPhone.status());

        PunchVerdict clock = check(gps(at("2026-10-19", "07:45"), inside, 5), t);   // máy chạy chậm 15 phút
        assertEquals(Status.REJECTED, clock.status());
        assertTrue(clock.message().contains("lệch 15 phút"), clock.message());

        AssignmentHistory newbie = AssignmentHistory.of("DOIT2601", List.of(new Assignment(1, "DOIT2601",
                WorkLocationType.VAN_PHONG, null, null, LocalDate.parse("2026-10-01"), null, null, null)));
        PunchVerdict firstTime = VALIDATOR.validate(new PunchRequest("DOIT2601", "dev-new", Type.IN, t, inside, 5.0,
                false, null, null), t, newbie);
        assertEquals(Status.NEEDS_REVIEW, firstTime.status());
        assertTrue(firstTime.message().contains("thiết bị mới"));
    }

    @Test
    void allowedPlaceFollowsAssignmentHistory() {
        // Đang onsite ở ABC: chấm ở văn phòng công ty bị từ chối, chấm ở ABC được chấp nhận
        Instant nov = at("2026-11-16", "08:00");
        PunchVerdict atOffice = check(gps(nov, new GeoPoint(21.0279, 105.8360), 10), nov);
        assertEquals(Status.REJECTED, atOffice.status());
        assertTrue(atOffice.message().contains("Ngân hàng ABC"), atOffice.message());
        assertEquals(Status.ACCEPTED, check(gps(nov, ABC.center(), 10), nov).status());

        // Ngày đi công tác Đà Nẵng: điểm hợp lệ là chi nhánh Đà Nẵng
        Instant trip = at("2026-12-11", "08:00");
        PunchVerdict dn = check(gps(trip, new GeoPoint(16.0550, 108.2025), 20), trip);
        assertEquals(Status.ACCEPTED, dn.status());
        assertEquals("DN", dn.site().id());

        // Nơi chưa khai báo điểm chấm công → chờ duyệt, không từ chối
        AssignmentHistory unknown = history().add(new Assignment(0, NV, WorkLocationType.CONG_TAC, "Đối tác XYZ", null,
                LocalDate.parse("2027-02-10"), LocalDate.parse("2027-02-11"), null, null)).history();
        Instant feb = at("2027-02-10", "08:00");
        PunchVerdict v = VALIDATOR.validate(gps(feb, new GeoPoint(10.77, 106.70), 10), feb, unknown);
        assertEquals(Status.NEEDS_REVIEW, v.status());
        assertTrue(v.message().contains("chưa khai báo điểm chấm công"), v.message());

        // Làm từ xa: chấp nhận không cần vị trí
        AssignmentHistory remote = AssignmentHistory.of(NV, List.of(new Assignment(1, NV, WorkLocationType.TU_XA, null, null,
                LocalDate.parse("2026-01-01"), null, null, null)));
        PunchVerdict r = VALIDATOR.validate(new PunchRequest(NV, DEVICE, Type.IN, nov, null, null, false, null, null), nov, remote);
        assertEquals(Status.ACCEPTED, r.status());
        assertEquals(Method.REMOTE, r.method());
    }

    // ------------------------------------------------------------------ bảng công

    static final WorkSchedule SCHEDULE = WorkSchedule.doitDefault(Set.of(LocalDate.parse("2026-10-20")));

    static Timesheet.Day day(String date, String in, String out) {
        List<Punch> p = new java.util.ArrayList<>();
        if (in != null) {
            p.add(new Punch(at(date, in), Type.IN));
        }
        if (out != null) {
            p.add(new Punch(at(date, out), Type.OUT));
        }
        return Timesheet.day(LocalDate.parse(date), p, SCHEDULE, VN);
    }

    @Test
    void dailyCredits() {
        Timesheet.Day full = day("2026-10-19", "07:55", "17:05");          // thứ Hai
        assertEquals(DayStatus.PRESENT, full.status());
        assertEquals(480, full.workedMinutes());
        assertEquals(0, new BigDecimal("1.00").compareTo(full.credit()));
        assertEquals(0, full.lateMinutes());

        assertEquals(0, day("2026-10-21", "08:04", "17:00").lateMinutes());   // trong 5 phút cho phép
        Timesheet.Day late = day("2026-10-21", "08:20", "17:00");
        assertEquals(20, late.lateMinutes());
        assertEquals(460, late.workedMinutes());
        assertEquals(0, new BigDecimal("0.95").compareTo(late.credit()));     // 460/480 = 0,958 → làm tròn xuống

        Timesheet.Day lunch = day("2026-10-22", "08:00", "12:30");           // về lúc 12:30, nghỉ trưa không tính
        assertEquals(240, lunch.workedMinutes());
        assertEquals(270, lunch.earlyLeaveMinutes());

        Timesheet.Day sat = day("2026-10-24", "08:00", "12:00");             // thứ Bảy nửa buổi
        assertEquals(0, new BigDecimal("0.50").compareTo(sat.credit()));

        assertEquals(DayStatus.MISSING_OUT, day("2026-10-23", "08:00", null).status());
        assertEquals(DayStatus.ABSENT, day("2026-10-23", null, null).status());
        assertEquals(DayStatus.OFF_DAY, day("2026-10-20", null, null).status());            // ngày lễ khai báo
        assertEquals(DayStatus.WORKED_ON_OFF_DAY, day("2026-10-25", "09:00", "11:00").status()); // Chủ nhật
    }

    @Test
    void monthlyTimesheet() {
        List<Punch> punches = new java.util.ArrayList<>();
        // Đi làm đủ mọi ngày làm việc tháng 10/2026, trừ: 05/10 vắng, 06/10 muộn 30 phút, 07/10 quên chấm ra
        for (LocalDate d = LocalDate.parse("2026-10-01"); !d.isAfter(LocalDate.parse("2026-10-31")); d = d.plusDays(1)) {
            if (SCHEDULE.shiftOn(d).isEmpty() || d.equals(LocalDate.parse("2026-10-05"))) {
                continue;
            }
            boolean sat = d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY;
            punches.add(new Punch(at(d.toString(), d.toString().equals("2026-10-06") ? "08:30" : "07:58"), Type.IN));
            if (!d.toString().equals("2026-10-07")) {
                punches.add(new Punch(at(d.toString(), sat ? "12:01" : "17:02"), Type.OUT));
            }
        }
        Timesheet.Month m = Timesheet.month(YearMonth.of(2026, 10), punches, SCHEDULE, VN);
        // 22 ngày thứ Hai–Sáu (trừ 20/10 nghỉ = 21) + 5 thứ Bảy × 0,5 = 23,5
        assertEquals(0, new BigDecimal("23.5").compareTo(m.standardCredit()), m.standardCredit().toString());
        // 23,5 − 1 (vắng 05/10) − 1 (07/10 thiếu giờ ra) − 0,07 (06/10: 450/480 = 0,9375 → 0,93)
        assertEquals(0, new BigDecimal("21.43").compareTo(m.workedCredit()), m.workedCredit().toString());
        assertEquals(1, m.absentDays());
        assertEquals(1, m.lateCount());
        assertEquals(30, m.lateMinutes());
        assertEquals(List.of(LocalDate.parse("2026-10-07")), m.missingPunchDays());
    }
}

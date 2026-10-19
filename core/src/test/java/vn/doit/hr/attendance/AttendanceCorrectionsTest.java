package vn.doit.hr.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.doit.hr.attendance.AttendanceTest.SCHEDULE;
import static vn.doit.hr.attendance.AttendanceTest.VN;
import static vn.doit.hr.attendance.AttendanceTest.at;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.doit.hr.attendance.AttendanceCorrections.LockPolicy;
import vn.doit.hr.attendance.AttendanceCorrections.ManualPunch;
import vn.doit.hr.attendance.AttendanceCorrections.OrgChart;
import vn.doit.hr.attendance.AttendanceCorrections.Request;
import vn.doit.hr.attendance.AttendanceCorrections.RequestKind;
import vn.doit.hr.attendance.AttendanceCorrections.RequestStatus;
import vn.doit.hr.attendance.AttendanceCorrections.StoredPunch;
import vn.doit.hr.attendance.PunchRequest.Type;
import vn.doit.hr.attendance.PunchVerdict.Status;
import vn.doit.hr.attendance.Timesheet.DayStatus;
import vn.doit.hr.attendance.Timesheet.Punch;
import vn.doit.hr.attendance.Timesheet.Punch.Source;

class AttendanceCorrectionsTest {

    static final String NV = "DOIT2401";
    static final String MANAGER = "DOIT2001";
    static final String HR = "DOIT2002";
    static final String OTHER = "DOIT2402";

    static final OrgChart ORG = new OrgChart() {
        @Override
        public boolean isManagerOf(String approver, String employee) {
            return approver.equals(MANAGER) && (employee.equals(NV) || employee.equals(OTHER));
        }

        @Override
        public boolean isHr(String code) {
            return code.equals(HR);
        }
    };
    static final LockPolicy LOCK = new LockPolicy(5);   // khóa công tháng trước sau ngày 05

    static void expectRule(Runnable r, String text) {
        try {
            r.run();
        } catch (AttendanceRuleException e) {
            assertTrue(e.getMessage().contains(text), e.getMessage());
            return;
        }
        throw new AssertionError("Phải báo lỗi: " + text);
    }

    static Request forgotOut(Instant now) {
        return AttendanceCorrections.submit(NV, LocalDate.parse("2026-10-23"), RequestKind.BO_SUNG,
                List.of(at("2026-10-23", "17:05")), List.of(Type.OUT), "Quên chấm ra, có camera cửa ra vào xác nhận", now, LOCK, VN);
    }

    @Test
    void forgottenCheckOutIsFixedByApprovedRequest() {
        Instant now = at("2026-10-24", "09:00");
        StoredPunch in = new StoredPunch(1, NV, new Punch(at("2026-10-23", "07:58"), Type.IN), Status.ACCEPTED, null, null, null);

        // Trước khi giải trình: thiếu giờ ra → 0 công
        assertEquals(DayStatus.MISSING_OUT, Timesheet.day(LocalDate.parse("2026-10-23"),
                AttendanceCorrections.effectivePunches(List.of(in), List.of(), List.of()), SCHEDULE, VN).status());

        Request r = forgotOut(now);
        assertEquals(RequestStatus.CHO_DUYET, r.status());
        Request ok = AttendanceCorrections.decide(r, MANAGER, true, null, now, ORG, LOCK, VN);
        assertEquals(RequestStatus.DA_DUYET, ok.status());
        assertEquals(MANAGER, ok.decidedBy());

        List<Punch> eff = AttendanceCorrections.effectivePunches(List.of(in), List.of(ok), List.of());
        Timesheet.Day day = Timesheet.day(LocalDate.parse("2026-10-23"), eff, SCHEDULE, VN);
        assertEquals(DayStatus.PRESENT, day.status());
        assertEquals(0, BigDecimal.ONE.compareTo(day.credit()));
        assertEquals(Source.GIAI_TRINH, eff.get(1).source());
    }

    @Test
    void approvalRules() {
        Instant now = at("2026-10-24", "09:00");
        Request r = forgotOut(now);
        expectRule(() -> AttendanceCorrections.decide(r, NV, true, null, now, ORG, LOCK, VN), "tự duyệt");
        expectRule(() -> AttendanceCorrections.decide(r, OTHER, true, null, now, ORG, LOCK, VN), "Chỉ quản lý trực tiếp hoặc HR");
        expectRule(() -> AttendanceCorrections.decide(r, MANAGER, false, " ", now, ORG, LOCK, VN), "Từ chối phải ghi lý do");
        Request byHr = AttendanceCorrections.decide(r, HR, false, "Không có bằng chứng", now, ORG, LOCK, VN);
        assertEquals(RequestStatus.TU_CHOI, byHr.status());
        expectRule(() -> AttendanceCorrections.decide(byHr, MANAGER, true, null, now, ORG, LOCK, VN), "đã được xử lý");
        assertTrue(AttendanceCorrections.effectivePunches(List.of(), List.of(byHr), List.of()).isEmpty());
    }

    @Test
    void submitValidation() {
        Instant now = at("2026-10-24", "09:00");
        expectRule(() -> AttendanceCorrections.submit(NV, LocalDate.parse("2026-10-23"), RequestKind.BO_SUNG,
                List.of(at("2026-10-23", "17:00")), List.of(Type.OUT), "  ", now, LOCK, VN), "lý do");
        expectRule(() -> AttendanceCorrections.submit(NV, LocalDate.parse("2026-10-26"), RequestKind.BO_SUNG,
                List.of(at("2026-10-26", "08:00")), List.of(Type.IN), "Xin trước", now, LOCK, VN), "chưa tới");
        expectRule(() -> AttendanceCorrections.submit(NV, LocalDate.parse("2026-10-23"), RequestKind.BO_SUNG,
                List.of(at("2026-10-22", "17:00")), List.of(Type.OUT), "Nhầm ngày", now, LOCK, VN), "nằm trong ngày");
        // Công tháng 09 khóa sau 05/10: ngày 05/10 vẫn sửa được, 06/10 thì không
        AttendanceCorrections.submit(NV, LocalDate.parse("2026-09-30"), RequestKind.GIAI_TRINH,
                List.of(at("2026-09-30", "08:00")), List.of(Type.IN), "GPS lỗi", at("2026-10-05", "17:00"), LOCK, VN);
        expectRule(() -> AttendanceCorrections.submit(NV, LocalDate.parse("2026-09-30"), RequestKind.GIAI_TRINH,
                List.of(at("2026-09-30", "08:00")), List.of(Type.IN), "GPS lỗi", at("2026-10-06", "08:00"), LOCK, VN), "đã khóa");
    }

    @Test
    void managerReviewsPendingAppPunchAndCanPunchManually() {
        Instant now = at("2026-10-19", "10:00");
        StoredPunch pending = new StoredPunch(7, NV, new Punch(at("2026-10-19", "08:01"), Type.IN), Status.NEEDS_REVIEW, null, null, null);
        StoredPunch approved = AttendanceCorrections.review(pending, MANAGER, true, "Đúng là ở văn phòng, GPS lệch", now, ORG, LOCK, VN);
        assertEquals(Status.ACCEPTED, approved.status());
        expectRule(() -> AttendanceCorrections.review(approved, MANAGER, true, null, now, ORG, LOCK, VN), "đang chờ duyệt");

        ManualPunch out = AttendanceCorrections.manualPunch(NV, at("2026-10-19", "17:00"), Type.OUT, MANAGER,
                "Điện thoại hết pin", at("2026-10-19", "17:30"), ORG, LOCK, VN);
        assertEquals(Source.DUYET_TAY, out.punch().source());
        expectRule(() -> AttendanceCorrections.manualPunch(NV, at("2026-10-19", "17:00"), Type.OUT, OTHER,
                "x", at("2026-10-19", "17:30"), ORG, LOCK, VN), "Chỉ quản lý");
        expectRule(() -> AttendanceCorrections.manualPunch(NV, at("2026-10-19", "17:00"), Type.OUT, MANAGER,
                "", at("2026-10-19", "17:30"), ORG, LOCK, VN), "phải ghi lý do");

        List<Punch> eff = AttendanceCorrections.effectivePunches(List.of(approved), List.of(), List.of(out));
        Timesheet.Day day = Timesheet.day(LocalDate.parse("2026-10-19"), eff, SCHEDULE, VN);
        assertEquals(DayStatus.PRESENT, day.status());
        assertEquals(480, day.workedMinutes());   // vào 08:01, trong 5 phút cho phép → coi như vào 08:00
        assertEquals(0, BigDecimal.ONE.compareTo(day.credit()));
        assertEquals(0, day.lateMinutes());
    }
}

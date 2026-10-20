package vn.doit.hr.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.doit.hr.assignment.AssignmentChange.Kind;
import vn.doit.hr.assignment.AssignmentHistory.Segment;
import vn.doit.hr.reminder.WorkCalendar;

class AssignmentHistoryTest {

    private static final String NV = "DOIT2401";
    private static final String ABC = "Ngân hàng ABC";

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static Assignment office(String from) {
        return new Assignment(1, NV, WorkLocationType.VAN_PHONG, null, null, d(from), null, null, null);
    }

    private static Assignment onsite(String unit, String from, String to) {
        return new Assignment(0, NV, WorkLocationType.KHACH_HANG, unit, null, d(from), to == null ? null : d(to), "15/2026/QĐ-DOIT", null);
    }

    private static Assignment trip(String unit, String from, String to) {
        return new Assignment(0, NV, WorkLocationType.CONG_TAC, unit, null, d(from), d(to), null, null);
    }

    private static AssignmentHistory base() {
        return AssignmentHistory.of(NV, List.of(office("2024-09-01")));
    }

    private static String place(AssignmentHistory h, String date) {
        return h.locationOn(d(date)).map(Assignment::placeLabel).orElse("-");
    }

    private static void expectConflict(Runnable r, String text) {
        try {
            r.run();
        } catch (AssignmentConflictException e) {
            assertTrue(e.getMessage().contains(text), e.getMessage());
            return;
        }
        throw new AssertionError("Phải báo lỗi: " + text);
    }

    @Test
    void fixedTermOnsiteClosesOfficeAndResumesAfter() {
        AssignmentHistory.Result r = base().add(onsite(ABC, "2026-11-01", "2027-01-31"));
        assertEquals(List.of(Kind.CLOSED, Kind.CREATED, Kind.RESUMED), r.changes().stream().map(AssignmentChange::kind).toList());
        AssignmentHistory h = r.history();
        assertEquals(d("2026-10-31"), h.entries().get(0).to());
        assertEquals(1L, h.entries().get(0).id());                                  // bản ghi cũ giữ id, chỉ chốt ngày
        assertEquals("Văn phòng công ty", place(h, "2026-10-31"));
        assertEquals("Làm việc tại khách hàng/dự án – " + ABC, place(h, "2026-12-15"));
        assertEquals("Văn phòng công ty", place(h, "2027-02-01"));
        assertTrue(h.entries().get(2).openEnded());
        r.changes().forEach(System.out::println);
    }

    @Test
    void openEndedTransferDoesNotResume() {
        AssignmentHistory h = base().add(onsite(ABC, "2026-11-01", null)).history();
        assertEquals(2, h.entries().size());
        assertEquals("Làm việc tại khách hàng/dự án – " + ABC, place(h, "2030-01-01"));
    }

    @Test
    void tripOverlaysPrimaryAndTimelineSplits() {
        AssignmentHistory h = base().add(onsite(ABC, "2026-11-01", "2027-01-31")).history()
                .add(trip("Chi nhánh Đà Nẵng của ABC", "2026-12-10", "2026-12-12")).history();
        assertEquals("Đi công tác – Chi nhánh Đà Nẵng của ABC", place(h, "2026-12-11"));
        List<Segment> dec = h.timeline(d("2026-12-01"), d("2026-12-31"));
        assertEquals(3, dec.size());
        assertEquals(d("2026-12-09"), dec.get(0).to());
        assertEquals(d("2026-12-13"), dec.get(2).from());
        assertEquals(dec.get(0).assignment(), dec.get(2).assignment());              // về lại đúng nơi onsite

        Map<String, Long> days = h.workingDaysByPlace(d("2026-12-01"), d("2026-12-31"), WorkCalendar.mondayToSaturday(Set.of()));
        // Tháng 12/2026: 31 ngày − 4 Chủ nhật = 27 ngày làm việc; công tác 10–12/12 (thứ Năm–thứ Bảy) = 3
        assertEquals(Long.valueOf(24), days.get("Làm việc tại khách hàng/dự án – " + ABC));
        assertEquals(Long.valueOf(3), days.get("Đi công tác – Chi nhánh Đà Nẵng của ABC"));
    }

    @Test
    void rejectsInvalidAndOverlappingEntries() {
        AssignmentHistory h = base().add(trip("Đà Nẵng", "2026-12-10", "2026-12-12")).history();
        expectConflict(() -> h.add(trip("Huế", "2026-12-12", "2026-12-14")), "Trùng thời gian với đợt công tác");
        expectConflict(() -> h.add(new Assignment(0, NV, WorkLocationType.CONG_TAC, "Huế", null, d("2027-01-05"), null, null, null)),
                "phải có ngày về");
        expectConflict(() -> h.add(onsite(ABC, "2026-11-10", "2026-11-01")), "trước ngày bắt đầu");
        expectConflict(() -> h.add(onsite("", "2026-11-10", null)), "đơn vị");
        // chèn nơi chính bắt đầu trước giai đoạn chính hiện có → không được viết lại quá khứ ngầm
        expectConflict(() -> h.add(onsite(ABC, "2024-01-01", null)), "Trùng thời gian");
    }

    @Test
    void endingOnsiteEarlyPullsReturnDateForward() {
        AssignmentHistory h = base().add(onsite(ABC, "2026-11-01", "2027-01-31")).history();
        Assignment abc = h.entries().get(1);
        AssignmentHistory.Result r = h.changeEnd(abc, d("2026-12-20"));
        assertEquals(List.of(Kind.END_CHANGED, Kind.ADJUSTED), r.changes().stream().map(AssignmentChange::kind).toList());
        assertEquals("Văn phòng công ty", place(r.history(), "2026-12-21"));
        assertTrue(r.history().gaps(d("2024-09-01"), d("2027-12-31")).isEmpty(), "không để hở ngày nào");
        // gia hạn thêm cũng dời ngày quay về văn phòng
        AssignmentHistory ext = h.changeEnd(abc, d("2027-03-31")).history();
        assertEquals("Văn phòng công ty", place(ext, "2027-04-01"));
        assertEquals("Làm việc tại khách hàng/dự án – " + ABC, place(ext, "2027-03-31"));
    }

    @Test
    void reportsGapsAndWhoIsWhere() {
        AssignmentHistory withGap = AssignmentHistory.of("DOIT2402", List.of(
                new Assignment(5, "DOIT2402", WorkLocationType.VAN_PHONG, null, null, d("2026-01-01"), d("2026-05-31"), null, null),
                new Assignment(6, "DOIT2402", WorkLocationType.TU_XA, null, null, d("2026-06-15"), null, null, null)));
        List<Segment> gaps = withGap.gaps(d("2026-01-01"), d("2026-12-31"));
        assertEquals(1, gaps.size());
        assertEquals(d("2026-06-01"), gaps.get(0).from());
        assertEquals(d("2026-06-14"), gaps.get(0).to());

        AssignmentHistory a = base().add(onsite(ABC, "2026-11-01", null)).history();
        Map<String, List<String>> who = AssignmentReport.whoIsWhere(List.of(a, withGap), d("2026-11-15"));
        assertEquals(List.of("DOIT2401"), who.get("Làm việc tại khách hàng/dự án – " + ABC));
        assertEquals(List.of("DOIT2402"), who.get("Làm việc từ xa"));
    }

    @Test
    void decisionFieldsMatchTemplate() {
        AssignmentDecision dec = new AssignmentDecision("15/2026/QĐ-DOIT", d("2026-10-28"), "Trần Ngọc Nga", "Giám đốc",
                "Nguyễn Văn A", NV, "Lập trình viên", "Phòng Phát triển phần mềm", "DOIT/2026/HĐLĐ/0001",
                onsite(ABC, "2026-11-01", "2027-01-31"), "Triển khai dự án", "Giữ nguyên lương");
        Map<String, String> f = dec.toFields();
        assertEquals("28", f.get("ngay"));
        assertEquals("cử nhân sự làm việc tại khách hàng", f.get("trichYeu"));
        assertEquals("31/01/2027", f.get("denNgay"));
        assertEquals("GIÁM ĐỐC", f.get("chucVuKy"));
        assertEquals("", f.get("diaChi"));
        assertEquals(19, f.size());
    }
}

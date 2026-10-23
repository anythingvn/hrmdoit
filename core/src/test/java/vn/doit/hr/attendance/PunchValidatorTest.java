package vn.doit.hr.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
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

/** Chính sách xác minh cho web (nhân viên tự chấm công trên trình duyệt). */
class PunchValidatorTest {

    static final String NV = "DOIT2401";
    static final GeoPoint CENTER = new GeoPoint(21.0278, 105.8360);
    static final WorkSite OFFICE = new WorkSite("HQ", "Văn phòng DOIT", CENTER, 200, Set.of(), Set.of("203.0.113.0/24"));
    static final SiteDirectory SITES = SiteDirectory.of(List.of(OFFICE), Map.of());
    static final PunchValidator WEB = new PunchValidator(PunchValidator.Policy.web(), SITES, code -> Optional.empty());
    static final Instant NOW = Instant.parse("2026-10-19T01:00:00Z");

    static AssignmentHistory history() {
        return AssignmentHistory.of(NV, List.of(new Assignment(1, NV, WorkLocationType.VAN_PHONG, null, null,
                LocalDate.parse("2024-09-01"), null, null, null)));
    }

    static PunchRequest req(Instant clientTime, GeoPoint loc, Double acc, String ip) {
        return new PunchRequest(NV, null, Type.IN, clientTime, loc, acc, false, null, ip);
    }

    /** Điểm cách tâm văn phòng đúng (xấp xỉ) {@code meters} mét về phía bắc. */
    static GeoPoint north(double meters) {
        return new GeoPoint(CENTER.latitude() + meters / 111_195.0, CENTER.longitude());
    }

    @Test
    void webPolicySkipsDeviceAndSkew() {
        PunchRequest r = req(NOW.minusSeconds(2 * 3600), north(50), 20.0, "198.51.100.7");
        PunchVerdict v = WEB.validate(r, NOW, history());
        assertEquals(Status.ACCEPTED, v.status());
        assertEquals(Method.GPS, v.method());
    }

    @Test
    void officeIpCidrAccepted() {
        PunchVerdict v = WEB.validate(req(null, null, null, "203.0.113.77"), NOW, history());
        assertEquals(Status.ACCEPTED, v.status());
        assertEquals(Method.IP, v.method());
    }

    @Test
    void rejectMessageKm() {
        PunchVerdict far = WEB.validate(req(null, north(2400), 10.0, null), NOW, history());
        assertEquals(Status.REJECTED, far.status());
        assertTrue(far.message().contains("2,4 km"), far.message());
        assertTrue(far.message().contains("Văn phòng DOIT"), far.message());
        assertTrue(far.message().contains("Hôm nay bạn phải chấm tại"), far.message());

        PunchVerdict near = WEB.validate(req(null, north(650), 10.0, null), NOW, history());
        assertEquals(Status.REJECTED, near.status());
        assertTrue(near.message().contains("650 m"), near.message());
    }

    @Test
    void noLocationNoIpIsReview() {
        PunchVerdict v = WEB.validate(req(null, null, null, "198.51.100.7"), NOW, history());
        assertEquals(Status.NEEDS_REVIEW, v.status());
        assertTrue(v.message().contains("Không có vị trí"), v.message());
    }

    @Test
    void siteWithoutCoordinatesAndIpIsReview() {
        WorkSite bare = new WorkSite("HQ", "Văn phòng DOIT", null, 200, Set.of(), Set.of());
        PunchValidator v = new PunchValidator(PunchValidator.Policy.web(), SiteDirectory.of(List.of(bare), Map.of()),
                code -> Optional.empty());
        PunchVerdict out = v.validate(req(null, north(10), 10.0, "198.51.100.7"), NOW, history());
        assertEquals(Status.NEEDS_REVIEW, out.status());
        assertTrue(out.message().contains("Địa điểm chưa khai báo"), out.message());
    }

    @Test
    void borderlineAndBlurryAreReviewNotRejected() {
        PunchVerdict edge = WEB.validate(req(null, north(210), 30.0, null), NOW, history());
        assertEquals(Status.NEEDS_REVIEW, edge.status());
        PunchVerdict blurry = WEB.validate(req(null, north(10), 250.0, null), NOW, history());
        assertEquals(Status.NEEDS_REVIEW, blurry.status());
    }
}

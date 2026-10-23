package vn.doit.hr.attendance;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.AssignmentHistory;
import vn.doit.hr.assignment.WorkLocationType;
import vn.doit.hr.attendance.PunchVerdict.Method;
import vn.doit.hr.attendance.PunchVerdict.Status;

/**
 * Kiểm tra một lần chấm công. Thứ tự:
 * <ol>
 *   <li>Thiết bị: mỗi nhân viên gắn một thiết bị; thiết bị mới chờ HR duyệt, thiết bị lạ bị từ chối.</li>
 *   <li>Vị trí giả lập (mock location) → từ chối.</li>
 *   <li>Giờ điện thoại lệch giờ server quá ngưỡng → từ chối (dấu hiệu chỉnh giờ máy).</li>
 *   <li>Hôm nay nhân viên phải làm ở đâu: lấy từ lịch sử vị trí làm việc (đang công tác thì là nơi công tác).</li>
 *   <li>Làm từ xa → chấp nhận, không cần vị trí.</li>
 *   <li>Đúng WiFi hoặc đúng IP mạng của điểm chấm công → chấp nhận (tin cậy hơn GPS trong tòa nhà).</li>
 *   <li>GPS: trong bán kính → chấp nhận; sát ranh giới hoặc sai số lớn → chờ duyệt; xa hơn → từ chối.</li>
 * </ol>
 */
public class PunchValidator {

    /** Thiết bị đã gắn với nhân viên. */
    @FunctionalInterface
    public interface DeviceRegistry {
        Optional<String> boundDevice(String employeeCode);
    }

    /**
     * @param maxClockSkew null = không kiểm tra lệch giờ
     * @param webMode chấm trên trình duyệt: không kiểm tra thiết bị, không có WiFi; thiếu vị trí thì chờ duyệt
     *            (không từ chối); câu từ chối nêu khoảng cách theo m/km
     */
    public record Policy(double maxAccuracyMeters, Duration maxClockSkew, ZoneId zone, boolean webMode) {
        public Policy(double maxAccuracyMeters, Duration maxClockSkew, ZoneId zone) {
            this(maxAccuracyMeters, maxClockSkew, zone, false);
        }

        public static Policy defaults() {
            return new Policy(100, Duration.ofMinutes(5), ZoneId.of("Asia/Ho_Chi_Minh"));
        }

        /** Nhân viên tự chấm trên web: tin giờ máy chủ, không gắn thiết bị. */
        public static Policy web() {
            return new Policy(100, null, ZoneId.of("Asia/Ho_Chi_Minh"), true);
        }
    }

    private final Policy policy;
    private final SiteDirectory sites;
    private final DeviceRegistry devices;

    public PunchValidator(Policy policy, SiteDirectory sites, DeviceRegistry devices) {
        this.policy = policy;
        this.sites = sites;
        this.devices = devices;
    }

    public PunchVerdict validate(PunchRequest p, Instant serverNow, AssignmentHistory history) {
        List<String> review = new ArrayList<>();

        // 1. Thiết bị
        if (!policy.webMode()) {
            Optional<String> bound = devices.boundDevice(p.employeeCode());
            if (bound.isPresent() && !bound.get().equals(p.deviceId())) {
                return reject(serverNow, null, "Thiết bị này chưa được đăng ký cho bạn. Liên hệ HR nếu bạn đã đổi điện thoại");
            }
            if (bound.isEmpty()) {
                review.add("thiết bị mới, chờ HR xác nhận gắn với tài khoản");
            }
        }
        // 2. Vị trí giả lập
        if (p.mockLocation()) {
            return reject(serverNow, null, "Điện thoại đang dùng vị trí giả lập. Tắt ứng dụng giả lập vị trí rồi thử lại");
        }
        // 3. Giờ máy
        if (p.clientTime() != null && policy.maxClockSkew() != null) {
            Duration skew = Duration.between(p.clientTime(), serverNow).abs();
            if (skew.compareTo(policy.maxClockSkew()) > 0) {
                return reject(serverNow, null, "Giờ trên điện thoại lệch " + skew.toMinutes()
                        + " phút so với giờ hệ thống. Bật chế độ giờ tự động rồi thử lại");
            }
        }
        // 4. Hôm nay làm ở đâu
        LocalDate today = serverNow.atZone(policy.zone()).toLocalDate();
        Optional<Assignment> where = history.locationOn(today);
        if (where.isEmpty()) {
            review.add("chưa có dữ liệu nơi làm việc của bạn trong ngày này");
            return verdict(Status.NEEDS_REVIEW, Method.NONE, serverNow, null, null, null, review);
        }
        Assignment a = where.get();
        // 5. Làm từ xa
        if (a.type() == WorkLocationType.TU_XA) {
            return verdict(review.isEmpty() ? Status.ACCEPTED : Status.NEEDS_REVIEW, Method.REMOTE, serverNow, a, null, null, review);
        }
        List<WorkSite> allowed = sites.sitesFor(a);
        if (policy.webMode()) {
            return validateWeb(p, serverNow, a, allowed);
        }
        if (allowed.isEmpty()) {
            review.add("chưa khai báo điểm chấm công cho \"" + a.placeLabel() + "\"");
            return verdict(Status.NEEDS_REVIEW, Method.NONE, serverNow, a, null, null, review);
        }
        // 6. WiFi / IP
        for (WorkSite s : allowed) {
            if (s.hasWifi(p.wifiBssid())) {
                return verdict(review.isEmpty() ? Status.ACCEPTED : Status.NEEDS_REVIEW, Method.WIFI, serverNow, a, s, null, review);
            }
        }
        for (WorkSite s : allowed) {
            if (s.hasIp(p.clientIp())) {
                return verdict(review.isEmpty() ? Status.ACCEPTED : Status.NEEDS_REVIEW, Method.IP, serverNow, a, s, null, review);
            }
        }
        // 7. GPS
        if (p.location() == null) {
            return reject(serverNow, a, "Không lấy được vị trí. Bật định vị hoặc kết nối WiFi của " + allowed.get(0).name());
        }
        WorkSite nearest = allowed.stream()
                .min(Comparator.comparingDouble(s -> s.center().distanceMeters(p.location()) - s.radiusMeters()))
                .orElseThrow();
        double d = nearest.center().distanceMeters(p.location());
        double acc = p.accuracyMeters() == null ? Double.MAX_VALUE : p.accuracyMeters();
        if (acc > policy.maxAccuracyMeters()) {
            review.add("sai số GPS " + Math.round(acc) + " m quá lớn (tối đa " + Math.round(policy.maxAccuracyMeters()) + " m)");
            return verdict(Status.NEEDS_REVIEW, Method.GPS, serverNow, a, nearest, d, review);
        }
        if (d <= nearest.radiusMeters()) {
            return verdict(review.isEmpty() ? Status.ACCEPTED : Status.NEEDS_REVIEW, Method.GPS, serverNow, a, nearest, d, review);
        }
        if (d <= nearest.radiusMeters() + acc) {
            review.add("vị trí sát ranh giới " + nearest.name() + " (cách " + Math.round(d) + " m, cho phép "
                    + nearest.radiusMeters() + " m, sai số " + Math.round(acc) + " m)");
            return verdict(Status.NEEDS_REVIEW, Method.GPS, serverNow, a, nearest, d, review);
        }
        return verdict(Status.REJECTED, Method.GPS, serverNow, a, nearest, d, List.of("Bạn đang cách " + nearest.name()
                + " khoảng " + Math.round(d) + " m (cho phép " + nearest.radiusMeters() + " m). Hôm nay nơi làm việc của bạn là: "
                + a.placeLabel()));
    }

    /** Phần 6–7 của bảng xác minh cho web: IP văn phòng, rồi GPS; thiếu vị trí thì chờ duyệt. */
    private PunchVerdict validateWeb(PunchRequest p, Instant serverNow, Assignment a, List<WorkSite> allowed) {
        boolean declared = allowed.stream().anyMatch(s -> s.center() != null || !s.publicIps().isEmpty());
        if (!declared) {
            return verdict(Status.NEEDS_REVIEW, Method.NONE, serverNow, a, null, null,
                    List.of("Địa điểm chưa khai báo vị trí hoặc IP cho \"" + a.placeLabel() + "\""));
        }
        for (WorkSite s : allowed) {
            if (s.hasIp(p.clientIp())) {
                return verdict(Status.ACCEPTED, Method.IP, serverNow, a, s, null, List.of());
            }
        }
        if (p.location() == null) {
            return verdict(Status.NEEDS_REVIEW, Method.NONE, serverNow, a, null, null,
                    List.of("Không có vị trí và không ở mạng văn phòng"));
        }
        List<WorkSite> located = allowed.stream().filter(s -> s.center() != null).toList();
        if (located.isEmpty()) {
            return verdict(Status.NEEDS_REVIEW, Method.NONE, serverNow, a, null, null,
                    List.of("Địa điểm chưa khai báo tọa độ, không đối chiếu được vị trí"));
        }
        WorkSite nearest = located.stream()
                .min(Comparator.comparingDouble(s -> s.center().distanceMeters(p.location()) - s.radiusMeters()))
                .orElseThrow();
        double d = nearest.center().distanceMeters(p.location());
        if (p.accuracyMeters() == null) {
            // Thiếu sai số không được lách việc từ chối: trong bán kính thì chờ duyệt, ngoài bán kính thì từ chối
            if (d <= nearest.radiusMeters()) {
                return verdict(Status.NEEDS_REVIEW, Method.GPS, serverNow, a, nearest, d,
                        List.of("không rõ sai số GPS"));
            }
            return verdict(Status.REJECTED, Method.GPS, serverNow, a, nearest, d, List.of("Bạn đang cách " + nearest.name()
                    + " " + formatDistance(d) + ". Hôm nay bạn phải chấm tại " + nearest.name()));
        }
        double acc = p.accuracyMeters();
        if (acc > policy.maxAccuracyMeters()) {
            return verdict(Status.NEEDS_REVIEW, Method.GPS, serverNow, a, nearest, d, List.of(
                    "sai số GPS " + Math.round(acc) + " m quá lớn (tối đa " + Math.round(policy.maxAccuracyMeters()) + " m)"));
        }
        if (d <= nearest.radiusMeters()) {
            return verdict(Status.ACCEPTED, Method.GPS, serverNow, a, nearest, d, List.of());
        }
        if (d <= nearest.radiusMeters() + acc) {
            return verdict(Status.NEEDS_REVIEW, Method.GPS, serverNow, a, nearest, d, List.of(
                    "vị trí sát ranh giới " + nearest.name() + " (cách " + formatDistance(d) + ", cho phép "
                            + nearest.radiusMeters() + " m, sai số " + Math.round(acc) + " m)"));
        }
        return verdict(Status.REJECTED, Method.GPS, serverNow, a, nearest, d, List.of("Bạn đang cách " + nearest.name()
                + " " + formatDistance(d) + ". Hôm nay bạn phải chấm tại " + nearest.name()));
    }

    /** "650 m" dưới 1000 m; "2,4 km" từ 1000 m. */
    static String formatDistance(double meters) {
        long m = Math.round(meters);
        if (m < 1000) {
            return m + " m";
        }
        return String.format(Locale.ROOT, "%.1f", m / 1000.0).replace('.', ',') + " km";
    }

    private static PunchVerdict reject(Instant now, Assignment a, String reason) {
        return new PunchVerdict(Status.REJECTED, Method.NONE, now, a, null, null, List.of(reason));
    }

    private static PunchVerdict verdict(Status s, Method m, Instant now, Assignment a, WorkSite site, Double d, List<String> reasons) {
        return new PunchVerdict(s, m, now, a, site, d, reasons);
    }
}

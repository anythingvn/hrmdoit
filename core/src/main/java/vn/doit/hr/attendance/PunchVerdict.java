package vn.doit.hr.attendance;

import java.time.Instant;
import java.util.List;
import vn.doit.hr.assignment.Assignment;

/**
 * Kết quả kiểm tra một lần chấm công.
 *
 * <ul>
 *   <li>ACCEPTED – tính công ngay.</li>
 *   <li>NEEDS_REVIEW – lưu lại, chờ quản lý duyệt (GPS kém, sát ranh giới, thiết bị mới, chưa khai báo điểm chấm công...).</li>
 *   <li>REJECTED – không tính; nhân viên có thể gửi giải trình kèm lý do.</li>
 * </ul>
 */
public record PunchVerdict(
        Status status,
        Method method,
        Instant serverTime,
        Assignment assignment,
        WorkSite site,
        Double distanceMeters,
        List<String> reasons) {

    public enum Status { ACCEPTED, NEEDS_REVIEW, REJECTED }

    public enum Method { WIFI, IP, GPS, REMOTE, NONE }

    public PunchVerdict {
        reasons = List.copyOf(reasons);
    }

    /** Câu hiển thị cho nhân viên trên app. */
    public String message() {
        return switch (status) {
            case ACCEPTED -> "Đã chấm công" + (site == null ? "" : " tại " + site.name());
            case NEEDS_REVIEW -> "Đã ghi nhận, chờ duyệt: " + String.join("; ", reasons);
            case REJECTED -> "Không chấm công được: " + String.join("; ", reasons);
        };
    }
}

package vn.doit.hrm.service.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import vn.doit.hrm.domain.AttendancePunch;

/**
 * Một lần chấm trên màn gõ, bảng công tháng và "Bảng công của tôi": loại ({@code VAO}/{@code RA}), giờ, nguồn ({@code GO_TAY}, {@code APP},
 * {@code GIAI_TRINH}), trạng thái, phương thức xác minh, điểm chấm đã đối chiếu, khoảng cách, lý do chờ / từ chối, ghi chú và người quyết định (người duyệt từ chối hoặc đồng ý; không phải dữ liệu vị trí).
 *
 * <p>Tọa độ, sai số và IP chỉ có giá trị khi người xem là chính người đó, người duyệt ngày đó, HR hoặc Quản trị; ngược lại là null (spec
 * 3.8). {@link #toString()} không bao giờ chứa các trường này, để không lọt vào log.
 */
public record PunchView(
    Long id,
    String kind,
    @JsonFormat(pattern = "HH:mm") LocalTime time,
    String source,
    String status,
    String method,
    String siteName,
    Integer distanceM,
    String reviewReason,
    String decisionNote,
    String decidedBy,
    BigDecimal latitude,
    BigDecimal longitude,
    BigDecimal accuracyM,
    String clientIp
) {
    /** Lần chấm {@code p}; {@code withLocation} quyết định có trả tọa độ, sai số, IP hay không. */
    static PunchView of(AttendancePunch p, Map<Long, String> siteNames, boolean withLocation) {
        return new PunchView(
            p.getId(),
            p.getKind() == null ? null : p.getKind().name(),
            p.getPunchTime(),
            p.getSource() == null ? null : p.getSource().name(),
            p.getStatus() == null ? null : p.getStatus().name(),
            p.getMethod() == null ? null : p.getMethod().name(),
            p.getSiteId() == null ? null : siteNames.get(p.getSiteId()),
            p.getDistanceM(),
            p.getReviewReason(),
            p.getDecisionNote(),
            p.getDecidedBy(),
            withLocation ? p.getLatitude() : null,
            withLocation ? p.getLongitude() : null,
            withLocation ? p.getAccuracyM() : null,
            withLocation ? p.getClientIp() : null
        );
    }

    @Override
    public String toString() {
        return (
            "PunchView{id=" + id + ", kind=" + kind + ", time=" + time + ", source=" + source + ", status=" + status + ", method=" + method + "}"
        );
    }
}

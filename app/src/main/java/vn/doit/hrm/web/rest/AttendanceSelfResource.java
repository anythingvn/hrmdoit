package vn.doit.hrm.web.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.aop.logging.NoArgumentLogging;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.attendance.SelfPunchService;
import vn.doit.hrm.service.attendance.SelfPunchService.PunchResultDTO;
import vn.doit.hrm.service.attendance.SelfPunchService.TodayDTO;

/**
 * Nhân viên tự chấm công (dùng chung cho web và app sau này). Nhân viên lấy từ phiên đăng nhập, không từ body; giờ là giờ máy chủ. IP lấy
 * từ kết nối ({@link HttpServletRequest#getRemoteAddr()}): sau reverse proxy, {@code server.forward-headers-strategy} của Spring thay
 * bằng IP trong {@code X-Forwarded-For} của proxy tin cậy; không tự đọc header từ client.
 */
@NoArgumentLogging
@RestController
@RequestMapping("/api/attendance")
public class AttendanceSelfResource {

    /** Một lần bấm: {@code kind} là {@code VAO} hoặc {@code RA}; vị trí tùy chọn (người dùng có thể từ chối chia sẻ). */
    public record PunchVM(String kind, Double latitude, Double longitude, Double accuracy) {}

    private final SelfPunchService service;

    public AttendanceSelfResource(SelfPunchService service) {
        this.service = service;
    }

    @PostMapping("/punches")
    public PunchResultDTO punch(@RequestBody(required = false) PunchVM body, HttpServletRequest request) {
        PunchVM b = body == null ? new PunchVM(null, null, null, null) : body;
        return service.punch(currentLogin(), b.kind(), b.latitude(), b.longitude(), b.accuracy(), request.getRemoteAddr());
    }

    @GetMapping("/punches/today")
    public TodayDTO today() {
        return service.today(currentLogin());
    }

    @PostMapping("/consent")
    public ResponseEntity<Void> consent() {
        service.consent(currentLogin());
        return ResponseEntity.noContent().build();
    }

    private static String currentLogin() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new AccessDeniedException("Chưa đăng nhập"));
    }
}

package vn.doit.hrm.web.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.aop.logging.NoArgumentLogging;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.attendance.ExplanationService;
import vn.doit.hrm.service.attendance.ExplanationService.ExplanationDTO;
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

    /** Giải trình: ngày, giờ vào và/hoặc giờ ra đề xuất ({@code HH:mm}), lý do (bắt buộc). */
    public record ExplanationVM(LocalDate workDay, LocalTime proposedIn, LocalTime proposedOut, String reason) {}

    private final SelfPunchService service;
    private final ExplanationService explanationService;

    public AttendanceSelfResource(SelfPunchService service, ExplanationService explanationService) {
        this.service = service;
        this.explanationService = explanationService;
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

    /** Gửi giải trình cho một ngày đã qua (hoặc hôm nay với giờ đã qua) của chính mình. */
    @PostMapping("/explanations")
    public ExplanationDTO submitExplanation(@RequestBody(required = false) ExplanationVM body) {
        ExplanationVM b = body == null ? new ExplanationVM(null, null, null, null) : body;
        return explanationService.submit(currentLogin(), b.workDay(), b.proposedIn(), b.proposedOut(), b.reason());
    }

    /** Rút giải trình đang chờ của chính mình. */
    @PostMapping("/explanations/{id}/withdraw")
    public ExplanationDTO withdrawExplanation(@PathVariable Long id) {
        return explanationService.withdraw(currentLogin(), id);
    }

    /** Giải trình của chính mình trong tháng. */
    @GetMapping("/explanations/mine")
    public List<ExplanationDTO> myExplanations(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return explanationService.mine(currentLogin(), month);
    }

    private static String currentLogin() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new AccessDeniedException("Chưa đăng nhập"));
    }
}

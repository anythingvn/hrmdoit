package vn.doit.hrm.web.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import vn.doit.hr.attendance.DayCredit;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.attendance.AttendanceAccess;
import vn.doit.hrm.service.attendance.AttendanceDayService;
import vn.doit.hrm.service.attendance.AttendanceDayService.DayView;
import vn.doit.hrm.service.attendance.AttendanceDayService.Entry;
import vn.doit.hrm.service.attendance.AttendanceMonthService;
import vn.doit.hrm.service.attendance.AttendanceMonthService.BoardDTO;
import vn.doit.hrm.service.attendance.AttendanceMonthService.MonthStatusDTO;
import vn.doit.hrm.service.attendance.AttendanceScheduleService;
import vn.doit.hrm.service.attendance.ExplanationService;
import vn.doit.hrm.service.attendance.ExplanationService.ExplanationDTO;
import vn.doit.hrm.service.attendance.ExplanationService.PendingItemDTO;
import vn.doit.hrm.service.attendance.PunchReviewService;

/**
 * Chấm công. Màn gõ theo ngày (kiểu A): quyền thật theo {@link AttendanceAccess} (Quản trị, HR, trưởng nhóm, trưởng nhóm onsite; người
 * không gõ được cho ai → 403). Bảng công tháng (kiểu B): xem theo {@link AttendanceMonthService} (Quản trị, HR, BGĐ toàn công ty; trưởng
 * phòng phòng mình; trưởng nhóm nhóm mình; trưởng nhóm onsite người onsite ở khách hàng của mình, chỉ các ngày đó; Kế toán tháng đã
 * chốt); sửa ô qua {@code PUT /day}; "Bảng công của tôi" mọi tài khoản; chốt và mở lại chỉ Quản trị, HR.
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceResource {

    public record HoursDTO(
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime end,
        @JsonFormat(pattern = "HH:mm") LocalTime breakStart,
        @JsonFormat(pattern = "HH:mm") LocalTime breakEnd
    ) {}

    /** Một khối trên màn gõ: "Văn phòng" (giờ công ty) hoặc "Onsite · khách hàng" (giờ khách). */
    public record DayGroup(String group, String label, HoursDTO hours, List<DayView> rows) {}

    public record DayBoard(LocalDate date, int graceMinutes, int halfDayThresholdMinutes, List<DayGroup> groups) {}

    public record DayEntryVM(LocalDate date, List<Entry> entries) {}

    public record ReopenVM(String reason) {}

    public record DecisionVM(String note) {}

    private static final String CLOSERS = "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')";
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AttendanceDayService dayService;
    private final AttendanceAccess access;
    private final AttendanceScheduleService scheduleService;
    private final AttendanceMonthService monthService;
    private final PunchReviewService reviewService;
    private final ExplanationService explanationService;

    public AttendanceResource(
        AttendanceDayService dayService,
        AttendanceAccess access,
        AttendanceScheduleService scheduleService,
        AttendanceMonthService monthService,
        PunchReviewService reviewService,
        ExplanationService explanationService
    ) {
        this.reviewService = reviewService;
        this.explanationService = explanationService;
        this.dayService = dayService;
        this.access = access;
        this.scheduleService = scheduleService;
        this.monthService = monthService;
    }

    /** Những người người gọi được gõ ngày {@code date}, chia khối. */
    @GetMapping("/day")
    public DayBoard day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String login = currentLogin();
        if (!access.entersAnyone(login)) {
            throw new AccessDeniedException("Không có quyền gõ giờ");
        }
        // Chỉ những người người gọi được gõ (người duyệt ngày đó): được thấy tọa độ / IP của lần chấm.
        List<DayView> views = dayService.days(access.enterableEmployees(login, date), date, AttendanceDayService.LocationAccess.ALL);
        Map<String, List<DayView>> byGroup = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (DayView v : views) {
            byGroup.computeIfAbsent(v.group(), k -> new ArrayList<>()).add(v);
            labels.putIfAbsent(v.group(), v.groupLabel());
        }
        List<DayGroup> groups = new ArrayList<>();
        byGroup
            .keySet()
            .stream()
            .sorted(
                Comparator.comparing((String g) -> !AttendanceDayService.OFFICE.equals(g)).thenComparing(labels::get, Comparator.nullsLast(Comparator.naturalOrder()))
            )
            .forEach(g -> {
                Long siteId = AttendanceDayService.OFFICE.equals(g) ? null : Long.valueOf(g.substring("SITE:".length()));
                DayCredit.Hours h = scheduleService.hoursFor(date, siteId);
                List<DayView> rows = new ArrayList<>(byGroup.get(g));
                rows.sort(AttendanceDayService.BY_CODE);
                groups.add(new DayGroup(g, labels.get(g), new HoursDTO(h.start(), h.end(), h.breakStart(), h.breakEnd()), rows));
            });
        DayCredit.Rules rules = scheduleService.rules(date);
        return new DayBoard(date, rules.graceMinutes(), rules.halfDayThresholdMinutes(), groups);
    }

    /** Thay giờ gõ tay của các dòng trong ngày; trả kết quả ngày sau khi lưu. */
    @PutMapping("/day")
    public List<DayView> saveDay(@RequestBody DayEntryVM body) {
        String login = currentLogin();
        if (!access.entersAnyone(login)) {
            throw new AccessDeniedException("Không có quyền gõ giờ");
        }
        return dayService.save(login, body == null ? null : body.date(), body == null ? List.of() : body.entries());
    }

    /** Duyệt lần tự chấm đang chờ duyệt (người duyệt của ngày đó, không tự duyệt); trả kết quả ngày đó sau khi duyệt. */
    @PostMapping("/punches/{id}/approve")
    public DayView approvePunch(@PathVariable Long id) {
        return reviewService.approve(currentLogin(), id);
    }

    /** Từ chối lần tự chấm đang chờ duyệt, bắt buộc lý do {@code note}; trả kết quả ngày đó. */
    @PostMapping("/punches/{id}/reject")
    public DayView rejectPunch(@PathVariable Long id, @RequestBody(required = false) DecisionVM body) {
        return reviewService.reject(currentLogin(), id, body == null ? null : body.note());
    }

    /** Đồng ý giải trình (người duyệt của ngày đó, không tự duyệt): sinh lần chấm {@code GIAI_TRINH} cho mỗi giờ đề xuất. */
    @PostMapping("/explanations/{id}/approve")
    public ExplanationDTO approveExplanation(@PathVariable Long id) {
        return explanationService.approve(currentLogin(), id);
    }

    /** Từ chối giải trình, bắt buộc lý do {@code note}. */
    @PostMapping("/explanations/{id}/reject")
    public ExplanationDTO rejectExplanation(@PathVariable Long id, @RequestBody(required = false) DecisionVM body) {
        return explanationService.reject(currentLogin(), id, body == null ? null : body.note());
    }

    /** Lần tự chấm chờ duyệt và giải trình đang chờ trong phạm vi người gọi duyệt được (không phải người duyệt: danh sách rỗng). */
    @GetMapping("/pending")
    public List<PendingItemDTO> pending() {
        return explanationService.pending(currentLogin());
    }

    /** Bảng công tháng trong phạm vi người gọi được xem; lọc phòng, khách hàng (onsite ít nhất một ngày), nhóm (trưởng nhóm). */
    @GetMapping("/month")
    public BoardDTO month(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) Long siteId,
        @RequestParam(required = false) Long managerId
    ) {
        return monthService.board(currentLogin(), month, departmentId, siteId, managerId);
    }

    /** Bảng công tháng của chính người gọi (không gắn hồ sơ, hoặc không phải nhân viên: không có dòng). */
    @GetMapping("/my")
    public BoardDTO my(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return monthService.my(currentLogin(), month);
    }

    /** Chốt bảng công tháng; còn ô đỏ thì phải gửi {@code force=true} (không thì 400 {@code attendanceunresolved} kèm danh sách). */
    @PostMapping("/month/{month}/close")
    @PreAuthorize(CLOSERS)
    public MonthStatusDTO close(
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
        @RequestParam(defaultValue = "false") boolean force
    ) {
        monthService.close(month, force);
        return monthService.status(month);
    }

    /** Mở lại bảng công đã chốt (cần lý do; kỳ lương tháng đó đã duyệt thì không mở được). */
    @PostMapping("/month/{month}/reopen")
    @PreAuthorize(CLOSERS)
    public MonthStatusDTO reopen(@PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, @RequestBody(required = false) ReopenVM body) {
        monthService.reopen(month, body == null ? null : body.reason());
        return monthService.status(month);
    }

    /** File Excel bảng công tháng, cùng phạm vi xem với {@code GET /month}. */
    @GetMapping("/month/{month}/export.xlsx")
    public ResponseEntity<byte[]> export(@PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        byte[] body = monthService.export(currentLogin(), month);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment("bang-cong-" + month + ".xlsx"))
            .contentType(XLSX)
            .body(body);
    }

    private static String currentLogin() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new AccessDeniedException("Chưa đăng nhập"));
    }
}

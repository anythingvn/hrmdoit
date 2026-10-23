package vn.doit.hrm.web.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import vn.doit.hr.attendance.DayCredit;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.attendance.AttendanceAccess;
import vn.doit.hrm.service.attendance.AttendanceDayService;
import vn.doit.hrm.service.attendance.AttendanceDayService.DayView;
import vn.doit.hrm.service.attendance.AttendanceDayService.Entry;
import vn.doit.hrm.service.attendance.AttendanceScheduleService;

/**
 * Chấm công theo ngày (màn gõ kiểu A). Mọi tài khoản đăng nhập gọi được; quyền thật theo {@link AttendanceAccess}: Quản trị, HR, trưởng
 * nhóm, trưởng nhóm onsite. Người không gõ được cho ai → 403.
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

    private final AttendanceDayService dayService;
    private final AttendanceAccess access;
    private final AttendanceScheduleService scheduleService;

    public AttendanceResource(AttendanceDayService dayService, AttendanceAccess access, AttendanceScheduleService scheduleService) {
        this.dayService = dayService;
        this.access = access;
        this.scheduleService = scheduleService;
    }

    /** Những người người gọi được gõ ngày {@code date}, chia khối. */
    @GetMapping("/day")
    public DayBoard day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String login = currentLogin();
        if (!access.entersAnyone(login)) {
            throw new AccessDeniedException("Không có quyền gõ giờ");
        }
        List<DayView> views = dayService.days(access.enterableEmployees(login, date), date);
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

    private static String currentLogin() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new AccessDeniedException("Chưa đăng nhập"));
    }
}

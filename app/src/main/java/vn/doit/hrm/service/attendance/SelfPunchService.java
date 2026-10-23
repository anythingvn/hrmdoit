package vn.doit.hrm.service.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hr.attendance.DayCredit;
import vn.doit.hr.attendance.GeoPoint;
import vn.doit.hr.attendance.PunchRequest;
import vn.doit.hr.attendance.PunchValidator;
import vn.doit.hr.attendance.PunchVerdict;
import vn.doit.hrm.aop.logging.NoArgumentLogging;
import vn.doit.hrm.domain.AttendanceConsent;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchMethod;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.AttendancePunchStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AttendanceConsentRepository;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.ArchivedEmployeeGuard;
import vn.doit.hrm.service.CompanySettingService;
import vn.doit.hrm.service.EmployeeRuleException;

/**
 * Nhân viên tự chấm công (web, sau này app): giờ máy chủ (Asia/Ho_Chi_Minh), IP từ kết nối (sau proxy tin cậy: theo cấu hình forward
 * headers của Spring), vị trí lúc bấm; xác minh bằng {@link PunchValidator} chế độ web. Lần bị từ chối vẫn được lưu. Tọa độ và IP được
 * lưu nhưng không ghi log.
 */
@NoArgumentLogging
@Service
@Transactional
public class SelfPunchService {

    /** Kết quả một lần chấm (hoặc lần chấm đã có khi bấm trùng trong 1 phút). */
    public record PunchResultDTO(
        Long id,
        String kind,
        @JsonFormat(pattern = "HH:mm") LocalTime time,
        String status,
        String method,
        String siteName,
        Integer distanceM,
        String message
    ) {}

    /** Màn chấm công hôm nay: nơi phải chấm, giờ làm, đã đồng ý thông báo chưa, các lần chấm trong ngày. */
    public record TodayDTO(
        LocalDate day,
        String siteName,
        String siteKind,
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime end,
        boolean consentRequired,
        String noticeText,
        List<PunchResultDTO> punches
    ) {}

    static final ZoneId ZONE = PunchValidator.Policy.web().zone();
    static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(1);
    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final double MAX_ACCURACY_INPUT = 100_000;

    private final EmployeeRepository employeeRepository;
    private final AttendancePunchRepository punchRepository;
    private final AttendanceConsentRepository consentRepository;
    private final WorkSiteRepository siteRepository;
    private final AttendanceMonthService monthService;
    private final AttendanceScheduleService scheduleService;
    private final ArchivedEmployeeGuard archivedGuard;
    private final EmploymentWindow window;
    private final PunchSiteResolver siteResolver;
    private final CompanySettingService companySettingService;
    private final Clock clock;

    public SelfPunchService(
        EmployeeRepository employeeRepository,
        AttendancePunchRepository punchRepository,
        AttendanceConsentRepository consentRepository,
        WorkSiteRepository siteRepository,
        AttendanceMonthService monthService,
        AttendanceScheduleService scheduleService,
        ArchivedEmployeeGuard archivedGuard,
        EmploymentWindow window,
        PunchSiteResolver siteResolver,
        CompanySettingService companySettingService,
        Clock clock
    ) {
        this.employeeRepository = employeeRepository;
        this.punchRepository = punchRepository;
        this.consentRepository = consentRepository;
        this.siteRepository = siteRepository;
        this.monthService = monthService;
        this.scheduleService = scheduleService;
        this.archivedGuard = archivedGuard;
        this.window = window;
        this.siteResolver = siteResolver;
        this.companySettingService = companySettingService;
        this.clock = clock;
    }

    /**
     * Lần chấm được tính công: gõ tay (luôn), tự chấm ở trạng thái {@code CHAP_NHAN} hoặc {@code DA_DUYET}, lần sinh từ giải trình đã
     * duyệt. {@code CHO_DUYET} và {@code TU_CHOI} không tính.
     */
    public static boolean counts(AttendancePunch p) {
        if (p == null || p.getSource() == null) {
            return false;
        }
        return switch (p.getSource()) {
            case GO_TAY, GIAI_TRINH -> true;
            case APP -> p.getStatus() == null ||
                p.getStatus() == AttendancePunchStatus.CHAP_NHAN ||
                p.getStatus() == AttendancePunchStatus.DA_DUYET;
        };
    }

    /**
     * Một lần tự chấm của {@code login}. Thứ tự: hồ sơ (404), lưu trữ, áp dụng, dữ liệu gửi lên, đồng ý thông báo, tháng đã chốt, khóa hồ
     * sơ, chặn bấm trùng trong 1 phút (trả lại lần đã có), nơi phải chấm ngày đó, {@link PunchValidator} chế độ web, lưu.
     *
     * @param ip IP của kết nối ({@code HttpServletRequest#getRemoteAddr}); không đọc header từ client
     */
    public PunchResultDTO punch(String login, String kind, Double latitude, Double longitude, Double accuracy, String ip) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        Employee me = profile(login);
        archivedGuard.requireActive(me);
        requireApplicable(me, today);
        AttendancePunchKind k = parseKind(kind);
        GeoPoint location = location(latitude, longitude, accuracy);
        if (!consentRepository.existsByEmployeeId(me.getId())) {
            throw consentRequired();
        }
        if (monthService.isClosed(YearMonth.from(today))) {
            throw new EmployeeRuleException(
                "Bảng công tháng " + YearMonth.from(today).format(MM_YYYY) + " đã chốt, không chấm công được",
                "attendancemonthclosed"
            );
        }
        // Khóa hồ sơ: hai lần bấm cùng lúc chạy lần lượt; lần sau thấy lần trước (đã commit) và trả lại kết quả của nó.
        employeeRepository.findByIdForUpdate(me.getId()).orElseThrow(SelfPunchService::noProfile);
        // Lấy giờ sau khi giữ khóa, để lần chờ khóa không có giờ sớm hơn lần vừa ghi.
        Instant now = clock.instant();
        ZonedDateTime at = now.atZone(ZONE);
        LocalDate day = at.toLocalDate();
        Optional<AttendancePunch> recent = punchRepository
            .findRecent(me.getId(), k, AttendancePunchSource.APP, now.minus(DUPLICATE_WINDOW), now)
            .stream()
            .findFirst();
        if (recent.isPresent()) {
            AttendancePunch p = recent.orElseThrow();
            return toResult(p, siteNames(List.of(p)), siteResolver.resolve(me, p.getWorkDay()).siteName());
        }

        PunchSiteResolver.Resolved where = siteResolver.resolve(me, day);
        PunchRequest request = new PunchRequest(
            me.getCode(),
            null,
            k == AttendancePunchKind.VAO ? PunchRequest.Type.IN : PunchRequest.Type.OUT,
            null,
            location,
            location == null ? null : accuracy,
            false,
            null,
            ip
        );
        PunchVerdict v = new PunchValidator(PunchValidator.Policy.web(), where.directory(), code -> Optional.empty()).validate(
            request,
            now,
            where.history()
        );

        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(me.getId());
        p.setWorkDay(day);
        p.setPunchTime(at.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
        p.setKind(k);
        p.setSource(AttendancePunchSource.APP);
        p.setEnteredBy(cut(login, 50));
        p.setEnteredAt(now);
        p.setStatus(status(v.status()));
        p.setMethod(method(v.method()));
        p.setSiteId(v.site() == null ? null : Long.valueOf(v.site().id()));
        p.setDistanceM(v.distanceMeters() == null ? null : (int) Math.round(v.distanceMeters()));
        if (location != null) {
            p.setLatitude(BigDecimal.valueOf(latitude).setScale(6, RoundingMode.HALF_UP));
            p.setLongitude(BigDecimal.valueOf(longitude).setScale(6, RoundingMode.HALF_UP));
            p.setAccuracyM(accuracy == null ? null : BigDecimal.valueOf(accuracy).setScale(1, RoundingMode.HALF_UP));
        }
        p.setClientIp(cut(ip, 45));
        p.setReviewReason(v.status() == PunchVerdict.Status.ACCEPTED ? null : cut(String.join("; ", v.reasons()), 500));
        p = punchRepository.saveAndFlush(p);
        return toResult(p, v.site() == null ? Map.of() : Map.of(p.getSiteId(), v.site().name()), where.siteName());
    }

    /** Đồng ý thông báo thu thập vị trí (đồng ý rồi thì giữ nguyên lần đầu). */
    public void consent(String login) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        Employee me = profile(login);
        archivedGuard.requireActive(me);
        requireApplicable(me, today);
        employeeRepository.findByIdForUpdate(me.getId()).orElseThrow(SelfPunchService::noProfile);
        if (consentRepository.existsByEmployeeId(me.getId())) {
            return;
        }
        AttendanceConsent c = new AttendanceConsent();
        c.setEmployeeId(me.getId());
        c.setAcceptedAt(clock.instant());
        c.setNoticeVersion(companySettingService.attendanceNoticeVersion());
        consentRepository.saveAndFlush(c);
    }

    /** Màn chấm công hôm nay của {@code login}. */
    @Transactional(readOnly = true)
    public TodayDTO today(String login) {
        LocalDate day = LocalDate.ofInstant(clock.instant(), ZONE);
        Employee me = profile(login);
        requireApplicable(me, day);
        PunchSiteResolver.Resolved where = siteResolver.resolve(me, day);
        DayCredit.Hours hours = scheduleService.hoursFor(day, where.hoursSiteId());
        List<AttendancePunch> rows = punchRepository
            .findByEmployeeIdAndWorkDayOrderByPunchTimeAsc(me.getId(), day)
            .stream()
            .sorted(Comparator.comparing(AttendancePunch::getPunchTime).thenComparing(AttendancePunch::getId))
            .toList();
        Map<Long, String> names = siteNames(rows);
        boolean consentRequired = !consentRepository.existsByEmployeeId(me.getId());
        return new TodayDTO(
            day,
            where.siteName(),
            where.siteKind(),
            hours.start(),
            hours.end(),
            consentRequired,
            companySettingService.attendanceNoticeText(),
            rows
                .stream()
                .map(p -> toResult(p, names, where.siteName()))
                .toList()
        );
    }

    // ------------------------------------------------------------------ hỗ trợ

    private Employee profile(String login) {
        if (login == null) {
            throw noProfile();
        }
        return employeeRepository.findOneByUserLogin(login).orElseThrow(SelfPunchService::noProfile);
    }

    /** Chỉ nhân viên ({@code NHAN_VIEN}) trong lần làm việc phủ ngày {@code day} (từ ngày vào làm đến ngày làm việc cuối). */
    private void requireApplicable(Employee e, LocalDate day) {
        if (e.getPersonType() != PersonType.NHAN_VIEN || !window.covers(e, day)) {
            throw new EmployeeRuleException("Chỉ nhân viên đang làm việc mới tự chấm công", "attendancenotapplicable");
        }
    }

    private static ResponseStatusException noProfile() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tài khoản chưa gắn hồ sơ nhân viên");
    }

    private static EmployeeRuleException consentRequired() {
        return new EmployeeRuleException("Cần đồng ý thông báo thu thập vị trí trước khi chấm công", "attendanceconsentrequired");
    }

    private static EmployeeRuleException invalid() {
        return new EmployeeRuleException(
            "Lần chấm không hợp lệ: chọn Vào hoặc Ra; vị trí cần đủ vĩ độ, kinh độ hợp lệ và sai số không âm",
            "attendancepunchinvalid"
        );
    }

    private static AttendancePunchKind parseKind(String kind) {
        if ("VAO".equals(kind)) return AttendancePunchKind.VAO;
        if ("RA".equals(kind)) return AttendancePunchKind.RA;
        throw invalid();
    }

    /** Vị trí gửi lên: cả hai tọa độ hoặc không có; sai số (nếu có) hữu hạn, không âm; null = không có vị trí. */
    private static GeoPoint location(Double lat, Double lng, Double acc) {
        if (acc != null && (!Double.isFinite(acc) || acc < 0 || acc > MAX_ACCURACY_INPUT)) {
            throw invalid();
        }
        if (lat == null && lng == null) {
            return null;
        }
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            throw invalid();
        }
        return new GeoPoint(lat, lng);
    }

    static AttendancePunchStatus status(PunchVerdict.Status s) {
        return switch (s) {
            case ACCEPTED -> AttendancePunchStatus.CHAP_NHAN;
            case NEEDS_REVIEW -> AttendancePunchStatus.CHO_DUYET;
            case REJECTED -> AttendancePunchStatus.TU_CHOI;
        };
    }

    static AttendancePunchMethod method(PunchVerdict.Method m) {
        return switch (m) {
            case REMOTE -> AttendancePunchMethod.TU_XA;
            case IP -> AttendancePunchMethod.IP;
            case GPS -> AttendancePunchMethod.GPS;
            case WIFI, NONE -> null;
        };
    }

    private Map<Long, String> siteNames(List<AttendancePunch> rows) {
        List<Long> ids = rows.stream().map(AttendancePunch::getSiteId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> out = new HashMap<>();
        if (!ids.isEmpty()) {
            for (WorkSite s : siteRepository.findAllById(ids)) {
                out.put(s.getId(), s.getName());
            }
        }
        return out;
    }

    /** Kết quả hiển thị của một lần chấm đã lưu; tên điểm chấm theo điểm đã đối chiếu, không có thì nơi phải chấm ngày đó. */
    private static PunchResultDTO toResult(AttendancePunch p, Map<Long, String> siteNames, String fallbackSite) {
        String matched = p.getSiteId() == null ? null : siteNames.get(p.getSiteId());
        return new PunchResultDTO(
            p.getId(),
            p.getKind() == null ? null : p.getKind().name(),
            p.getPunchTime(),
            p.getStatus() == null ? null : p.getStatus().name(),
            p.getMethod() == null ? null : p.getMethod().name(),
            matched != null ? matched : fallbackSite,
            p.getDistanceM(),
            message(p, matched)
        );
    }

    /** Câu cho nhân viên, cùng cách viết với {@link PunchVerdict#message()}. */
    static String message(AttendancePunch p, String matchedSite) {
        if (p.getSource() == AttendancePunchSource.GO_TAY) {
            return "Giờ do người quản lý nhập";
        }
        if (p.getSource() == AttendancePunchSource.GIAI_TRINH) {
            return "Giờ theo giải trình đã duyệt";
        }
        AttendancePunchStatus st = p.getStatus() == null ? AttendancePunchStatus.CHAP_NHAN : p.getStatus();
        return switch (st) {
            case CHAP_NHAN -> "Đã chấm công" + (matchedSite == null ? "" : " tại " + matchedSite);
            case DA_DUYET -> "Đã duyệt";
            case CHO_DUYET -> "Đã ghi nhận, chờ duyệt: " + nullToEmpty(p.getReviewReason());
            case TU_CHOI -> "Không chấm công được: " + nullToEmpty(p.getDecisionNote() != null ? p.getDecisionNote() : p.getReviewReason());
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}

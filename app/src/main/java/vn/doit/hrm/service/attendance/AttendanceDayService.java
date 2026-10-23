package vn.doit.hrm.service.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.attendance.DayCredit;
import vn.doit.hr.leave.DayPart;
import vn.doit.hrm.domain.AttendanceMark;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.Holiday;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.AttendanceMarkKind;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AttendanceMarkRepository;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.HolidayRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.service.ArchivedEmployeeGuard;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Kết quả chấm công từng ngày: ghép ô tự điền (ngoài hợp đồng, ngày nghỉ hằng tuần, lễ, đơn nghỉ đã duyệt, công tác), bộ giờ áp dụng
 * (công ty hoặc khách hàng) và các lần chấm, rồi tính công bằng {@link DayCredit}. Thứ tự ưu tiên ô tự điền: ngoài hợp đồng → nghỉ hằng
 * tuần → lễ → nghỉ cả ngày → công tác; nghỉ nửa ngày thì nửa còn lại vẫn gõ giờ.
 *
 * <p>Công thực tế = công đi làm + công tác + nghỉ có lương ({@code PHEP_NAM}, {@code NGHI_BU}, {@code VIEC_RIENG}) + lễ.
 */
@Service
@Transactional
public class AttendanceDayService {

    /**
     * Một người, một ngày.
     *
     * @param group "VP" hoặc "SITE:&lt;id khách hàng&gt;"
     * @param autoKind loại ô tự điền ({@code PHEP_NAM|NGHI_BU|VIEC_RIENG|OM|KHONG_LUONG|CONG_TAC|LE|NGHI|NGOAI_HD}), với nghỉ nửa ngày là
     *     loại nghỉ của nửa đó; null khi không có
     * @param credit công đi làm (0; 0,5; 1), null khi là ô tự điền cả ngày hoặc chưa đủ giờ
     * @param actualCredit phần đóng góp vào công thực tế
     * @param status null với ô tự điền cả ngày, và với ngày chưa qua chưa có gì để tính
     * @param leavePart {@code SANG}/{@code CHIEU} khi nghỉ nửa ngày (nửa còn lại phải gõ), null nếu không
     * @param markedAbsent người gõ đã đánh dấu Vắng ngày này
     */
    public record DayView(
        Long employeeId,
        String code,
        String name,
        LocalDate day,
        String group,
        String groupLabel,
        String autoKind,
        BigDecimal credit,
        BigDecimal actualCredit,
        DayCredit.Status status,
        @JsonFormat(pattern = "HH:mm") LocalTime in,
        @JsonFormat(pattern = "HH:mm") LocalTime out,
        int lateMinutes,
        int earlyMinutes,
        boolean remote,
        String leavePart,
        boolean markedAbsent
    ) {
        /** Ô tự điền cả ngày: không có ô nhập giờ. */
        public boolean autoFilled() {
            return autoKind != null && leavePart == null;
        }
    }

    /** Một người, một tháng; các số đếm tương ứng các cột của ảnh chụp chốt công. */
    public record MonthView(
        List<DayView> days,
        BigDecimal standardDays,
        BigDecimal actualDays,
        BigDecimal workDays,
        BigDecimal paidLeaveDays,
        BigDecimal unpaidLeaveDays,
        BigDecimal sickDays,
        BigDecimal holidayDays,
        BigDecimal tripDays,
        int lateCount,
        int lateMinutes,
        int earlyCount,
        int halfDays,
        int absentDays,
        int missingDays
    ) {}

    /** Một dòng gõ: giờ vào, giờ ra (null = bỏ trống), đánh dấu vắng. */
    public record Entry(
        Long employeeId,
        @JsonFormat(pattern = "HH:mm") LocalTime in,
        @JsonFormat(pattern = "HH:mm") LocalTime out,
        boolean absent
    ) {}

    public static final List<LeaveRequestStatus> LEAVE_STATUSES = List.of(LeaveRequestStatus.DA_DUYET, LeaveRequestStatus.CHO_HUY);

    private static final Set<LeaveRequestType> PAID = EnumSet.of(LeaveRequestType.PHEP_NAM, LeaveRequestType.NGHI_BU, LeaveRequestType.VIEC_RIENG);
    private static final BigDecimal HALF = new BigDecimal("0.5");
    public static final String OFFICE = "VP";
    public static final String OFFICE_LABEL = "Văn phòng";

    private final AttendancePunchRepository punchRepository;
    private final AttendanceMarkRepository markRepository;
    private final AttendanceMonthRepository monthRepository;
    private final LeaveRequestRepository leaveRepository;
    private final WorkAssignmentRepository assignmentRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkCalendarService calendarService;
    private final AttendanceScheduleService scheduleService;
    private final AttendanceAccess access;
    private final ArchivedEmployeeGuard archivedGuard;
    private final Clock clock;

    public AttendanceDayService(
        AttendancePunchRepository punchRepository,
        AttendanceMarkRepository markRepository,
        AttendanceMonthRepository monthRepository,
        LeaveRequestRepository leaveRepository,
        WorkAssignmentRepository assignmentRepository,
        HolidayRepository holidayRepository,
        EmployeeRepository employeeRepository,
        WorkCalendarService calendarService,
        AttendanceScheduleService scheduleService,
        AttendanceAccess access,
        ArchivedEmployeeGuard archivedGuard,
        Clock clock
    ) {
        this.punchRepository = punchRepository;
        this.markRepository = markRepository;
        this.monthRepository = monthRepository;
        this.leaveRepository = leaveRepository;
        this.assignmentRepository = assignmentRepository;
        this.holidayRepository = holidayRepository;
        this.employeeRepository = employeeRepository;
        this.calendarService = calendarService;
        this.scheduleService = scheduleService;
        this.access = access;
        this.archivedGuard = archivedGuard;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ đọc

    @Transactional(readOnly = true)
    public DayView day(Employee e, LocalDate d) {
        return new Loaded(List.of(e), d, d).compute(e, d).view();
    }

    /** Kết quả ngày {@code d} của nhiều người (theo thứ tự truyền vào). */
    @Transactional(readOnly = true)
    public List<DayView> days(List<Employee> employees, LocalDate d) {
        if (employees.isEmpty()) {
            return List.of();
        }
        Loaded l = new Loaded(employees, d, d);
        return employees.stream().map(e -> l.compute(e, d).view()).toList();
    }

    @Transactional(readOnly = true)
    public MonthView month(Employee e, YearMonth m) {
        return months(List.of(e), m).get(e.getId());
    }

    /** Bảng tháng của nhiều người (nạp dữ liệu một lần cho cả tháng). */
    @Transactional(readOnly = true)
    public Map<Long, MonthView> months(List<Employee> employees, YearMonth m) {
        Map<Long, MonthView> out = new LinkedHashMap<>();
        if (employees.isEmpty()) {
            return out;
        }
        Loaded l = new Loaded(employees, m.atDay(1), m.atEndOfMonth());
        BigDecimal standard = BigDecimal.valueOf(
            m.atDay(1).datesUntil(m.atEndOfMonth().plusDays(1)).filter(d -> l.workingDays.contains(d.getDayOfWeek())).count()
        );
        for (Employee e : employees) {
            List<DayView> views = new ArrayList<>();
            BigDecimal actual = BigDecimal.ZERO, work = BigDecimal.ZERO, paid = BigDecimal.ZERO, unpaid = BigDecimal.ZERO, sick = BigDecimal.ZERO;
            BigDecimal holiday = BigDecimal.ZERO, trip = BigDecimal.ZERO;
            int lateCount = 0, lateMinutes = 0, earlyCount = 0, halfDays = 0, absent = 0, missing = 0;
            for (LocalDate d = m.atDay(1); !d.isAfter(m.atEndOfMonth()); d = d.plusDays(1)) {
                Computed c = l.compute(e, d);
                DayView v = c.view();
                views.add(v);
                actual = actual.add(v.actualCredit());
                paid = paid.add(c.paidLeave());
                unpaid = unpaid.add(c.unpaidLeave());
                sick = sick.add(c.sickLeave());
                if ("LE".equals(v.autoKind())) holiday = holiday.add(BigDecimal.ONE);
                if ("CONG_TAC".equals(v.autoKind())) trip = trip.add(BigDecimal.ONE);
                if (v.credit() != null) work = work.add(v.credit());
                if (v.status() == DayCredit.Status.DI_LAM) {
                    if (v.lateMinutes() > 0) {
                        lateCount++;
                        lateMinutes += v.lateMinutes();
                    }
                    if (v.earlyMinutes() > 0) earlyCount++;
                    if (v.leavePart() == null && v.credit().compareTo(HALF) == 0) halfDays++;
                }
                if (v.status() == DayCredit.Status.VANG) absent++;
                if (v.status() == DayCredit.Status.THIEU_GIO || v.status() == DayCredit.Status.CHUA_NHAP) missing++;
            }
            out.put(
                e.getId(),
                new MonthView(views, standard, clean(actual), clean(work), clean(paid), clean(unpaid), clean(sick), clean(holiday), clean(trip),
                    lateCount, lateMinutes, earlyCount, halfDays, absent, missing)
            );
        }
        return out;
    }

    // ------------------------------------------------------------------ gõ

    /**
     * Lưu giờ gõ tay ngày {@code date} của các dòng {@code entries}: thay toàn bộ lần chấm {@code GO_TAY} của người đó trong ngày (không
     * động tới lần chấm {@code APP}) và đánh dấu vắng. Một người chỉ một dòng ({@code attendanceduplicateentry}). Người gõ không phải
     * Quản trị/HR bị kiểm quyền (403, kể cả id không tồn tại) trước mọi lỗi khác; sau đó: tháng đã chốt, ngày chưa tới, rồi từng dòng: hồ
     * sơ lưu trữ, quyền (Quản trị/HR), giờ chưa tới, giờ ra trước giờ vào, vắng kèm giờ, ô tự điền. Chính mình luôn là
     * {@code attendanceself}.
     */
    public List<DayView> save(String login, LocalDate date, List<Entry> entries) {
        if (date == null) {
            throw new EmployeeRuleException("Chọn ngày chấm công", "attendancetimeinvalid");
        }
        List<Entry> list = entries == null ? List.of() : entries.stream().filter(Objects::nonNull).toList();
        Set<Long> seen = new HashSet<>();
        for (Entry en : list) {
            if (en.employeeId() != null && !seen.add(en.employeeId())) {
                throw new EmployeeRuleException("Mỗi người chỉ được một dòng trong một lần lưu", "attendanceduplicateentry");
            }
        }
        boolean companyWide = access.isCompanyWide(login);
        // 1. Người gõ không phải Quản trị/HR: kiểm quyền trước mọi lỗi cụ thể, để không dò được hồ sơ ngoài phạm vi (R8).
        List<Employee> targets = new ArrayList<>();
        for (Entry en : list) {
            Employee t = en.employeeId() == null ? null : employeeRepository.findById(en.employeeId()).orElse(null);
            if (t == null) {
                if (!companyWide) throw forbidden();
                throw new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound");
            }
            if (AttendanceAccess.isSelf(login, t)) {
                throw new EmployeeRuleException("Không ai gõ giờ cho chính mình", "attendanceself");
            }
            if (!companyWide && !access.canEnter(login, t, date)) {
                throw forbidden();
            }
            targets.add(t);
        }
        // 2. Tháng đã chốt, ngày chưa tới.
        if (monthRepository.existsByMonthAndStatus(YearMonth.from(date).toString(), AttendanceMonthStatus.DA_CHOT)) {
            throw new EmployeeRuleException(
                "Bảng công tháng " + YearMonth.from(date).format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy")) + " đã chốt, không sửa được giờ",
                "attendancemonthclosed"
            );
        }
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today)) {
            throw future();
        }
        // 3. Từng dòng: hồ sơ lưu trữ, quyền (Quản trị/HR), giờ chưa tới, giờ ra trước giờ vào, vắng kèm giờ, ô tự điền.
        LocalTime now = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        Loaded loaded = targets.isEmpty() ? null : new Loaded(targets, date, date);
        for (int i = 0; i < list.size(); i++) {
            Entry en = list.get(i);
            Employee t = targets.get(i);
            // Sau bước kiểm quyền ở trên: chỉ Quản trị/HR (hoặc người đã có quyền) mới thấy lỗi cụ thể về hồ sơ lưu trữ (R8).
            archivedGuard.requireActive(t);
            if (companyWide && !access.canEnter(login, t, date)) {
                throw forbidden();
            }
            if (date.equals(today) && ((en.in() != null && en.in().isAfter(now)) || (en.out() != null && en.out().isAfter(now)))) {
                throw future();
            }
            if (en.in() != null && en.out() != null && !en.out().isAfter(en.in())) {
                throw new EmployeeRuleException("Giờ ra phải sau giờ vào", "attendancetimeorder");
            }
            if (en.absent() && (en.in() != null || en.out() != null)) {
                throw new EmployeeRuleException("Đã đánh dấu vắng thì không nhập giờ vào, giờ ra", "attendanceabsentwithtimes");
            }
            boolean hasInput = en.in() != null || en.out() != null || en.absent();
            if (hasInput && loaded.compute(t, date).view().autoFilled()) {
                throw new EmployeeRuleException("Ngày này đã tự điền (nghỉ, lễ, công tác…), không gõ giờ", "attendanceautofilled");
            }
        }
        Instant at = Instant.now(clock);
        String who = login == null ? "system" : login.length() > 50 ? login.substring(0, 50) : login;
        for (int i = 0; i < list.size(); i++) {
            Entry en = list.get(i);
            Long id = targets.get(i).getId();
            punchRepository.deleteAll(punchRepository.findByEmployeeIdAndWorkDayAndSource(id, date, AttendancePunchSource.GO_TAY));
            if (en.in() != null) punchRepository.save(punch(id, date, en.in(), AttendancePunchKind.VAO, who, at));
            if (en.out() != null) punchRepository.save(punch(id, date, en.out(), AttendancePunchKind.RA, who, at));
            AttendanceMark mark = markRepository.findByEmployeeIdAndWorkDay(id, date).orElse(null);
            if (en.absent() && mark == null) {
                mark = new AttendanceMark();
                mark.setEmployeeId(id);
                mark.setWorkDay(date);
                mark.setKind(AttendanceMarkKind.VANG);
                mark.setEnteredBy(who);
                mark.setEnteredAt(at);
                markRepository.save(mark);
            } else if (!en.absent() && mark != null) {
                markRepository.delete(mark);
            }
        }
        punchRepository.flush();
        markRepository.flush();
        return days(targets, date);
    }

    private static AccessDeniedException forbidden() {
        return new AccessDeniedException("Không có quyền gõ giờ cho người này vào ngày này");
    }

    private static EmployeeRuleException future() {
        return new EmployeeRuleException("Không gõ giờ cho thời điểm chưa tới", "attendancefuture");
    }

    private static AttendancePunch punch(Long employeeId, LocalDate day, LocalTime t, AttendancePunchKind kind, String who, Instant at) {
        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(employeeId);
        p.setWorkDay(day);
        p.setPunchTime(t);
        p.setKind(kind);
        p.setSource(AttendancePunchSource.GO_TAY);
        p.setEnteredBy(who);
        p.setEnteredAt(at);
        return p;
    }

    /** Nhóm của một người trong ngày (khách hàng onsite, hoặc văn phòng). */
    public static String groupKey(WorkSite site) {
        return site == null ? OFFICE : "SITE:" + site.getId();
    }

    private static BigDecimal clean(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros();
    }

    // ------------------------------------------------------------------ tính

    private record Computed(DayView view, BigDecimal paidLeave, BigDecimal unpaidLeave, BigDecimal sickLeave) {}

    /** Dữ liệu nguồn của một nhóm người trong một khoảng ngày, nạp một lần. */
    private final class Loaded {

        final Set<DayOfWeek> workingDays;
        final Set<LocalDate> holidays;
        final Map<Long, List<LeaveRequest>> leaves;
        final Map<Long, List<WorkAssignment>> assignments;
        final Map<Long, Map<LocalDate, List<AttendancePunch>>> punches;
        final Set<String> absent = new HashSet<>();
        final Map<String, DayCredit.Hours> hours = new HashMap<>();
        final Map<LocalDate, DayCredit.Rules> rules = new HashMap<>();
        final LocalDate today = LocalDate.now(clock);

        Loaded(Collection<Employee> employees, LocalDate from, LocalDate to) {
            List<Long> ids = employees.stream().map(Employee::getId).toList();
            workingDays = EnumSet.copyOf(calendarService.workingDays());
            holidays = holidayRepository.findByDayBetweenOrderByDayAsc(from, to).stream().map(Holiday::getDay).collect(Collectors.toSet());
            leaves = leaveRepository
                .findForCalendar(ids, LEAVE_STATUSES, from, to)
                .stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));
            assignments = assignmentRepository.findOverlapping(ids, from, to).stream().collect(Collectors.groupingBy(w -> w.getEmployee().getId()));
            punches = punchRepository
                .findByEmployeeIdInAndWorkDayBetween(ids, from, to)
                .stream()
                .collect(Collectors.groupingBy(AttendancePunch::getEmployeeId, Collectors.groupingBy(AttendancePunch::getWorkDay)));
            for (AttendanceMark m : markRepository.findByEmployeeIdInAndWorkDayBetween(ids, from, to)) {
                if (m.getKind() == AttendanceMarkKind.VANG) {
                    absent.add(m.getEmployeeId() + "@" + m.getWorkDay());
                }
            }
        }

        Computed compute(Employee e, LocalDate d) {
            WorkAssignment trip = null, onsite = null, remote = null;
            for (WorkAssignment w : assignments.getOrDefault(e.getId(), List.of())) {
                if (!covers(w, d) || w.getType() == null) continue;
                switch (w.getType()) {
                    case CONG_TAC -> trip = trip == null ? w : trip;
                    case KHACH_HANG -> onsite = onsite == null && w.getSite() != null ? w : onsite;
                    case TU_XA -> remote = remote == null ? w : remote;
                    default -> {}
                }
            }
            WorkSite site = onsite == null ? null : onsite.getSite();
            String group = groupKey(site);
            String label = site == null ? OFFICE_LABEL : "Onsite · " + site.getName();
            boolean isRemote = site == null && remote != null;

            if ((e.getJoinDate() != null && d.isBefore(e.getJoinDate())) || (e.getLastWorkingDay() != null && d.isAfter(e.getLastWorkingDay()))) {
                return auto(e, d, group, label, isRemote, "NGOAI_HD", BigDecimal.ZERO);
            }
            if (!workingDays.contains(d.getDayOfWeek())) {
                return auto(e, d, group, label, isRemote, "NGHI", BigDecimal.ZERO);
            }
            if (holidays.contains(d)) {
                return auto(e, d, group, label, isRemote, "LE", BigDecimal.ONE);
            }
            LeaveRequestType morning = null, afternoon = null;
            for (LeaveRequest r : leaves.getOrDefault(e.getId(), List.of())) {
                int mask = halves(r, d);
                if ((mask & 1) != 0 && morning == null) morning = r.getType();
                if ((mask & 2) != 0 && afternoon == null) afternoon = r.getType();
            }
            if (morning != null && afternoon != null) {
                Computed c = auto(e, d, group, label, isRemote, morning.name(), share(morning, PAID::contains).add(share(afternoon, PAID::contains)));
                return new Computed(
                    c.view(),
                    clean(share(morning, PAID::contains).add(share(afternoon, PAID::contains))),
                    clean(share(morning, t -> t == LeaveRequestType.KHONG_LUONG).add(share(afternoon, t -> t == LeaveRequestType.KHONG_LUONG))),
                    clean(share(morning, t -> t == LeaveRequestType.OM).add(share(afternoon, t -> t == LeaveRequestType.OM)))
                );
            }
            if (trip != null) {
                return auto(e, d, group, label, isRemote, "CONG_TAC", BigDecimal.ONE);
            }
            LeaveRequestType half = morning != null ? morning : afternoon;
            DayPart part = morning != null ? DayPart.SANG : afternoon != null ? DayPart.CHIEU : null;

            List<LocalTime> ins = new ArrayList<>();
            List<LocalTime> outs = new ArrayList<>();
            for (AttendancePunch p : punches.getOrDefault(e.getId(), Map.of()).getOrDefault(d, List.of())) {
                (p.getKind() == AttendancePunchKind.VAO ? ins : outs).add(p.getPunchTime());
            }
            Long siteId = site == null ? null : site.getId();
            DayCredit.Hours h = hours.computeIfAbsent(d + "@" + siteId, k -> scheduleService.hoursFor(d, siteId));
            DayCredit.Rules ru = rules.computeIfAbsent(d, scheduleService::rules);
            boolean markedAbsent = absent.contains(e.getId() + "@" + d);
            DayCredit.Result r = DayCredit.compute(ins, outs, h, ru, part, markedAbsent);
            DayCredit.Status status = r.status();
            if (status == DayCredit.Status.CHUA_NHAP && !d.isBefore(today)) {
                status = null; // ngày chưa qua: chưa phải ô đỏ "chưa nhập"
            }
            BigDecimal leaveShare = half == null ? BigDecimal.ZERO : HALF;
            BigDecimal paidShare = half != null && PAID.contains(half) ? HALF : BigDecimal.ZERO;
            BigDecimal actual = (r.workCredit() == null ? BigDecimal.ZERO : r.workCredit()).add(paidShare);
            DayView v = new DayView(
                e.getId(),
                e.getCode(),
                e.getFullName(),
                d,
                group,
                label,
                half == null ? null : half.name(),
                clean(r.workCredit()),
                clean(actual),
                status,
                r.firstIn(),
                r.lastOut(),
                r.lateMinutes(),
                r.earlyMinutes(),
                isRemote,
                part == null ? null : part.name(),
                markedAbsent
            );
            return new Computed(
                v,
                clean(paidShare),
                half == LeaveRequestType.KHONG_LUONG ? leaveShare : BigDecimal.ZERO,
                half == LeaveRequestType.OM ? leaveShare : BigDecimal.ZERO
            );
        }

        private Computed auto(Employee e, LocalDate d, String group, String label, boolean remote, String kind, BigDecimal actual) {
            DayView v = new DayView(e.getId(), e.getCode(), e.getFullName(), d, group, label, kind, null, clean(actual), null, null, null, 0, 0, remote, null, false);
            return new Computed(v, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private static BigDecimal share(LeaveRequestType t, java.util.function.Predicate<LeaveRequestType> p) {
        return t != null && p.test(t) ? HALF : BigDecimal.ZERO;
    }

    private static boolean covers(WorkAssignment w, LocalDate d) {
        return (w.getFromDate() == null || !d.isBefore(w.getFromDate())) && (w.getToDate() == null || !d.isAfter(w.getToDate()));
    }

    /** Buổi của ngày {@code d} mà đơn nghỉ phủ: bit 1 sáng, bit 2 chiều (0 nếu không phủ). */
    static int halves(LeaveRequest r, LocalDate d) {
        if (r.getFromDate() == null || r.getToDate() == null || d.isBefore(r.getFromDate()) || d.isAfter(r.getToDate())) {
            return 0;
        }
        if (r.getFromDate().equals(r.getToDate())) {
            return mask(r.getFirstPart());
        }
        if (d.equals(r.getFromDate())) {
            return r.getFirstPart() == DayPart.CHIEU ? 2 : 3;
        }
        if (d.equals(r.getToDate())) {
            return r.getLastPart() == DayPart.SANG ? 1 : 3;
        }
        return 3;
    }

    private static int mask(DayPart p) {
        if (p == null) return 3;
        return switch (p) {
            case SANG -> 1;
            case CHIEU -> 2;
            case CA_NGAY -> 3;
        };
    }

    /** Sắp các dòng theo mã. */
    public static final Comparator<DayView> BY_CODE = Comparator.comparing(DayView::code, Comparator.nullsLast(Comparator.naturalOrder()));
}

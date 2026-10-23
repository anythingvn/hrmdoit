package vn.doit.hrm.service.attendance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.security.AuthoritiesConstants;

/**
 * Ai được gõ giờ cho ai, vào ngày nào (spec 3.4):
 * <ul>
 *   <li>Quản trị, HR: mọi nhân viên;</li>
 *   <li>trưởng nhóm ({@code Employee.manager}): người trong nhóm mình;</li>
 *   <li>trưởng nhóm onsite của khách hàng X: người có phân công {@code KHACH_HANG} ở X còn hiệu lực đúng ngày đó.</li>
 * </ul>
 * Trưởng nhóm (thường hoặc onsite) có hồ sơ đã lưu trữ hoặc đã nghỉ việc thì không còn quyền gì. Không ai gõ cho chính mình. Chỉ
 * chấm công {@code NHAN_VIEN} chưa lưu trữ, vào ngày thuộc một lần làm việc ({@link EmploymentWindow}: lần hiện tại và các lần trước khi
 * nhận làm lại).
 */
@Service
@Transactional(readOnly = true)
public class AttendanceAccess {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final WorkAssignmentRepository assignmentRepository;
    private final AttendanceScheduleService scheduleService;
    private final AttendanceMonthRepository monthRepository;
    private final PayrollPeriodRepository periodRepository;
    private final EmploymentWindow window;
    private final Clock clock;

    public AttendanceAccess(
        EmployeeRepository employeeRepository,
        UserRepository userRepository,
        WorkAssignmentRepository assignmentRepository,
        AttendanceScheduleService scheduleService,
        AttendanceMonthRepository monthRepository,
        PayrollPeriodRepository periodRepository,
        EmploymentWindow window,
        Clock clock
    ) {
        this.monthRepository = monthRepository;
        this.periodRepository = periodRepository;
        this.window = window;
        this.clock = clock;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.scheduleService = scheduleService;
    }

    /** {@code login} được gõ giờ ngày {@code day} cho {@code target} (không gồm chính mình). */
    public boolean canEnter(String login, Employee target, LocalDate day) {
        if (login == null || target == null || day == null || !attendable(target, day) || isSelf(login, target)) {
            return false;
        }
        if (companyWide(login)) {
            return true;
        }
        Employee me = activeLead(login);
        if (me == null) {
            return false;
        }
        if (target.getManager() != null && Objects.equals(target.getManager().getId(), me.getId())) {
            return true;
        }
        Set<Long> sites = scheduleService.onsiteLeadSiteIds(me.getId());
        return !sites.isEmpty() && onsiteAt(target.getId(), sites, day);
    }

    /** Những người {@code login} được gõ giờ ngày {@code day}, theo mã. */
    public List<Employee> enterableEmployees(String login, LocalDate day) {
        if (login == null || day == null) {
            return List.of();
        }
        Map<Long, Employee> out = new LinkedHashMap<>();
        if (companyWide(login)) {
            employeeRepository.findByPersonTypeAndArchivedAtIsNull(PersonType.NHAN_VIEN).forEach(e -> out.put(e.getId(), e));
        } else {
            Employee me = activeLead(login);
            if (me == null) {
                return List.of();
            }
            employeeRepository.findByManagerIdAndArchivedAtIsNullOrderByCode(me.getId()).forEach(e -> out.put(e.getId(), e));
            Set<Long> sites = scheduleService.onsiteLeadSiteIds(me.getId());
            if (!sites.isEmpty()) {
                assignmentRepository.findOnsiteOn(sites, day).forEach(w -> out.putIfAbsent(w.getEmployee().getId(), w.getEmployee()));
            }
        }
        EmploymentWindow.Spans spans = window.of(out.values());
        return out
            .values()
            .stream()
            .filter(e -> attendable(e, day, spans) && !isSelf(login, e))
            .sorted(Comparator.comparing(Employee::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /**
     * {@code login} còn gõ được cho ai đó (cùng phạm vi với {@link #canEnter}); quyết định cả cổng API màn gõ và cờ menu
     * {@code entersAttendance}. Đúng khi là Quản trị, HR; hoặc là trưởng nhóm / trưởng nhóm onsite (còn làm việc, chưa lưu trữ) có ít
     * nhất một ngày còn gõ được. Tháng "đã khóa" là tháng đã chốt công hoặc có kỳ lương đã duyệt (R17):
     * <ul>
     *   <li>người trong nhóm là NHAN_VIEN chưa lưu trữ, đang làm việc hoặc có ngày làm việc cuối thuộc tháng chưa khóa;</li>
     *   <li>phân công {@code KHACH_HANG} ở khách hàng mình làm trưởng nhóm onsite, người được phân công là NHAN_VIEN chưa lưu trữ, đã bắt
     *   đầu (không sau hôm nay), và ngày kết thúc thực tế (ngày kết thúc phân công, chặn bởi ngày làm việc cuối) để trống hoặc thuộc
     *   tháng chưa khóa. Trạng thái hoạt động của nơi làm việc không liên quan.</li>
     * </ul>
     */
    public boolean entersAnyone(String login) {
        if (login == null) {
            return false;
        }
        if (companyWide(login)) {
            return true;
        }
        Employee me = activeLead(login);
        if (me == null) {
            return false;
        }
        Set<String> closed = lockedMonths();
        for (Employee r : employeeRepository.findByManagerIdAndArchivedAtIsNullOrderByCode(me.getId())) {
            if (r.getPersonType() != PersonType.NHAN_VIEN) {
                continue;
            }
            if (r.getStatus() == EmploymentStatus.DANG_LAM_VIEC) {
                return true;
            }
            if (r.getLastWorkingDay() != null && !closed.contains(YearMonth.from(r.getLastWorkingDay()).toString())) {
                return true;
            }
        }
        Set<Long> sites = scheduleService.onsiteLeadSiteIds(me.getId());
        if (sites.isEmpty()) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        for (WorkAssignment w : assignmentRepository.findOnsiteStartedBy(sites, today)) {
            Employee e = w.getEmployee();
            if (e.getPersonType() != PersonType.NHAN_VIEN || e.isArchived()) {
                continue;
            }
            LocalDate end = w.getToDate();
            if (e.getLastWorkingDay() != null && (end == null || e.getLastWorkingDay().isBefore(end))) {
                end = e.getLastWorkingDay();
            } else if (end == null && e.getStatus() == EmploymentStatus.DA_NGHI) {
                continue; // đã nghỉ mà không có ngày làm việc cuối: attendable loại
            }
            if (end != null && w.getFromDate() != null && end.isBefore(w.getFromDate())) {
                continue;
            }
            if (end == null || !closed.contains(YearMonth.from(end).toString())) {
                return true;
            }
        }
        return false;
    }

    /** Tháng (yyyy-MM) không còn gõ được: bảng công đã chốt, hoặc kỳ lương tháng đó đã duyệt (R17). */
    public Set<String> lockedMonths() {
        Set<String> out = new HashSet<>(monthRepository.findMonthsInRange(AttendanceMonthStatus.DA_CHOT, "0000-01", null));
        out.addAll(periodRepository.findPeriodsByStatus(PayrollPeriodStatus.DA_DUYET));
        return out;
    }

    /**
     * Phạm vi trưởng nhóm / trưởng nhóm onsite của {@code login} trong tháng {@code m}, đúng như {@link #canEnter} (R24): {@code team} là
     * người trong nhóm (mọi ngày làm việc), {@code onsite} là người onsite ở khách hàng mình làm trưởng nhóm onsite kèm đúng các ngày đó.
     * Chỉ NHAN_VIEN chưa lưu trữ, ngày trong một lần làm việc, không gồm chính mình. Không phải trưởng nhóm còn làm việc thì rỗng.
     */
    public LeadScope leadScope(String login, YearMonth m) {
        Employee me = login == null ? null : activeLead(login);
        if (me == null) {
            return LeadScope.EMPTY;
        }
        Map<Long, Set<LocalDate>> team = new LinkedHashMap<>();
        Map<Long, Set<LocalDate>> onsite = new LinkedHashMap<>();
        List<Employee> reports = employeeRepository.findByManagerIdAndArchivedAtIsNullOrderByCode(me.getId());
        Set<Long> sites = scheduleService.onsiteLeadSiteIds(me.getId());
        List<WorkAssignment> assignments = sites.isEmpty()
            ? List.of()
            : assignmentRepository.findOnsiteOverlapping(sites, m.atDay(1), m.atEndOfMonth());
        Map<Long, Employee> everyone = new LinkedHashMap<>();
        reports.forEach(e -> everyone.put(e.getId(), e));
        assignments.forEach(w -> everyone.putIfAbsent(w.getEmployee().getId(), w.getEmployee()));
        EmploymentWindow.Spans spans = window.of(everyone.values());
        for (Employee e : reports) {
            Set<LocalDate> days = new TreeSet<>();
            for (LocalDate d = m.atDay(1); !d.isAfter(m.atEndOfMonth()); d = d.plusDays(1)) {
                if (attendable(e, d, spans) && !isSelf(login, e)) days.add(d);
            }
            if (!days.isEmpty()) team.put(e.getId(), days);
        }
        for (WorkAssignment w : assignments) {
            Employee e = w.getEmployee();
            for (LocalDate d = m.atDay(1); !d.isAfter(m.atEndOfMonth()); d = d.plusDays(1)) {
                boolean on = (w.getFromDate() == null || !d.isBefore(w.getFromDate())) && (w.getToDate() == null || !d.isAfter(w.getToDate()));
                if (on && attendable(e, d, spans) && !isSelf(login, e)) {
                    onsite.computeIfAbsent(e.getId(), k -> new TreeSet<>()).add(d);
                }
            }
        }
        return new LeadScope(team, onsite);
    }

    /**
     * Phạm vi trưởng nhóm trong một tháng: ngày gõ được của từng người, theo nhóm ({@code team}) và theo onsite ({@code onsite}).
     */
    public record LeadScope(Map<Long, Set<LocalDate>> team, Map<Long, Set<LocalDate>> onsite) {
        static final LeadScope EMPTY = new LeadScope(Map.of(), Map.of());

        /** Ngày {@code login} gõ được cho người {@code id} trong tháng (hợp hai phạm vi). */
        public Set<LocalDate> days(Long id) {
            Set<LocalDate> out = new LinkedHashSet<>(team.getOrDefault(id, Set.of()));
            out.addAll(onsite.getOrDefault(id, Set.of()));
            return out;
        }
    }

    /**
     * {@code login} là trưởng nhóm hoặc trưởng nhóm onsite còn làm việc, có ít nhất một người NHAN_VIEN chưa lưu trữ trong nhóm hoặc là
     * trưởng nhóm onsite của ít nhất một khách hàng: được xem bảng công tháng trong phạm vi của mình (R24).
     */
    public boolean leadsAnyone(String login) {
        Employee me = login == null ? null : activeLead(login);
        if (me == null) {
            return false;
        }
        boolean team = employeeRepository
            .findByManagerIdAndArchivedAtIsNullOrderByCode(me.getId())
            .stream()
            .anyMatch(e -> e.getPersonType() == PersonType.NHAN_VIEN);
        return team || !scheduleService.onsiteLeadSiteIds(me.getId()).isEmpty();
    }

    /** Quản trị hoặc HR: gõ cho cả công ty. */
    public boolean isCompanyWide(String login) {
        return login != null && companyWide(login);
    }

    /** Vai trò của {@code login} (phiên hiện tại nếu đúng người đó, không thì từ tài khoản). */
    public Set<String> roles(String login) {
        return login == null ? Set.of() : authorities(login);
    }

    /** Hồ sơ {@code target} gắn với tài khoản {@code login}. */
    public static boolean isSelf(String login, Employee target) {
        return target != null && target.getUser() != null && login != null && login.equalsIgnoreCase(target.getUser().getLogin());
    }

    /** Người được chấm công ngày {@code day}: NHAN_VIEN, chưa lưu trữ, ngày thuộc một lần làm việc ({@link EmploymentWindow}). */
    private boolean attendable(Employee e, LocalDate day) {
        return attendable(e, day, window.of(List.of(e)));
    }

    private static boolean attendable(Employee e, LocalDate day, EmploymentWindow.Spans spans) {
        return e.getPersonType() == PersonType.NHAN_VIEN && !e.isArchived() && spans.covers(e, day);
    }

    private boolean onsiteAt(Long employeeId, Set<Long> sites, LocalDate day) {
        for (WorkAssignment w : assignmentRepository.findOverlapping(List.of(employeeId), day, day)) {
            if (w.getType() == WorkLocationType.KHACH_HANG && w.getSite() != null && sites.contains(w.getSite().getId())) {
                return true;
            }
        }
        return false;
    }

    /** Hồ sơ của {@code login} khi còn làm việc và chưa lưu trữ (R5); ngược lại null. */
    private Employee activeLead(String login) {
        Employee me = employeeRepository.findOneByUserLogin(login).orElse(null);
        if (me == null || me.isArchived() || me.getStatus() == EmploymentStatus.DA_NGHI) {
            return null;
        }
        return me;
    }

    private boolean companyWide(String login) {
        Set<String> roles = authorities(login);
        return roles.contains(AuthoritiesConstants.ADMIN) || roles.contains(AuthoritiesConstants.HR);
    }

    /** Vai trò của {@code login}: lấy từ phiên hiện tại nếu đúng người đó, không thì đọc từ tài khoản. */
    private Set<String> authorities(String login) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && login.equalsIgnoreCase(auth.getName())) {
            return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        }
        return userRepository
            .findOneWithAuthoritiesByLogin(login)
            .map(u -> u.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet()))
            .orElse(Set.of());
    }
}

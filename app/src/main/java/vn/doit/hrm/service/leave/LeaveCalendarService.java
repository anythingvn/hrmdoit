package vn.doit.hrm.service.leave;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.dto.LeaveCalendarDTO;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Lịch nghỉ tháng (spec 6.1): đơn nghỉ (chờ duyệt hiện mờ), đi công tác từ lịch sử vị trí, nghỉ việc từ lịch đã hẹn hoặc đã áp
 * dụng. Chỉ trả người trong tập được xem; không trả lý do. Ba truy vấn cho cả tháng, không truy vấn theo từng người.
 */
@Service
@Transactional(readOnly = true)
public class LeaveCalendarService {

    static final Set<String> TYPES = Set.of("PHEP_NAM", "VIEC_RIENG", "OM", "KHONG_LUONG", "NGHI_BU", "CONG_TAC", "NGHI_VIEC");

    private static final Set<LeaveRequestStatus> SHOWN = EnumSet.of(
        LeaveRequestStatus.CHO_DUYET,
        LeaveRequestStatus.DA_DUYET,
        LeaveRequestStatus.CHO_HUY
    );

    private final LeaveVisibility visibility;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository requestRepository;
    private final WorkAssignmentRepository assignmentRepository;
    private final TerminationRecordRepository terminationRepository;
    private final WorkCalendarService workCalendar;

    public LeaveCalendarService(
        LeaveVisibility visibility,
        EmployeeRepository employeeRepository,
        LeaveRequestRepository requestRepository,
        WorkAssignmentRepository assignmentRepository,
        TerminationRecordRepository terminationRepository,
        WorkCalendarService workCalendar
    ) {
        this.visibility = visibility;
        this.employeeRepository = employeeRepository;
        this.requestRepository = requestRepository;
        this.assignmentRepository = assignmentRepository;
        this.terminationRepository = terminationRepository;
        this.workCalendar = workCalendar;
    }

    /** Phòng ban của những người được xem lịch (để lọc), sắp theo tên. Không cần quyền xem danh mục phòng ban. */
    public List<LeaveCalendarDTO.DepartmentOption> departments() {
        Set<Long> visible = visibility.visibleEmployeeIds();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<Long, LeaveCalendarDTO.DepartmentOption> out = new HashMap<>();
        for (Employee e : employeeRepository.findNhanVienWithDepartmentByIdIn(visible)) {
            if (e.getDepartment() != null && e.getStatus() != EmploymentStatus.DA_NGHI) {
                out.putIfAbsent(
                    e.getDepartment().getId(),
                    new LeaveCalendarDTO.DepartmentOption(e.getDepartment().getId(), e.getDepartment().getName())
                );
            }
        }
        return out
            .values()
            .stream()
            .sorted(Comparator.comparing(LeaveCalendarDTO.DepartmentOption::name, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    public LeaveCalendarDTO month(String monthText, Long departmentId, String type) {
        YearMonth month = parseMonth(monthText);
        if (type != null && !type.isBlank() && !TYPES.contains(type)) {
            throw new EmployeeRuleException("Loại không hợp lệ", "leavecalendartype");
        }
        String typeFilter = type == null || type.isBlank() ? null : type;
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<LeaveCalendarDTO.Day> days = days(month);

        Set<Long> visible = visibility.visibleEmployeeIds();
        if (visible.isEmpty()) {
            return new LeaveCalendarDTO(days, List.of());
        }
        Map<Long, Employee> people = employeeRepository
            .findNhanVienWithDepartmentByIdIn(visible)
            .stream()
            .filter(e -> !hasLeftBefore(e, from))
            .filter(e -> departmentId == null || (e.getDepartment() != null && departmentId.equals(e.getDepartment().getId())))
            .collect(Collectors.toMap(Employee::getId, Function.identity()));
        if (people.isEmpty()) {
            return new LeaveCalendarDTO(days, List.of());
        }
        Set<Long> ids = people.keySet();

        List<LeaveCalendarDTO.Item> items = new ArrayList<>();
        for (LeaveRequest r : requestRepository.findForCalendar(ids, SHOWN, from, to)) {
            String t = r.getType().name();
            if (typeFilter != null && !typeFilter.equals(t)) {
                continue;
            }
            Employee e = people.get(r.getEmployee().getId());
            items.add(
                item(
                    e,
                    t,
                    r.getFromDate(),
                    r.getToDate(),
                    r.getFirstPart().name(),
                    r.getLastPart().name(),
                    r.getStatus() == LeaveRequestStatus.CHO_DUYET
                )
            );
        }
        if (typeFilter == null || typeFilter.equals("CONG_TAC")) {
            for (WorkAssignment w : assignmentRepository.findBusinessTripsForCalendar(ids, from, to)) {
                Employee e = people.get(w.getEmployee().getId());
                LocalDate end = w.getToDate() == null ? to : w.getToDate();
                items.add(item(e, "CONG_TAC", w.getFromDate(), end, null, null, false));
            }
        }
        if (typeFilter == null || typeFilter.equals("NGHI_VIEC")) {
            Set<Long> seen = new HashSet<>();
            List<TerminationRecord> records = new ArrayList<>(
                terminationRepository.findByEmployeeIdInAndStatus(ids, TerminationRecordStatus.APPLIED)
            );
            records.addAll(terminationRepository.findByEmployeeIdInAndStatus(ids, TerminationRecordStatus.SCHEDULED));
            records.sort(Comparator.comparing(TerminationRecord::getId).reversed());
            for (TerminationRecord t : records) {
                LocalDate start = t.getLastWorkingDay().plusDays(1);
                if (start.isBefore(from) || start.isAfter(to) || !seen.add(t.getEmployeeId())) {
                    continue;
                }
                items.add(item(people.get(t.getEmployeeId()), "NGHI_VIEC", start, to, null, null, false));
            }
        }
        items.sort(
            Comparator.comparing(LeaveCalendarDTO.Item::from)
                .thenComparing(LeaveCalendarDTO.Item::employeeName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LeaveCalendarDTO.Item::type)
        );
        return new LeaveCalendarDTO(days, items);
    }

    /** Người đã nghỉ việc từ trước tháng này (ngày nghỉ = ngày làm việc cuối + 1 trước ngày đầu tháng) không còn hiện trên lịch. */
    private static boolean hasLeftBefore(Employee e, LocalDate monthStart) {
        return (
            e.getStatus() == EmploymentStatus.DA_NGHI &&
            e.getLastWorkingDay() != null &&
            e.getLastWorkingDay().plusDays(1).isBefore(monthStart)
        );
    }

    private static LeaveCalendarDTO.Item item(
        Employee e,
        String type,
        LocalDate from,
        LocalDate to,
        String first,
        String last,
        boolean pending
    ) {
        return new LeaveCalendarDTO.Item(
            e.getId(),
            e.getFullName(),
            e.getDepartment() == null ? null : e.getDepartment().getName(),
            type,
            from,
            to,
            first,
            last,
            pending
        );
    }

    private List<LeaveCalendarDTO.Day> days(YearMonth month) {
        List<DayOfWeek> working = workCalendar.workingDays();
        Map<LocalDate, String> holidays = workCalendar
            .holidays(month.getYear())
            .stream()
            .collect(Collectors.toMap(h -> h.day(), h -> h.name(), (a, b) -> a));
        List<LeaveCalendarDTO.Day> out = new ArrayList<>();
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            out.add(new LeaveCalendarDTO.Day(date, !working.contains(date.getDayOfWeek()), holidays.get(date)));
        }
        return out;
    }

    private static YearMonth parseMonth(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new EmployeeRuleException("Tháng không hợp lệ (YYYY-MM)", "leavecalendarmonth");
        }
    }
}

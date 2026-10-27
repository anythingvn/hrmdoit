package vn.doit.hrm.service.payroll.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Pool;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.calc.CalcInput;
import vn.doit.hr.payroll.calc.CalcInput.NamedAllowance;
import vn.doit.hr.payroll.calc.CalcInput.OvertimeItem;
import vn.doit.hr.payroll.calc.CalcInput.PersonKind;
import vn.doit.hr.payroll.calc.CalcInput.SalarySegment;
import vn.doit.hr.payroll.calc.CalcSettings;
import vn.doit.hr.payroll.calc.CalcSettings.DayKind;
import vn.doit.hr.payroll.calc.PayrollCalculator;
import vn.doit.hr.reminder.WorkCalendar;
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.domain.AttendanceSnapshot;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.AllowanceRepository;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendanceSnapshotRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.service.CurrentSalary;
import vn.doit.hrm.service.CurrentSalaryCalculator;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.LeaveLedgerService;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Gom đầu vào tính lương của kỳ từ hồ sơ, bảng công đã chốt, làm thêm giờ và sổ phép. Không tính tiền ở đây.
 *
 * <p>Người trong kỳ: có ảnh chụp bảng công của tháng, đã vào làm trước hoặc trong tháng và chưa nghỉ hoặc nghỉ từ đầu tháng.
 */
@Service
@Transactional(readOnly = true)
public class PayrollInputCollector {

    private static final String NO_SALARY = "Chưa có mức lương";
    private static final String NO_JOIN_DATE = "Hồ sơ thiếu ngày vào làm";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM/yyyy");

    /** Một dòng đầu vào của kỳ; {@code errors} không rỗng thì dòng chưa tính được. */
    public record CollectedRow(
        Long employeeId,
        String code,
        String fullName,
        String position,
        CalcInput input,
        List<String> errors,
        List<String> warnings
    ) {}

    private final AttendanceMonthRepository monthRepository;
    private final AttendanceSnapshotRepository snapshotRepository;
    private final EmployeeRepository employeeRepository;
    private final SalaryChangeRepository salaryRepository;
    private final EmploymentContractRepository contractRepository;
    private final CalcSettingsService settingsService;
    private final AllowanceRepository allowanceRepository;
    private final OvertimeRequestRepository overtimeRepository;
    private final LeaveLedgerService ledger;
    private final WorkCalendarService calendarService;

    public PayrollInputCollector(
        AttendanceMonthRepository monthRepository,
        AttendanceSnapshotRepository snapshotRepository,
        EmployeeRepository employeeRepository,
        SalaryChangeRepository salaryRepository,
        EmploymentContractRepository contractRepository,
        CalcSettingsService settingsService,
        AllowanceRepository allowanceRepository,
        OvertimeRequestRepository overtimeRepository,
        LeaveLedgerService ledger,
        WorkCalendarService calendarService
    ) {
        this.monthRepository = monthRepository;
        this.snapshotRepository = snapshotRepository;
        this.employeeRepository = employeeRepository;
        this.salaryRepository = salaryRepository;
        this.contractRepository = contractRepository;
        this.settingsService = settingsService;
        this.allowanceRepository = allowanceRepository;
        this.overtimeRepository = overtimeRepository;
        this.ledger = ledger;
        this.calendarService = calendarService;
    }

    /** Đầu vào của mọi người trong kỳ, theo mã. Ném {@code payrollattendancenotclosed} khi bảng công tháng chưa chốt. */
    public List<CollectedRow> collect(YearMonth period) {
        boolean closed = monthRepository
            .findByMonth(period.toString())
            .map(m -> m.getStatus() == AttendanceMonthStatus.DA_CHOT)
            .orElse(false);
        if (!closed) {
            throw new EmployeeRuleException(
                "Hãy chốt bảng công tháng " + period.format(MM_YYYY) + " trước khi tính lương.",
                "payrollattendancenotclosed"
            );
        }
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();

        Map<Long, AttendanceSnapshot> snapshots = new HashMap<>();
        snapshotRepository.findByMonth(period.toString()).forEach(s -> snapshots.put(s.getEmployeeId(), s));
        List<Employee> people = employeeRepository
            .findAllById(snapshots.keySet())
            .stream()
            // Thiếu ngày vào làm: vẫn đưa vào bảng (dòng lỗi) để Kế toán thấy, không âm thầm bỏ người có công trong tháng.
            .filter(e -> e.getJoinDate() == null || !e.getJoinDate().isAfter(end))
            .filter(e -> e.getLastWorkingDay() == null || !e.getLastWorkingDay().isBefore(start))
            .sorted(Comparator.comparing(Employee::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        if (people.isEmpty()) {
            return List.of();
        }

        List<Long> ids = people.stream().map(Employee::getId).toList();
        Map<Long, List<SalaryChange>> salaries = salaryRepository
            .findByEmployeeIdIn(ids)
            .stream()
            .collect(Collectors.groupingBy(x -> x.getEmployee().getId()));
        Map<Long, List<EmploymentContract>> contracts = contractRepository
            .findByEmployeeIdIn(ids)
            .stream()
            .collect(Collectors.groupingBy(x -> x.getEmployee().getId()));
        CalcSettings settings = settingsService.forPeriod(period);
        Map<Long, List<OvertimeItem>> overtime = new HashMap<>();
        for (OvertimeRequest r : overtimeRepository.findByStatusAndCompensationAndWorkDateBetween(
            OvertimeStatus.HOAN_TAT,
            OvertimeCompensation.TRA_TIEN,
            start,
            end
        )) {
            // Đơn hoàn tất xác nhận không có phần chênh có thể không ghi giờ thực tế: khi đó giờ thực tế là giờ đã đăng ký.
            BigDecimal hours = r.getActualHours() != null ? r.getActualHours() : r.getPlannedHours();
            overtime.computeIfAbsent(r.getEmployee().getId(), k -> new ArrayList<>()).add(new OvertimeItem(hours, r.getRate()));
        }
        WorkCalendar calendar = calendarService.calendar(start);

        List<CollectedRow> rows = new ArrayList<>();
        for (Employee e : people) {
            List<String> errors = new ArrayList<>();
            List<NamedAllowance> named = new ArrayList<>();
            BigDecimal insuredAllowance = allowances(e, start, end, settings, named);
            List<CurrentSalary> timeline = CurrentSalaryCalculator.timeline(
                contracts.getOrDefault(e.getId(), List.of()),
                salaries.getOrDefault(e.getId(), List.of()),
                lastDay(e, end)
            );
            List<String> warnings = new ArrayList<>();
            if (e.getJoinDate() == null) {
                errors.add(NO_JOIN_DATE);
                rows.add(
                    new CollectedRow(
                        e.getId(),
                        e.getCode(),
                        e.getFullName(),
                        e.getPosition(),
                        new CalcInput(kind(e.getPersonType()), false, List.of(), null, null, null, null, null, null, 0, false, null, null),
                        errors,
                        warnings
                    )
                );
                continue;
            }
            List<SalarySegment> segments = segments(e, timeline, insuredAllowance, start, end, calendar);
            LocalDate from = e.getJoinDate().isAfter(start) ? e.getJoinDate() : start;
            if (!timeline.isEmpty() && timeline.get(0).effectiveDate().isAfter(from)) {
                warnings.add(
                    "Mức lương bắt đầu hiệu lực từ " +
                        timeline.get(0).effectiveDate().format(DMY) +
                        ", những ngày trước đó tạm tính theo mức này."
                );
            }
            if (segments.isEmpty()) {
                errors.add(NO_SALARY);
            }
            CalcInput input = new CalcInput(
                kind(e.getPersonType()),
                e.getInsuranceExemption() == null,
                segments,
                e.getSocialInsuranceSalary() == null ? null : BigDecimal.valueOf(e.getSocialInsuranceSalary()),
                days(snapshots.get(e.getId())),
                orZero(snapshots.get(e.getId()).getStandardDays()),
                named,
                overtime.getOrDefault(e.getId(), List.of()),
                unusedLeave(e, start, end),
                e.getDependants() == null ? 0 : e.getDependants(),
                Boolean.TRUE.equals(e.getPitCommitment08()),
                Map.of(),
                Map.of()
            );
            rows.add(new CollectedRow(e.getId(), e.getCode(), e.getFullName(), e.getPosition(), input, errors, warnings));
        }
        return rows;
    }

    private static PersonKind kind(PersonType t) {
        if (t == PersonType.THUC_TAP) {
            return PersonKind.THUC_TAP;
        }
        return t == PersonType.DICH_VU ? PersonKind.DICH_VU : PersonKind.HDLD;
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static Map<DayKind, BigDecimal> days(AttendanceSnapshot s) {
        Map<DayKind, BigDecimal> m = new EnumMap<>(DayKind.class);
        m.put(DayKind.WORK, orZero(s.getWorkDays()));
        m.put(DayKind.PAID_LEAVE, orZero(s.getPaidLeaveDays()));
        m.put(DayKind.HOLIDAY, orZero(s.getHolidayDays()));
        m.put(DayKind.TRIP, orZero(s.getTripDays()));
        m.put(DayKind.SICK, orZero(s.getSickDays()));
        return m;
    }

    private static LocalDate lastDay(Employee e, LocalDate monthEnd) {
        return e.getLastWorkingDay() != null && e.getLastWorkingDay().isBefore(monthEnd) ? e.getLastWorkingDay() : monthEnd;
    }

    /**
     * Các đoạn mức lương trong khoảng người đó làm việc của tháng, theo cùng quy tắc với {@link CurrentSalaryCalculator} (hợp đồng
     * và điều chỉnh lương; trùng ngày thì điều chỉnh thắng). Mức hiệu lực tại ngày đầu khoảng kéo dài đến mức kế; tỷ lệ là số ngày
     * làm việc của đoạn trên tổng ngày làm việc của khoảng (tổng bằng 1; đoạn cuối lấy phần còn lại). PC đóng BH như nhau ở mọi đoạn
     * (bộ tính nhân với tỷ lệ).
     */
    private static List<SalarySegment> segments(
        Employee e,
        List<CurrentSalary> timeline,
        BigDecimal insuredAllowance,
        LocalDate monthStart,
        LocalDate monthEnd,
        WorkCalendar calendar
    ) {
        LocalDate from = e.getJoinDate().isAfter(monthStart) ? e.getJoinDate() : monthStart;
        LocalDate to = lastDay(e, monthEnd);
        // Chỉ giữ mức hiệu lực tại ngày đầu khoảng và các mức đổi sau đó.
        int first = 0;
        for (int i = 0; i < timeline.size(); i++) {
            if (!timeline.get(i).effectiveDate().isAfter(from)) {
                first = i;
            }
        }
        List<CurrentSalary> used = timeline.subList(first, timeline.size());
        if (used.isEmpty()) {
            return List.of();
        }
        long total = workingDays(calendar, from, to);
        List<SalarySegment> out = new ArrayList<>();
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < used.size(); i++) {
            CurrentSalary s = used.get(i);
            BigDecimal share;
            if (i == used.size() - 1) {
                share = BigDecimal.ONE.subtract(given);
            } else {
                LocalDate segFrom = i == 0 ? from : s.effectiveDate();
                LocalDate segTo = used
                    .get(i + 1)
                    .effectiveDate()
                    .minusDays(1);
                share =
                    total == 0
                        ? i == 0
                            ? BigDecimal.ONE
                            : BigDecimal.ZERO
                        : BigDecimal.valueOf(workingDays(calendar, segFrom, segTo)).divide(
                              BigDecimal.valueOf(total),
                              10,
                              RoundingMode.HALF_UP
                          );
                given = given.add(share);
            }
            out.add(new SalarySegment(BigDecimal.valueOf(s.amount()), insuredAllowance, s.basis() == SalaryBasis.NET, share));
        }
        return out;
    }

    private static long workingDays(WorkCalendar calendar, LocalDate from, LocalDate to) {
        long n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (calendar.isWorkingDay(d)) {
                n++;
            }
        }
        return n;
    }

    /** Cộng phụ cấp tên khớp từ khóa PC đóng BH và trả tổng; các phụ cấp khác (đang hiệu lực trong kỳ) vào {@code named}. */
    private BigDecimal allowances(Employee e, LocalDate start, LocalDate end, CalcSettings settings, List<NamedAllowance> named) {
        List<String> keywords = settings.allowanceKeywords().getOrDefault(PayrollField.PHU_CAP_DONG_BH, List.of());
        BigDecimal insured = BigDecimal.ZERO;
        for (Allowance a : allowanceRepository.findByEmployeeId(e.getId())) {
            boolean active =
                a.getFromDate() != null && !a.getFromDate().isAfter(end) && (a.getToDate() == null || !a.getToDate().isBefore(start));
            if (!active || a.getAmountPerMonth() == null) {
                continue;
            }
            String name = PayrollCalculator.plain(a.getName());
            boolean insuredKind = keywords
                .stream()
                .map(PayrollCalculator::plain)
                .anyMatch(k -> !k.isEmpty() && name.contains(k));
            if (insuredKind) {
                insured = insured.add(BigDecimal.valueOf(a.getAmountPerMonth()));
            } else {
                named.add(new NamedAllowance(a.getName(), BigDecimal.valueOf(a.getAmountPerMonth())));
            }
        }
        return insured;
    }

    /**
     * Số ngày phép năm còn tại ngày nghỉ việc nếu ngày đó nằm trong kỳ, không thì null. Bỏ qua dòng chốt khi nghỉ việc và dòng hoàn
     * tác của nó (đưa số dư về 0): số cần trả là số dư ngay trước khi chốt.
     */
    private BigDecimal unusedLeave(Employee e, LocalDate start, LocalDate end) {
        LocalDate last = e.getLastWorkingDay();
        if (last == null || last.isBefore(start) || last.isAfter(end)) {
            return null;
        }
        List<LeaveLedgerEntry> rows = ledger
            .entries(e.getId())
            .stream()
            .filter(r -> r.getReason() != LedgerReason.NGHI_VIEC && r.getReason() != LedgerReason.HOAN_TAC)
            .toList();
        return ledger.balanceAsOf(rows, Pool.PHEP_NAM, last).total().max(BigDecimal.ZERO);
    }
}

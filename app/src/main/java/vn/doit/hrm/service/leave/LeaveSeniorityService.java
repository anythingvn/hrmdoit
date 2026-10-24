package vn.doit.hrm.service.leave;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.AnnualAccrual;
import vn.doit.hr.leave.DateRange;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentPeriod;
import vn.doit.hrm.domain.LeaveSetting;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmploymentPeriodRepository;

/**
 * Mức phép năm của một tháng (Điều 114 BLLĐ 2019): mức gốc của hồ sơ + ⌊thâm niên tính đến ngày cuối tháng ÷ chu kỳ⌋ × số ngày cộng
 * (theo cài đặt đang có hiệu lực). Thâm niên = các lần làm nhân viên chính thức trước ({@code employment_period} có loại NHAN_VIEN
 * hoặc chưa ghi loại) + lần làm hiện tại; khoảng nghỉ giữa các lần làm không tính; thực tập, HĐ dịch vụ không tính.
 */
@Service
@Transactional(readOnly = true)
public class LeaveSeniorityService {

    /** Mức gốc mặc định khi hồ sơ chưa ghi. */
    static final int DEFAULT_BASE = 12;

    /**
     * Mức phép năm của tháng.
     *
     * @param baseAnnualDays mức gốc của hồ sơ
     * @param seniorityYears số năm thâm niên tròn tính đến ngày cuối tháng
     * @param seniorityExtraDays số ngày cộng thêm theo thâm niên
     * @param annualDays mức năm = gốc + phần cộng thêm
     */
    public record AnnualRate(int baseAnnualDays, int seniorityYears, int seniorityExtraDays, int annualDays) {}

    private final EmploymentPeriodRepository periodRepository;
    private final LeaveSettingService settings;

    public LeaveSeniorityService(EmploymentPeriodRepository periodRepository, LeaveSettingService settings) {
        this.periodRepository = periodRepository;
        this.settings = settings;
    }

    /** Mức phép năm của {@code e} cho tháng {@code month}. */
    public AnnualRate rate(Employee e, YearMonth month) {
        return rate(e, periodRepository.findByEmployeeIdOrderByJoinDateAscIdAsc(e.getId()), month, settings.current());
    }

    /** Mức phép năm của nhiều hồ sơ cho tháng {@code month} (đọc các lần làm trước một lần). */
    public Map<Long, AnnualRate> rates(Collection<Employee> people, YearMonth month) {
        if (people.isEmpty()) {
            return Map.of();
        }
        LeaveSetting s = settings.current();
        Map<Long, List<EmploymentPeriod>> periods = periodRepository
            .findByEmployeeIdIn(people.stream().map(Employee::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(EmploymentPeriod::getEmployeeId));
        return people
            .stream()
            .collect(Collectors.toMap(Employee::getId, e -> rate(e, periods.getOrDefault(e.getId(), List.of()), month, s)));
    }

    private static AnnualRate rate(Employee e, List<EmploymentPeriod> periods, YearMonth month, LeaveSetting s) {
        int base = e.getAnnualLeaveDays() == null ? DEFAULT_BASE : e.getAnnualLeaveDays();
        int years = AnnualAccrual.seniorityYears(spans(e, periods), month.atEndOfMonth());
        int annual = AnnualAccrual.annualDays(base, years, s.getSeniorityCycleYears(), s.getSeniorityExtraDays());
        return new AnnualRate(base, years, annual - base, annual);
    }

    private static List<DateRange> spans(Employee e, List<EmploymentPeriod> periods) {
        List<DateRange> spans = new ArrayList<>();
        for (EmploymentPeriod p : periods) {
            if (p.getJoinDate() != null && (p.getPersonType() == null || p.getPersonType() == PersonType.NHAN_VIEN)) {
                spans.add(new DateRange(p.getJoinDate(), p.getLastWorkingDay()));
            }
        }
        LocalDate join = e.getJoinDate();
        if (join != null && e.getPersonType() == PersonType.NHAN_VIEN) {
            spans.add(new DateRange(join, e.getLastWorkingDay()));
        }
        return spans;
    }
}

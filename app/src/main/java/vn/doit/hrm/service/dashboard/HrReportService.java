package vn.doit.hrm.service.dashboard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.report.EmploymentRecord;
import vn.doit.hr.report.HrReport;
import vn.doit.hr.report.ReportExcelWriter;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollLineRecord;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollLineRecordRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayrollVersionRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.payroll.PayrollAmounts;
import vn.doit.hrm.service.payroll.PayrollPeriodService;

/**
 * Báo cáo nhân sự Excel: biến động, nghỉ việc và (chỉ Quản trị, Kế toán) chi phí lương theo phiên bản bảng lương chính thức.
 * Chỉ tính nhân viên (NHAN_VIEN) có ngày vào làm; chi phí lương gồm mọi dòng của bảng lương.
 */
@Service
public class HrReportService {

    public static final int MAX_MONTHS = 36;
    static final String NO_DEPARTMENT = "Chưa có phòng ban";
    static final String NO_POSITION = "Chưa ghi vị trí";

    public enum GroupBy {
        DEPARTMENT("Phòng ban"),
        POSITION("Vị trí");

        private final String label;

        GroupBy(String label) {
            this.label = label;
        }
    }

    /** Một kỳ lương theo phiên bản chính thức. */
    public record OfficialPayroll(PayrollPeriod period, List<PayrollLineRecord> lines) {}

    private final EmployeeRepository employeeRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollVersionRepository versionRepository;
    private final PayrollLineRecordRepository lineRepository;
    private final PayrollPeriodService periodService;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties properties;

    public HrReportService(
        EmployeeRepository employeeRepository,
        PayrollPeriodRepository periodRepository,
        PayrollVersionRepository versionRepository,
        PayrollLineRecordRepository lineRepository,
        PayrollPeriodService periodService,
        ObjectMapper objectMapper,
        ApplicationProperties properties
    ) {
        this.employeeRepository = employeeRepository;
        this.periodRepository = periodRepository;
        this.versionRepository = versionRepository;
        this.lineRepository = lineRepository;
        this.periodService = periodService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** Quá trình làm việc của nhân viên; bỏ qua hồ sơ thiếu ngày vào làm hoặc có ngày nghỉ trước ngày vào. */
    @Transactional(readOnly = true)
    public List<EmploymentRecord> employmentRecords() {
        List<EmploymentRecord> out = new ArrayList<>();
        for (Employee e : employeeRepository.findByPersonType(PersonType.NHAN_VIEN)) {
            LocalDate left = e.getStatus() == EmploymentStatus.DA_NGHI
                ? Optional.ofNullable(e.getLastWorkingDay()).orElse(e.getTerminationDate())
                : null;
            if (e.getJoinDate() == null || (e.getStatus() == EmploymentStatus.DA_NGHI && left == null) || (left != null && left.isBefore(e.getJoinDate()))) {
                continue;
            }
            out.add(
                new EmploymentRecord(
                    e.getCode(),
                    e.getFullName(),
                    e.getDepartment() == null ? NO_DEPARTMENT : e.getDepartment().getName(),
                    e.getPosition(),
                    e.getJoinDate(),
                    left,
                    left == null || e.getTerminationType() == null ? null : EmploymentRecord.ExitType.valueOf(e.getTerminationType().name()),
                    e.getTerminationReason()
                )
            );
        }
        return out;
    }

    /** Kỳ lương mới nhất có phiên bản. */
    @Transactional(readOnly = true)
    public Optional<OfficialPayroll> latestPayroll() {
        return periodRepository.findAllByOrderByPeriodDesc().stream().map(this::official).flatMap(Optional::stream).findFirst();
    }

    @Transactional(readOnly = true)
    public byte[] excel(YearMonth from, YearMonth to, GroupBy groupBy, boolean includePayrollCost) {
        if (from.isAfter(to) || ChronoUnit.MONTHS.between(from, to) + 1 > MAX_MONTHS) {
            throw new EmployeeRuleException("Khoảng báo cáo không hợp lệ: từ tháng phải trước đến tháng, tối đa " + MAX_MONTHS + " tháng.", "reportrangeinvalid");
        }
        SortedMap<YearMonth, List<PayrollLine>> payrolls = new TreeMap<>();
        Map<String, String> departmentByCode = new HashMap<>();
        if (includePayrollCost) {
            for (PayrollPeriod p : periodRepository.findAllByOrderByPeriodDesc()) {
                YearMonth month = YearMonth.parse(p.getPeriod());
                if (month.isBefore(from) || month.isAfter(to)) {
                    continue;
                }
                official(p).ifPresent(o -> {
                    List<PayrollLine> lines = new ArrayList<>();
                    for (PayrollLineRecord l : o.lines()) {
                        Employee e = l.getEmployee();
                        departmentByCode.put(e.getCode(), e.getDepartment() == null ? NO_DEPARTMENT : e.getDepartment().getName());
                        lines.add(new PayrollLine(l.getExcelRow(), e.getCode(), l.getPosition(), e.getFullName(), null, PayrollAmounts.parse(objectMapper, l.getAmounts())));
                    }
                    payrolls.put(month, lines);
                });
            }
        }
        Function<PayrollLine, String> group = groupBy == GroupBy.POSITION
            ? l -> l.position() == null || l.position().isBlank() ? NO_POSITION : l.position().trim()
            : l -> departmentByCode.getOrDefault(l.employeeCode(), NO_DEPARTMENT);
        HrReport report = HrReport.build(properties.getCompany().getName(), from, to, employmentRecords(), payrolls, group, groupBy.label);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new ReportExcelWriter().write(report, out, includePayrollCost);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private Optional<OfficialPayroll> official(PayrollPeriod p) {
        Integer version = periodService.officialVersion(p);
        if (version == null) {
            return Optional.empty();
        }
        return versionRepository
            .findByPeriodIdAndVersion(p.getId(), version)
            .map(v -> new OfficialPayroll(p, lineRepository.findByVersionIdOrderByExcelRowAsc(v.getId())));
    }
}

package vn.doit.hrm.service.dashboard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.report.HeadcountReport;
import vn.doit.hrm.domain.PayrollLineRecord;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.dashboard.dto.DashboardDTO;

/** Số liệu trang Tổng quan: quân số, thử việc, HĐ sắp hết hạn, biến động 12 tháng, chi phí lương kỳ gần nhất. */
@Service
public class DashboardService {

    static final int EXPIRING_DAYS = 30;
    static final int MOVEMENT_MONTHS = 12;

    private final Clock clock;
    private final EmployeeRepository employeeRepository;
    private final EmploymentContractRepository contractRepository;
    private final HrReportService reportService;

    public DashboardService(
        Clock clock,
        EmployeeRepository employeeRepository,
        EmploymentContractRepository contractRepository,
        HrReportService reportService
    ) {
        this.clock = clock;
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.reportService = reportService;
    }

    @Transactional(readOnly = true)
    public DashboardDTO dashboard(boolean includePayrollCost) {
        LocalDate today = LocalDate.now(clock);
        YearMonth to = YearMonth.from(today);
        DashboardDTO.PayrollCost cost = includePayrollCost
            ? reportService
                .latestPayroll()
                .map(o ->
                    new DashboardDTO.PayrollCost(
                        o.period().getPeriod(),
                        o.period().getStatus(),
                        o.lines().stream().map(PayrollLineRecord::getTongChiPhi).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                    )
                )
                .orElse(null)
            : null;
        return new DashboardDTO(
            employeeRepository.countByPersonTypeAndStatus(PersonType.NHAN_VIEN, EmploymentStatus.DANG_LAM_VIEC),
            contractRepository.countOnProbation(today),
            contractRepository.countExpiringBetween(today, today.plusDays(EXPIRING_DAYS)),
            cost,
            HeadcountReport.monthly(reportService.employmentRecords(), to.minusMonths(MOVEMENT_MONTHS - 1L), to)
                .stream()
                .map(m -> new DashboardDTO.Movement(m.month().toString(), m.hires(), m.leavers(), m.closing()))
                .toList()
        );
    }
}

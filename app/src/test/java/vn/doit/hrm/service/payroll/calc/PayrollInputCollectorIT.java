package vn.doit.hrm.service.payroll.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hr.payroll.calc.CalcInput;
import vn.doit.hr.payroll.calc.CalcInput.PersonKind;
import vn.doit.hr.payroll.calc.CalcSettings.DayKind;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.AttendanceSnapshot;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.InsuranceExemption;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.AllowanceRepository;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendanceSnapshotRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.leave.LeaveLedgerService;
import vn.doit.hrm.service.payroll.calc.PayrollInputCollector.CollectedRow;

/**
 * Gom đầu vào tính lương của kỳ. Tháng thử 03/2026: 22 ngày làm việc (thứ Hai–thứ Sáu, không có ngày lễ), ngày 16/03 là thứ Hai,
 * ngày 20/03 là thứ Sáu.
 */
@IntegrationTest
@Transactional
class PayrollInputCollectorIT {

    private static final YearMonth P = YearMonth.of(2026, 3);

    @Autowired
    private PayrollInputCollector collector;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private AttendanceMonthRepository months;

    @Autowired
    private AttendanceSnapshotRepository snapshots;

    @Autowired
    private SalaryChangeRepository salaries;

    @Autowired
    private AllowanceRepository allowances;

    @Autowired
    private OvertimeRequestRepository overtime;

    @Autowired
    private LeaveLedgerService ledger;

    private void month(AttendanceMonthStatus status) {
        AttendanceMonth m = new AttendanceMonth();
        m.setMonth(P.toString());
        m.setStatus(status);
        months.saveAndFlush(m);
    }

    private Employee person(String code, LocalDate join) {
        return LeaveFixtures.employee(employees, code, PersonType.NHAN_VIEN, join);
    }

    private void snapshot(Employee e, String work, String paidLeave, String holiday, String trip, String sick) {
        AttendanceSnapshot s = new AttendanceSnapshot();
        s.setMonth(P.toString());
        s.setEmployeeId(e.getId());
        s.setStandardDays(new BigDecimal("22"));
        s.setWorkDays(new BigDecimal(work));
        s.setPaidLeaveDays(new BigDecimal(paidLeave));
        s.setHolidayDays(new BigDecimal(holiday));
        s.setTripDays(new BigDecimal(trip));
        s.setSickDays(new BigDecimal(sick));
        snapshots.saveAndFlush(s);
    }

    private void salary(Employee e, String from, long amount, SalaryBasis basis) {
        salaries.saveAndFlush(new SalaryChange().employee(e).effectiveDate(LocalDate.parse(from)).amount(amount).basis(basis));
    }

    private void overtimeRequest(Employee e, String day, String hours, int rate, OvertimeStatus status, OvertimeCompensation comp) {
        OvertimeRequest r = new OvertimeRequest();
        r.setEmployee(e);
        r.setWorkDate(LocalDate.parse(day));
        r.setStartTime(LocalTime.of(18, 0));
        r.setEndTime(LocalTime.of(21, 0));
        r.setPlannedHours(new BigDecimal(hours));
        r.setActualHours(new BigDecimal(hours));
        r.setRate(rate);
        r.setStatus(status);
        r.setCompensation(comp);
        r.setApproverKind(ApproverKind.QUAN_TRI);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        overtime.saveAndFlush(r);
    }

    private CollectedRow rowOf(Employee e) {
        return collector
            .collect(P)
            .stream()
            .filter(r -> r.employeeId().equals(e.getId()))
            .findFirst()
            .orElse(null);
    }

    @Test
    void failsWhenMonthIsMissingOrNotClosed() {
        assertThatThrownBy(() -> collector.collect(P)).isInstanceOfSatisfying(EmployeeRuleException.class, ex -> {
            assertThat(ex.getErrorKey()).isEqualTo("payrollattendancenotclosed");
            assertThat(ex.getMessage()).isEqualTo("Hãy chốt bảng công tháng 03/2026 trước khi tính lương.");
        });
        month(AttendanceMonthStatus.DANG_MO);
        assertThatThrownBy(() -> collector.collect(P)).isInstanceOfSatisfying(EmployeeRuleException.class, ex ->
            assertThat(ex.getErrorKey()).isEqualTo("payrollattendancenotclosed")
        );
    }

    @Test
    void collectsAFullEmployee() {
        month(AttendanceMonthStatus.DA_CHOT);
        Employee e = person("NV9801", LocalDate.of(2024, 1, 2));
        e.setDependants(2);
        e.setPitCommitment08(true);
        e.setSocialInsuranceSalary(7_000_000L);
        e = employees.saveAndFlush(e);
        snapshot(e, "18", "2", "1", "0.5", "0.5");
        salary(e, "2025-01-01", 20_000_000L, SalaryBasis.GROSS);
        salary(e, "2026-06-01", 25_000_000L, SalaryBasis.GROSS); // chưa hiệu lực trong kỳ
        allowances.saveAndFlush(
            new Allowance()
                .employee(e)
                .name("PC xăng")
                .amountPerMonth(500_000L)
                .fromDate(LocalDate.of(2026, 1, 1))
        );
        allowances.saveAndFlush(
            new Allowance()
                .employee(e)
                .name("PC cũ")
                .amountPerMonth(100_000L)
                .fromDate(LocalDate.of(2025, 1, 1))
                .toDate(LocalDate.of(2026, 2, 28))
        );
        allowances.saveAndFlush(
            new Allowance()
                .employee(e)
                .name("PC sau")
                .amountPerMonth(200_000L)
                .fromDate(LocalDate.of(2026, 4, 1))
        );
        overtimeRequest(e, "2026-03-10", "2", 150, OvertimeStatus.HOAN_TAT, OvertimeCompensation.TRA_TIEN);
        overtimeRequest(e, "2026-03-14", "4", 200, OvertimeStatus.HOAN_TAT, OvertimeCompensation.TRA_TIEN);
        overtimeRequest(e, "2026-03-11", "3", 150, OvertimeStatus.HOAN_TAT, OvertimeCompensation.NGHI_BU);
        overtimeRequest(e, "2026-03-12", "3", 150, OvertimeStatus.DA_DUYET, OvertimeCompensation.TRA_TIEN);
        overtimeRequest(e, "2026-04-01", "3", 150, OvertimeStatus.HOAN_TAT, OvertimeCompensation.TRA_TIEN);

        CollectedRow row = rowOf(e);
        assertThat(row).isNotNull();
        assertThat(row.code()).isEqualTo("NV9801");
        assertThat(row.errors()).isEmpty();
        CalcInput in = row.input();
        assertThat(in.kind()).isEqualTo(PersonKind.HDLD);
        assertThat(in.insured()).isTrue();
        assertThat(in.dependants()).isEqualTo(2);
        assertThat(in.pitCommitment08()).isTrue();
        assertThat(in.insuranceSalaryOverride()).isEqualByComparingTo("7000000");
        assertThat(in.standardDays()).isEqualByComparingTo("22");
        assertThat(in.days().get(DayKind.WORK)).isEqualByComparingTo("18");
        assertThat(in.days().get(DayKind.PAID_LEAVE)).isEqualByComparingTo("2");
        assertThat(in.days().get(DayKind.HOLIDAY)).isEqualByComparingTo("1");
        assertThat(in.days().get(DayKind.TRIP)).isEqualByComparingTo("0.5");
        assertThat(in.days().get(DayKind.SICK)).isEqualByComparingTo("0.5");
        assertThat(in.salary()).hasSize(1);
        assertThat(in.salary().get(0).baseSalary()).isEqualByComparingTo("20000000");
        assertThat(in.salary().get(0).insuredAllowance()).isEqualByComparingTo("0");
        assertThat(in.salary().get(0).net()).isFalse();
        assertThat(in.salary().get(0).workingDayShare()).isEqualByComparingTo("1");
        assertThat(in.allowances()).hasSize(1);
        assertThat(in.allowances().get(0).name()).isEqualTo("PC xăng");
        assertThat(in.allowances().get(0).amountPerMonth()).isEqualByComparingTo("500000");
        assertThat(in.overtime()).hasSize(2);
        assertThat(
            in
                .overtime()
                .stream()
                .map(o -> o.ratePercent() + ":" + o.hours().stripTrailingZeros().toPlainString())
                .sorted()
        ).containsExactly("150:2", "200:4");
        assertThat(in.unusedLeaveDays()).isNull();
    }

    @Test
    void exemptionMakesTheEmployeeNotInsuredAndNetSalaryIsMarked() {
        month(AttendanceMonthStatus.DA_CHOT);
        Employee e = person("NV9802", LocalDate.of(2024, 1, 2));
        e.setInsuranceExemption(InsuranceExemption.LUONG_HUU);
        e = employees.saveAndFlush(e);
        snapshot(e, "22", "0", "0", "0", "0");
        salary(e, "2025-01-01", 15_000_000L, SalaryBasis.NET);

        CalcInput in = rowOf(e).input();
        assertThat(in.insured()).isFalse();
        assertThat(in.salary().get(0).net()).isTrue();
        assertThat(in.salary().get(0).baseSalary()).isEqualByComparingTo("15000000");
    }

    @Test
    void salaryChangeOnThe16thSplitsByWorkingDays() {
        month(AttendanceMonthStatus.DA_CHOT);
        Employee e = person("NV9803", LocalDate.of(2024, 1, 2));
        snapshot(e, "22", "0", "0", "0", "0");
        salary(e, "2025-01-01", 10_000_000L, SalaryBasis.GROSS);
        salary(e, "2026-03-16", 12_000_000L, SalaryBasis.GROSS);

        List<CalcInput.SalarySegment> seg = rowOf(e).input().salary();
        assertThat(seg).hasSize(2);
        assertThat(seg.get(0).baseSalary()).isEqualByComparingTo("10000000");
        assertThat(seg.get(1).baseSalary()).isEqualByComparingTo("12000000");
        assertThat(seg.get(0).workingDayShare()).isEqualByComparingTo(
            new BigDecimal("10").divide(new BigDecimal("22"), 10, java.math.RoundingMode.HALF_UP)
        );
        assertThat(seg.get(0).workingDayShare().add(seg.get(1).workingDayShare())).isEqualByComparingTo("1");
    }

    @Test
    void terminationOnThe20thIsInTheListWithUnusedLeaveAndShareUpToThatDay() {
        month(AttendanceMonthStatus.DA_CHOT);
        Employee e = person("NV9804", LocalDate.of(2024, 1, 2));
        e.setStatus(EmploymentStatus.DA_NGHI);
        e.setLastWorkingDay(LocalDate.of(2026, 3, 20));
        e = employees.saveAndFlush(e);
        snapshot(e, "15", "0", "0", "0", "0");
        salary(e, "2025-01-01", 10_000_000L, SalaryBasis.GROSS);
        salary(e, "2026-03-16", 12_000_000L, SalaryBasis.GROSS);
        ledger.post(
            e.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal("5"))),
            LedgerReason.DIEU_CHINH,
            null,
            null,
            "thử",
            LocalDate.of(2026, 3, 1)
        );
        // Dòng chốt khi nghỉ việc đưa số dư về 0: số phép chưa dùng vẫn là 5.
        ledger.post(
            e.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal("-5"))),
            LedgerReason.NGHI_VIEC,
            "TERMINATION",
            1L,
            "chốt",
            LocalDate.of(2026, 3, 20)
        );

        CollectedRow row = rowOf(e);
        assertThat(row).isNotNull();
        assertThat(row.input().unusedLeaveDays()).isEqualByComparingTo("5");
        List<CalcInput.SalarySegment> seg = row.input().salary();
        // 02–13/03 = 10 ngày, 16–20/03 = 5 ngày: tổng 15 ngày làm việc đến ngày nghỉ.
        assertThat(seg).hasSize(2);
        assertThat(seg.get(0).workingDayShare()).isEqualByComparingTo(
            new BigDecimal("10").divide(new BigDecimal("15"), 10, java.math.RoundingMode.HALF_UP)
        );
        assertThat(seg.get(0).workingDayShare().add(seg.get(1).workingDayShare())).isEqualByComparingTo("1");
    }

    @Test
    void noSalaryIsARowError() {
        month(AttendanceMonthStatus.DA_CHOT);
        Employee e = person("NV9805", LocalDate.of(2024, 1, 2));
        snapshot(e, "22", "0", "0", "0", "0");
        salary(e, "2026-04-01", 10_000_000L, SalaryBasis.GROSS);

        CollectedRow row = rowOf(e);
        assertThat(row.errors()).containsExactly("Chưa có mức lương");
    }

    @Test
    void peopleOutsideTheMonthAreNotCollected() {
        month(AttendanceMonthStatus.DA_CHOT);
        Employee later = person("NV9806", LocalDate.of(2026, 4, 1));
        snapshot(later, "0", "0", "0", "0", "0");
        salary(later, "2026-04-01", 10_000_000L, SalaryBasis.GROSS);
        Employee gone = person("NV9807", LocalDate.of(2024, 1, 2));
        gone.setStatus(EmploymentStatus.DA_NGHI);
        gone.setLastWorkingDay(LocalDate.of(2026, 2, 27));
        gone = employees.saveAndFlush(gone);
        snapshot(gone, "0", "0", "0", "0", "0");
        salary(gone, "2025-01-01", 10_000_000L, SalaryBasis.GROSS);
        Employee noSnapshot = person("NV9808", LocalDate.of(2024, 1, 2));
        salary(noSnapshot, "2025-01-01", 10_000_000L, SalaryBasis.GROSS);

        assertThat(rowOf(later)).isNull();
        assertThat(rowOf(gone)).isNull();
        assertThat(rowOf(noSnapshot)).isNull();
    }
}

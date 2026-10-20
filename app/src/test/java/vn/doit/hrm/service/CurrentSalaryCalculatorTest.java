package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.SalaryBasis;

class CurrentSalaryCalculatorTest {

    private static EmploymentContract contract(ContractType type, LocalDate start, Long salary, ContractStatus status) {
        return new EmploymentContract().type(type).startDate(start).salary(salary).salaryBasis(SalaryBasis.GROSS).status(status);
    }

    private static SalaryChange change(LocalDate date, long amount) {
        return new SalaryChange().effectiveDate(date).amount(amount).basis(SalaryBasis.GROSS);
    }

    private final List<EmploymentContract> contracts = List.of(
        contract(ContractType.HDTV, LocalDate.of(2026, 1, 1), 10_000_000L, ContractStatus.DA_KET_THUC),
        contract(ContractType.HDLD_XDTH, LocalDate.of(2026, 3, 1), 12_000_000L, ContractStatus.DA_KY)
    );
    private final List<SalaryChange> changes = List.of(change(LocalDate.of(2026, 9, 1), 14_000_000L));

    private Optional<CurrentSalary> at(LocalDate d) {
        return CurrentSalaryCalculator.of(EmploymentStatus.DANG_LAM_VIEC, contracts, changes, d);
    }

    @Test
    void contractWinsBeforeChange() {
        CurrentSalary s = at(LocalDate.of(2026, 8, 15)).orElseThrow();
        assertThat(s.amount()).isEqualTo(12_000_000L);
        assertThat(s.source()).isEqualTo("CONTRACT");
    }

    @Test
    void changeWinsOnItsDate() {
        CurrentSalary s = at(LocalDate.of(2026, 9, 1)).orElseThrow();
        assertThat(s.amount()).isEqualTo(14_000_000L);
        assertThat(s.source()).isEqualTo("SALARY_CHANGE");
    }

    @Test
    void futureChangeIgnored() {
        List<SalaryChange> withFuture = List.of(change(LocalDate.of(2026, 9, 1), 14_000_000L), change(LocalDate.of(2026, 12, 1), 20_000_000L));
        CurrentSalary s = CurrentSalaryCalculator.of(EmploymentStatus.DANG_LAM_VIEC, contracts, withFuture, LocalDate.of(2026, 10, 19)).orElseThrow();
        assertThat(s.amount()).isEqualTo(14_000_000L);
    }

    @Test
    void sameDayChangeBeatsContract() {
        List<SalaryChange> sameDay = List.of(change(LocalDate.of(2026, 3, 1), 13_000_000L));
        CurrentSalary s = CurrentSalaryCalculator.of(EmploymentStatus.DANG_LAM_VIEC, contracts, sameDay, LocalDate.of(2026, 4, 1)).orElseThrow();
        assertThat(s.amount()).isEqualTo(13_000_000L);
        assertThat(s.source()).isEqualTo("SALARY_CHANGE");
    }

    @Test
    void terminatedHasNoSalary() {
        assertThat(CurrentSalaryCalculator.of(EmploymentStatus.DA_NGHI, contracts, changes, LocalDate.of(2026, 10, 19))).isEmpty();
    }

    @Test
    void draftContractIgnored() {
        List<EmploymentContract> draft = List.of(contract(ContractType.HDLD_XDTH, LocalDate.of(2026, 3, 1), 12_000_000L, ContractStatus.NHAP));
        assertThat(CurrentSalaryCalculator.of(EmploymentStatus.DANG_LAM_VIEC, draft, List.of(), LocalDate.of(2026, 10, 19))).isEmpty();
    }
}

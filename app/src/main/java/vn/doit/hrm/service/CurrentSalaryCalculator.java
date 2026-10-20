package vn.doit.hrm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.SalaryBasis;

/** Tính lương hiện tại: mức có ngày hiệu lực ≤ asOf mới nhất; trùng ngày thì điều chỉnh lương thắng. */
public final class CurrentSalaryCalculator {

    private CurrentSalaryCalculator() {}

    public static Optional<CurrentSalary> of(
        EmploymentStatus status,
        List<EmploymentContract> contracts,
        List<SalaryChange> changes,
        LocalDate asOf
    ) {
        if (status == EmploymentStatus.DA_NGHI) {
            return Optional.empty();
        }
        List<CurrentSalary> candidates = new ArrayList<>();
        for (EmploymentContract c : contracts) {
            if (c.getSalary() == null || c.getStartDate() == null || c.getStatus() == ContractStatus.NHAP || c.getStartDate().isAfter(asOf)) {
                continue;
            }
            candidates.add(build(c.getSalary(), c.getSalaryBasis(), c.getGrossAmount(), c.getStartDate(), "CONTRACT"));
        }
        for (SalaryChange s : changes) {
            if (s.getAmount() == null || s.getEffectiveDate() == null || s.getEffectiveDate().isAfter(asOf)) {
                continue;
            }
            candidates.add(build(s.getAmount(), s.getBasis(), s.getGrossAmount(), s.getEffectiveDate(), "SALARY_CHANGE"));
        }
        return candidates
            .stream()
            .max(Comparator.comparing(CurrentSalary::effectiveDate).thenComparing(x -> "SALARY_CHANGE".equals(x.source()) ? 1 : 0));
    }

    private static CurrentSalary build(long amount, SalaryBasis basis, Long gross, LocalDate date, String source) {
        SalaryBasis b = basis == null ? SalaryBasis.GROSS : basis;
        return new CurrentSalary(amount, b, b == SalaryBasis.GROSS ? Long.valueOf(amount) : gross, date, source);
    }
}

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
        List<CurrentSalary> t = timeline(contracts, changes, asOf);
        return t.isEmpty() ? Optional.empty() : Optional.of(t.get(t.size() - 1));
    }

    /**
     * Các mức lương có hiệu lực đến {@code asOf}, theo ngày hiệu lực tăng dần, mỗi ngày một mức (trùng ngày: điều chỉnh lương thắng
     * hợp đồng). Mức cuối là mức hiện hành.
     */
    public static List<CurrentSalary> timeline(List<EmploymentContract> contracts, List<SalaryChange> changes, LocalDate asOf) {
        List<CurrentSalary> candidates = new ArrayList<>();
        for (EmploymentContract c : contracts) {
            if (
                c.getSalary() == null || c.getStartDate() == null || c.getStatus() == ContractStatus.NHAP || c.getStartDate().isAfter(asOf)
            ) {
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
        candidates.sort(Comparator.comparing(CurrentSalary::effectiveDate).thenComparing(CurrentSalaryCalculator::rank));
        List<CurrentSalary> out = new ArrayList<>();
        for (CurrentSalary c : candidates) {
            CurrentSalary last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last == null || !last.effectiveDate().equals(c.effectiveDate())) {
                out.add(c);
            } else if (rank(c) > rank(last)) {
                // Trùng ngày: điều chỉnh lương thắng hợp đồng; cùng loại thì giữ mức đứng trước (như Stream.max trước đây).
                out.set(out.size() - 1, c);
            }
        }
        return out;
    }

    private static int rank(CurrentSalary s) {
        return "SALARY_CHANGE".equals(s.source()) ? 1 : 0;
    }

    private static CurrentSalary build(long amount, SalaryBasis basis, Long gross, LocalDate date, String source) {
        SalaryBasis b = basis == null ? SalaryBasis.GROSS : basis;
        return new CurrentSalary(amount, b, b == SalaryBasis.GROSS ? Long.valueOf(amount) : gross, date, source);
    }
}

package vn.doit.hrm.service;

import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.SalaryBasis;

/** Mức lương đang áp dụng; source là "CONTRACT" hoặc "SALARY_CHANGE". */
public record CurrentSalary(long amount, SalaryBasis basis, Long grossAmount, LocalDate effectiveDate, String source) {}

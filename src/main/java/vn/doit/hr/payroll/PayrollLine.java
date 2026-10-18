package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Một dòng nhân viên trong bảng lương. Ô trống được hiểu là 0. */
public record PayrollLine(
        int excelRow,
        String employeeCode,
        String position,
        String fullName,
        LocalDate contractStartDate,
        Map<PayrollField, BigDecimal> amounts) {

    public PayrollLine {
        amounts = Collections.unmodifiableMap(new EnumMap<>(amounts));
    }

    public BigDecimal get(PayrollField field) {
        return amounts.getOrDefault(field, BigDecimal.ZERO);
    }

    public boolean hasCode() {
        return employeeCode != null && !employeeCode.isBlank();
    }
}

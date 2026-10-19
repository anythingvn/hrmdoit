package vn.doit.hr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PayrollRulesTest {

    private final PayrollRules r = PayrollRules.forPeriod(YearMonth.of(2026, 6)).orElseThrow();

    private void tax(long income, long expected) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(r.monthlyPit(BigDecimal.valueOf(income))),
                "thuế của " + income + " = " + r.monthlyPit(BigDecimal.valueOf(income)));
    }

    @Test
    void progressiveFiveBrackets2026() {
        tax(0, 0);
        tax(1_101_500, 55_075);        // dòng 10 trong file
        tax(10_000_000, 500_000);
        tax(30_000_000, 2_500_000);     // 500k + 10% × 20tr
        tax(60_000_000, 8_500_000);     // + 20% × 30tr
        tax(100_000_000, 20_500_000);   // + 30% × 40tr
        tax(120_000_000, 27_500_000);   // + 35% × 20tr
    }

    @Test
    void insuranceCapChangesFromJuly2026() {
        assertEquals(0, new BigDecimal("46800000").compareTo(r.socialInsuranceCap()));
        assertEquals(0, new BigDecimal("50600000").compareTo(
                PayrollRules.forPeriod(YearMonth.of(2026, 7)).orElseThrow().socialInsuranceCap()));
        assertTrue(PayrollRules.forPeriod(YearMonth.of(2025, 12)).isEmpty());
    }
}

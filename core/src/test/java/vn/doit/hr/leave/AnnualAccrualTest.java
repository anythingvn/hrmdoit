package vn.doit.hr.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.doit.hr.leave.LeaveTestSupport.cal;
import static vn.doit.hr.leave.LeaveTestSupport.d;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AnnualAccrualTest {

    private static final LocalDate JOIN = LocalDate.of(2015, 3, 10);

    @Test
    void fiveYearStepAppliesFromMonthAfter() {
        assertEquals(12, AnnualAccrual.annualDays(12, JOIN, YearMonth.of(2020, 3)));
        assertEquals(13, AnnualAccrual.annualDays(12, JOIN, YearMonth.of(2020, 4)));
        assertEquals(14, AnnualAccrual.annualDays(12, JOIN, YearMonth.of(2025, 4)));
    }

    @Test
    void monthlyAmount() {
        YearMonth m = YearMonth.of(2026, 10);
        assertEquals(d("1.00"), AnnualAccrual.monthly(12, JOIN, null, m, cal()));
        assertEquals(d("1.08"), AnnualAccrual.monthly(13, JOIN, null, m, cal()));
    }

    @Test
    void lateJoinerUnderHalfMonthGetsZero() {
        assertEquals(0, d("0").compareTo(AnnualAccrual.monthly(12, LocalDate.of(2026, 10, 20), null, YearMonth.of(2026, 10), cal())));
    }

    @Test
    void midMonthJoinerWithEnoughDaysGetsFull() {
        assertEquals(d("1.00"), AnnualAccrual.monthly(12, LocalDate.of(2026, 10, 12), null, YearMonth.of(2026, 10), cal()));
    }

    @Test
    void earlyLeaverGetsZero() {
        assertEquals(0, d("0").compareTo(AnnualAccrual.monthly(12, JOIN, LocalDate.of(2026, 10, 9), YearMonth.of(2026, 10), cal())));
    }
}

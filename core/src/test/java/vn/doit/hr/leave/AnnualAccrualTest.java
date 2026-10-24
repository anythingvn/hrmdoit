package vn.doit.hr.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.doit.hr.leave.LeaveTestSupport.cal;
import static vn.doit.hr.leave.LeaveTestSupport.d;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnualAccrualTest {

    private static final LocalDate JOIN = LocalDate.of(2015, 3, 10);

    @Test
    void annualDaysAddsExtraPerFullCycle() {
        assertEquals(13, AnnualAccrual.annualDays(12, 5, 5, 1));
        assertEquals(13, AnnualAccrual.annualDays(12, 9, 5, 1));
        assertEquals(14, AnnualAccrual.annualDays(12, 10, 5, 1));
        assertEquals(12, AnnualAccrual.annualDays(12, 10, 5, 0));
        assertEquals(16, AnnualAccrual.annualDays(12, 6, 3, 2));
        assertEquals(12, AnnualAccrual.annualDays(12, 4, 5, 1));
    }

    @Test
    void seniorityYearsSumsSpansWithoutGaps() {
        List<DateRange> spans = List.of(
            new DateRange(LocalDate.of(2018, 1, 1), LocalDate.of(2020, 12, 31)),
            new DateRange(LocalDate.of(2023, 1, 1), null)
        );
        assertEquals(5, AnnualAccrual.seniorityYears(spans, LocalDate.of(2024, 12, 31)));
        assertEquals(4, AnnualAccrual.seniorityYears(spans, LocalDate.of(2024, 11, 30)));
    }

    @Test
    void seniorityYearsIgnoresTimeAfterAsOf() {
        List<DateRange> spans = List.of(new DateRange(LocalDate.of(2022, 6, 15), null));
        assertEquals(4, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 5, 31)));
        assertEquals(5, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 6, 30)));
        assertEquals(0, AnnualAccrual.seniorityYears(spans, LocalDate.of(2022, 6, 1)));
        assertEquals(0, AnnualAccrual.seniorityYears(List.of(), LocalDate.of(2022, 6, 1)));
    }

    @Test
    void firstOfMonthJoinReachesCycleOnLastDayBeforeAnniversary() {
        // Tính cả ngày đầu và ngày cuối: 01/07/2022–30/06/2027 là đủ 5 năm.
        List<DateRange> spans = List.of(new DateRange(LocalDate.of(2022, 7, 1), null));
        assertEquals(5, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 6, 30)));
        assertEquals(4, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 5, 31)));
    }

    @Test
    void midMonthJoinReachesCycleInAnniversaryMonth() {
        List<DateRange> spans = List.of(new DateRange(LocalDate.of(2022, 6, 15), null));
        assertEquals(4, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 5, 31)));
        assertEquals(5, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 6, 30)));
    }

    @Test
    void leapDaySpanIsPinned() {
        // 29/02/2020–28/02/2021 là 366 ngày tính cả hai đầu → đủ 1 năm; đến 27/02/2021 thì chưa.
        List<DateRange> spans = List.of(new DateRange(LocalDate.of(2020, 2, 29), null));
        assertEquals(1, AnnualAccrual.seniorityYears(spans, LocalDate.of(2021, 2, 28)));
        assertEquals(0, AnnualAccrual.seniorityYears(spans, LocalDate.of(2021, 2, 27)));
        // Một khoảng dài qua năm nhuận 2024: 01/03/2023–29/02/2028 đủ 5 năm, 28/02/2028 thì chưa.
        List<DateRange> across = List.of(new DateRange(LocalDate.of(2023, 3, 1), null));
        assertEquals(5, AnnualAccrual.seniorityYears(across, LocalDate.of(2028, 2, 29)));
        assertEquals(4, AnnualAccrual.seniorityYears(across, LocalDate.of(2028, 2, 28)));
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

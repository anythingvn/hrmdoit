package vn.doit.hr.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.doit.hr.leave.LeaveTestSupport.cal;
import static vn.doit.hr.leave.LeaveTestSupport.d;

import java.time.LocalDate;
import java.time.Period;
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
    void seniorityYearsClosedWholeYearsPlusRehireAddUp() {
        List<DateRange> spans = List.of(
            new DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2022, 12, 31)),
            new DateRange(LocalDate.of(2024, 1, 1), null)
        );
        // 3 năm (đã nghỉ) + lần làm hiện tại: lần sau tròn 2 năm vào 01/01/2026 (như một lần làm duy nhất:
        // Period.between(01/01/2024, 31/12/2025) mới 1 năm 11 tháng 30 ngày), nên tháng 01/2026 mới đủ 5 năm.
        assertEquals(4, AnnualAccrual.seniorityYears(spans, LocalDate.of(2025, 11, 30)));
        assertEquals(4, AnnualAccrual.seniorityYears(spans, LocalDate.of(2025, 12, 31)));
        assertEquals(5, AnnualAccrual.seniorityYears(spans, LocalDate.of(2026, 1, 1)));
        assertEquals(5, AnnualAccrual.seniorityYears(spans, LocalDate.of(2026, 1, 31)));
    }

    @Test
    void seniorityYearsTwoClosedWholeYearSpansAddUpExactly() {
        // 3 năm + 2 năm (đã nghỉ cả hai lần) = đủ 5 năm, không hụt một ngày.
        List<DateRange> spans = List.of(
            new DateRange(LocalDate.of(2018, 1, 1), LocalDate.of(2020, 12, 31)),
            new DateRange(LocalDate.of(2022, 1, 1), LocalDate.of(2023, 12, 31))
        );
        assertEquals(5, AnnualAccrual.seniorityYears(spans, LocalDate.of(2025, 6, 30)));
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
    void firstOfMonthJoinReachesCycleOnAnniversary() {
        // Ngày kỷ niệm: vào 01/07/2022 thì 01/07/2027 mới đủ 5 năm → tháng 06/2027 còn 4, tháng 07/2027 là 5.
        List<DateRange> spans = List.of(new DateRange(LocalDate.of(2022, 7, 1), null));
        assertEquals(4, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 6, 30)));
        assertEquals(5, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 7, 31)));
    }

    @Test
    void midMonthJoinReachesCycleInAnniversaryMonth() {
        List<DateRange> spans = List.of(new DateRange(LocalDate.of(2022, 6, 15), null));
        assertEquals(4, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 5, 31)));
        assertEquals(5, AnnualAccrual.seniorityYears(spans, LocalDate.of(2027, 6, 30)));
    }

    @Test
    void singleSpanEqualsPeriodBetweenJoinAndAsOf() {
        LocalDate join = LocalDate.of(2019, 3, 17);
        List<DateRange> spans = List.of(new DateRange(join, null));
        for (LocalDate asOf = join; asOf.isBefore(LocalDate.of(2030, 1, 1)); asOf = asOf.plusDays(1)) {
            assertEquals(Period.between(join, asOf).getYears(), AnnualAccrual.seniorityYears(spans, asOf), asOf.toString());
        }
    }

    @Test
    void leapDaySpanIsPinned() {
        // Vào 29/02/2020: 28/02/2021 chưa đủ 1 năm (Period 11 tháng 30 ngày), 01/03/2021 thì đủ.
        List<DateRange> spans = List.of(new DateRange(LocalDate.of(2020, 2, 29), null));
        assertEquals(0, AnnualAccrual.seniorityYears(spans, LocalDate.of(2021, 2, 28)));
        assertEquals(1, AnnualAccrual.seniorityYears(spans, LocalDate.of(2021, 3, 1)));
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

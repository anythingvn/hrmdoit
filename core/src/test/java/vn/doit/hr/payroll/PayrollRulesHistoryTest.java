package vn.doit.hr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Quy tắc theo ngày hiệu lực từ 07/2020; mỗi mốc có một phép tính Net từ Gross làm tay. */
class PayrollRulesHistoryTest {

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static PayrollRules rules(int year, int month) {
        return PayrollRules.forPeriod(YearMonth.of(year, month)).orElseThrow();
    }

    private static void net(int year, int month, long gross, int dependants, long expectedNet) {
        BigDecimal actual = NetGrossConverter.netFromGross(bd(gross), dependants, rules(year, month));
        assertEquals(0, actual.compareTo(bd(expectedNet)), month + "/" + year + ": net = " + actual);
    }

    private static void caps(int year, int month, long si, long ui, long personal, long dependant) {
        PayrollRules r = rules(year, month);
        assertEquals(0, r.socialInsuranceCap().compareTo(bd(si)), "trần BHXH " + month + "/" + year);
        assertEquals(0, r.unemploymentInsuranceCap().compareTo(bd(ui)), "trần BHTN " + month + "/" + year);
        assertEquals(0, r.personalDeduction().compareTo(bd(personal)));
        assertEquals(0, r.dependantDeduction().compareTo(bd(dependant)));
    }

    @Test
    void sevenBracketScheduleBefore2026() {
        PayrollRules r = rules(2025, 12);
        long[][] cases = {
            {0, 0}, {5_000_000, 250_000}, {10_000_000, 750_000}, {18_000_000, 1_950_000},
            {32_000_000, 4_750_000}, {52_000_000, 9_750_000}, {80_000_000, 18_150_000}, {100_000_000, 25_150_000},
        };
        for (long[] c : cases) {
            assertEquals(0, r.monthlyPit(bd(c[0])).compareTo(bd(c[1])), "thuế của " + c[0]);
        }
    }

    @Test
    void capsAndDeductionsAtEachMilestone() {
        caps(2020, 7, 29_800_000, 88_400_000, 11_000_000, 4_400_000);
        caps(2022, 6, 29_800_000, 88_400_000, 11_000_000, 4_400_000);
        caps(2022, 7, 29_800_000, 93_600_000, 11_000_000, 4_400_000);
        caps(2023, 6, 29_800_000, 93_600_000, 11_000_000, 4_400_000);
        caps(2023, 7, 36_000_000, 93_600_000, 11_000_000, 4_400_000);
        caps(2024, 6, 36_000_000, 93_600_000, 11_000_000, 4_400_000);
        caps(2024, 7, 46_800_000, 99_200_000, 11_000_000, 4_400_000);
        caps(2025, 12, 46_800_000, 99_200_000, 11_000_000, 4_400_000);
        caps(2026, 1, 46_800_000, 106_200_000, 15_500_000, 6_200_000);
        caps(2026, 6, 46_800_000, 106_200_000, 15_500_000, 6_200_000);
        caps(2026, 7, 50_600_000, 106_200_000, 15_500_000, 6_200_000);
    }

    @Test
    void july2020() {
        // BH 29,8tr×9,5% + 30tr×1% = 3.131.000; TNTT 15.869.000; thuế 250k + 500k + 5.869.000×15% = 1.630.350
        net(2020, 7, 30_000_000, 0, 25_238_650);
    }

    @Test
    void july2022() {
        // BH 2.831.000 + 93,6tr×1% = 3.767.000; TNTT 85.233.000; thuế 18.150.000 + 5.233.000×35% = 19.981.550
        net(2022, 7, 100_000_000, 0, 76_251_450);
        // 06/2022 trần BHTN còn 88,4tr: BH 3.715.000; TNTT 85.285.000; thuế 19.999.750
        net(2022, 6, 100_000_000, 0, 76_285_250);
    }

    @Test
    void july2023() {
        // BH 36tr×9,5% + 400k = 3.820.000; TNTT 40tr − 3,82tr − 11tr − 4,4tr = 20.780.000; thuế 1.950.000 + 556.000
        net(2023, 7, 40_000_000, 1, 33_674_000);
    }

    @Test
    void july2024() {
        // BH 46,8tr×9,5% + 500k = 4.946.000; TNTT 25.254.000; thuế 1.950.000 + 7.254.000×20% = 3.400.800
        net(2024, 7, 50_000_000, 2, 41_653_200);
    }

    @Test
    void january2026() {
        // BH 4.446.000 + 106,2tr×1% = 5.508.000; TNTT 88.992.000; thuế 8.500.000 + 28.992.000×30% = 17.197.600
        net(2026, 1, 110_000_000, 0, 87_294_400);
    }

    @Test
    void july2026() {
        // BH 50,6tr×9,5% + 1.062.000 = 5.869.000; TNTT 88.631.000; thuế 8.500.000 + 28.631.000×30% = 17.089.300
        net(2026, 7, 110_000_000, 0, 87_041_700);
    }

    @Test
    void beforeJuly2020HasNoRules() {
        assertTrue(PayrollRules.forPeriod(YearMonth.of(2019, 12)).isEmpty());
        assertTrue(PayrollRules.forPeriod(YearMonth.of(2020, 6)).isEmpty());
        assertTrue(PayrollRules.forPeriodOrLatest(YearMonth.of(2019, 12)).isEmpty());
        assertTrue(PayrollRules.forPeriodOrLatest(YearMonth.of(2020, 6)).isEmpty());
    }

    @Test
    void futurePeriodUsesLatestRulesAsEstimate() {
        assertTrue(PayrollRules.forPeriod(YearMonth.of(2027, 1)).isEmpty(), "chưa có luật cho 2027 → forPeriod rỗng");
        PayrollRules.RulesLookup lookup = PayrollRules.forPeriodOrLatest(YearMonth.of(2027, 1)).orElseThrow();
        assertTrue(lookup.estimated());
        assertEquals(YearMonth.of(2026, 7), lookup.effectiveFrom());
        assertEquals(rules(2026, 7), lookup.rules());
    }

    @Test
    void coveredPeriodIsNotEstimated() {
        PayrollRules.RulesLookup lookup = PayrollRules.forPeriodOrLatest(YearMonth.of(2026, 12)).orElseThrow();
        assertFalse(lookup.estimated());
        assertEquals(YearMonth.of(2026, 7), lookup.effectiveFrom());
        PayrollRules.RulesLookup old = PayrollRules.forPeriodOrLatest(YearMonth.of(2023, 9)).orElseThrow();
        assertFalse(old.estimated());
        assertEquals(YearMonth.of(2023, 7), old.effectiveFrom());
        assertEquals(rules(2023, 9), old.rules());
    }

    @Test
    void suggestGrossEstimateFlagsFuturePeriods() {
        NetGrossConverter.GrossSuggestion future =
                NetGrossConverter.suggestGrossOrEstimate(bd(26_215_000), 0, YearMonth.of(2027, 1)).orElseThrow();
        assertEquals(0, future.gross().compareTo(bd(30_000_000)));
        assertTrue(future.estimated());
        assertEquals(YearMonth.of(2026, 7), future.rulesEffectiveFrom());

        NetGrossConverter.GrossSuggestion past =
                NetGrossConverter.suggestGrossOrEstimate(bd(25_238_650), 0, YearMonth.of(2020, 7)).orElseThrow();
        assertEquals(0, past.gross().compareTo(bd(30_000_000)));
        assertFalse(past.estimated());

        assertTrue(NetGrossConverter.suggestGrossOrEstimate(bd(10_000_000), 0, YearMonth.of(2020, 6)).isEmpty());
        // Hàm cũ giữ nguyên: chỉ trả khi có quy tắc chính thức cho kỳ
        assertTrue(NetGrossConverter.suggestGross(bd(10_000_000), 0, YearMonth.of(2027, 1)).isEmpty());
        assertTrue(NetGrossConverter.suggestGross(bd(25_238_650), 0, YearMonth.of(2020, 7)).isPresent());
    }
}

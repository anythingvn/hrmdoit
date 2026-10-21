package vn.doit.hr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.doit.hr.payroll.PayrollField.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleBookTest {

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static PayrollRules rules2026(long personal) {
        PayrollRules r = RuleBook.defaults().forPeriod(YearMonth.of(2026, 6)).orElseThrow();
        return new PayrollRules(bd(personal), r.dependantDeduction(), r.socialInsuranceCap(), r.unemploymentInsuranceCap(), r.pitBrackets());
    }

    /** Bộ mặc định + một mốc {@code from} với giảm trừ bản thân {@code personal}, đã biết luật đến {@code knownThrough}. */
    private static RuleBook withMilestone(YearMonth from, long personal, YearMonth knownThrough) {
        List<RuleBook.Milestone> ms = new ArrayList<>(RuleBook.defaults().milestones());
        ms.add(new RuleBook.Milestone(from, rules2026(personal)));
        return new RuleBook(ms, knownThrough);
    }

    @Test
    void defaultsMatchCurrentLaw() {
        RuleBook d = RuleBook.defaults();
        PayrollRules jun2026 = d.forPeriod(YearMonth.of(2026, 6)).orElseThrow();
        assertEquals(0, bd(15_500_000).compareTo(jun2026.personalDeduction()));
        assertEquals(5, jun2026.pitBrackets().size());
        assertEquals(0, bd(11_000_000).compareTo(d.forPeriod(YearMonth.of(2025, 8)).orElseThrow().personalDeduction()));
        assertTrue(d.forPeriod(YearMonth.of(2027, 1)).isEmpty());
        assertTrue(d.forPeriodOrLatest(YearMonth.of(2027, 1)).orElseThrow().estimated());
        assertEquals(YearMonth.of(2026, 12), d.knownThrough());
        assertEquals(6, d.milestones().size());
    }

    @Test
    void staticLookupsDelegateToDefaults() {
        assertEquals(RuleBook.defaults().forPeriod(YearMonth.of(2024, 9)), PayrollRules.forPeriod(YearMonth.of(2024, 9)));
    }

    @Test
    void laterSameMonthWins() {
        RuleBook book = withMilestone(YearMonth.of(2026, 1), 16_000_000, YearMonth.of(2026, 12));
        assertEquals(0, bd(16_000_000).compareTo(book.forPeriod(YearMonth.of(2026, 3)).orElseThrow().personalDeduction()));
        // Mốc 07/2026 có sau vẫn áp dụng từ tháng 7.
        assertEquals(0, bd(15_500_000).compareTo(book.forPeriod(YearMonth.of(2026, 8)).orElseThrow().personalDeduction()));
    }

    @Test
    void validatorUsesInjectedBook() {
        RuleBook book = withMilestone(YearMonth.of(2027, 1), 20_000_000, YearMonth.of(2027, 12));
        ParsedPayroll p = oneLine(YearMonth.of(2027, 2));

        List<Issue> injected = new PayrollValidator(book).validate(p, YearMonth.of(2027, 2), c -> Optional.of("Nhân viên X"));
        assertTrue(
            injected.stream().anyMatch(i -> i.field() == GIAM_TRU_BAN_THAN && i.message().contains("20.000.000")),
            injected::toString
        );

        List<Issue> defaults = new PayrollValidator().validate(p, YearMonth.of(2027, 2), c -> Optional.of("Nhân viên X"));
        assertTrue(defaults.stream().anyMatch(i -> i.message().contains("Chưa khai báo mức luật định")), defaults::toString);
    }

    @Test
    void converterUsesInjectedBook() {
        RuleBook book = withMilestone(YearMonth.of(2027, 1), 20_000_000, YearMonth.of(2027, 12));
        BigDecimal net = bd(20_000_000);
        BigDecimal g2027 = NetGrossConverter.suggestGross(net, 0, YearMonth.of(2027, 2), book).orElseThrow();
        BigDecimal g2026 = NetGrossConverter.suggestGross(net, 0, YearMonth.of(2026, 6), book).orElseThrow();
        assertTrue(g2027.compareTo(g2026) < 0, g2027 + " < " + g2026);
        assertTrue(NetGrossConverter.suggestGross(net, 0, YearMonth.of(2027, 2)).isEmpty());
        assertTrue(NetGrossConverter.suggestGrossOrEstimate(net, 0, YearMonth.of(2028, 2), book).orElseThrow().estimated());
    }

    private static ParsedPayroll oneLine(YearMonth period) {
        Map<PayrollField, BigDecimal> a = new EnumMap<>(PayrollField.class);
        a.put(LUONG_CO_BAN, bd(20_000_000));
        a.put(LUONG_GOP, bd(20_000_000));
        a.put(TONG_LUONG_GOP, bd(20_000_000));
        a.put(GIAM_TRU_BAN_THAN, bd(15_500_000));
        a.put(THUC_NHAN, bd(20_000_000));
        a.put(TONG_CHI_PHI, bd(20_000_000));
        PayrollLine line = new PayrollLine(10, "DOIT9999", "Lập trình viên", "Nhân viên X", LocalDate.of(2025, 1, 1), a);
        Map<PayrollField, Integer> cols = new EnumMap<>(PayrollField.class);
        for (PayrollField f : PayrollField.values()) {
            cols.put(f, f.ordinal());
        }
        return new ParsedPayroll(Optional.of(period), cols, List.of(line), List.of());
    }
}

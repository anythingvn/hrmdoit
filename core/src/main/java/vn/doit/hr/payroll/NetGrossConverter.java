package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Optional;

/** Quy đổi lương Net sang Gross (và ngược lại) theo {@link PayrollRules} của kỳ lương. */
public final class NetGrossConverter {

    private NetGrossConverter() {
    }

    /** Gợi ý Gross; {@code estimated} = dùng bộ quy tắc mới nhất (hiệu lực từ {@code rulesEffectiveFrom}) cho kỳ chưa có luật. */
    public record GrossSuggestion(BigDecimal gross, boolean estimated, YearMonth rulesEffectiveFrom) {
    }

    /** Gross nhỏ nhất (số nguyên đồng) cho ra Net ≥ {@code net}; rỗng nếu chưa có quy tắc chính thức cho kỳ này. */
    public static Optional<BigDecimal> suggestGross(BigDecimal net, int dependants, YearMonth period) {
        return suggestGross(net, dependants, period, RuleBook.defaults());
    }

    /** Gross gợi ý theo mức luật trong {@code book}; rỗng nếu kỳ không có luật chính thức. */
    public static Optional<BigDecimal> suggestGross(BigDecimal net, int dependants, YearMonth period, RuleBook book) {
        return suggestGross(net, dependants, period, book, true);
    }

    /** {@code insured == false}: người không thuộc diện đóng BHXH bắt buộc, Gross không cộng BH người lao động. */
    public static Optional<BigDecimal> suggestGross(BigDecimal net, int dependants, YearMonth period, RuleBook book, boolean insured) {
        return book.forPeriod(period).map(rules -> grossFor(net, dependants, rules, insured));
    }

    /** Như {@link #suggestGross}, nhưng kỳ sau mốc luật đã biết thì ước tính theo quy định mới nhất. Rỗng nếu trước 07/2020. */
    public static Optional<GrossSuggestion> suggestGrossOrEstimate(BigDecimal net, int dependants, YearMonth period) {
        return suggestGrossOrEstimate(net, dependants, period, RuleBook.defaults());
    }

    /** Như {@link #suggestGrossOrEstimate(BigDecimal, int, YearMonth)} theo mức luật trong {@code book}. */
    public static Optional<GrossSuggestion> suggestGrossOrEstimate(BigDecimal net, int dependants, YearMonth period, RuleBook book) {
        return suggestGrossOrEstimate(net, dependants, period, book, true);
    }

    /** Như trên; {@code insured == false}: không cộng BH người lao động. */
    public static Optional<GrossSuggestion> suggestGrossOrEstimate(BigDecimal net, int dependants, YearMonth period, RuleBook book,
                                                                   boolean insured) {
        return book.forPeriodOrLatest(period)
                .map(l -> new GrossSuggestion(grossFor(net, dependants, l.rules(), insured), l.estimated(), l.effectiveFrom()));
    }

    private static BigDecimal grossFor(BigDecimal net, int dependants, PayrollRules rules, boolean insured) {
        long lo = net.setScale(0, RoundingMode.CEILING).longValueExact();
        long hi = Math.multiplyExact(lo, 2L);
        while (lo < hi) {
            long mid = lo + (hi - lo) / 2;
            if (netFromGross(BigDecimal.valueOf(mid), dependants, rules, insured).compareTo(net) >= 0) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return BigDecimal.valueOf(lo);
    }

    public static BigDecimal netFromGross(BigDecimal gross, int dependants, PayrollRules rules) {
        return netFromGross(gross, dependants, rules, true);
    }

    /** {@code insured == false}: không trừ BHXH, BHYT, BHTN (người không thuộc diện đóng bắt buộc). */
    public static BigDecimal netFromGross(BigDecimal gross, int dependants, PayrollRules rules, boolean insured) {
        BigDecimal insurance = !insured ? BigDecimal.ZERO : gross.min(rules.socialInsuranceCap())
                .multiply(PayrollValidator.BHXH_RATE.add(PayrollValidator.BHYT_RATE))
                .add(gross.min(rules.unemploymentInsuranceCap()).multiply(PayrollValidator.BHTN_RATE));
        BigDecimal taxable = gross.subtract(insurance)
                .subtract(rules.personalDeduction())
                .subtract(rules.dependantDeduction().multiply(BigDecimal.valueOf(dependants)))
                .max(BigDecimal.ZERO);
        return gross.subtract(insurance).subtract(rules.monthlyPit(taxable));
    }
}

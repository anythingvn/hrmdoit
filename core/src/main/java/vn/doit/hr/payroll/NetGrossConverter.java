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
        return PayrollRules.forPeriod(period).map(rules -> grossFor(net, dependants, rules));
    }

    /** Như {@link #suggestGross}, nhưng kỳ sau mốc luật đã biết thì ước tính theo quy định mới nhất. Rỗng nếu trước 07/2020. */
    public static Optional<GrossSuggestion> suggestGrossOrEstimate(BigDecimal net, int dependants, YearMonth period) {
        return PayrollRules.forPeriodOrLatest(period)
                .map(l -> new GrossSuggestion(grossFor(net, dependants, l.rules()), l.estimated(), l.effectiveFrom()));
    }

    private static BigDecimal grossFor(BigDecimal net, int dependants, PayrollRules rules) {
        long lo = net.setScale(0, RoundingMode.CEILING).longValueExact();
        long hi = Math.multiplyExact(lo, 2L);
        while (lo < hi) {
            long mid = lo + (hi - lo) / 2;
            if (netFromGross(BigDecimal.valueOf(mid), dependants, rules).compareTo(net) >= 0) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return BigDecimal.valueOf(lo);
    }

    public static BigDecimal netFromGross(BigDecimal gross, int dependants, PayrollRules rules) {
        BigDecimal insurance = gross.min(rules.socialInsuranceCap())
                .multiply(PayrollValidator.BHXH_RATE.add(PayrollValidator.BHYT_RATE))
                .add(gross.min(rules.unemploymentInsuranceCap()).multiply(PayrollValidator.BHTN_RATE));
        BigDecimal taxable = gross.subtract(insurance)
                .subtract(rules.personalDeduction())
                .subtract(rules.dependantDeduction().multiply(BigDecimal.valueOf(dependants)))
                .max(BigDecimal.ZERO);
        return gross.subtract(insurance).subtract(rules.monthlyPit(taxable));
    }
}

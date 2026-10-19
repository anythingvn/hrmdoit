package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Optional;

/** Quy đổi lương Net sang Gross (và ngược lại) theo {@link PayrollRules} của kỳ lương. */
public final class NetGrossConverter {

    private NetGrossConverter() {
    }

    /** Gross nhỏ nhất (số nguyên đồng) cho ra Net ≥ {@code net}; rỗng nếu chưa có quy tắc cho kỳ này. */
    public static Optional<BigDecimal> suggestGross(BigDecimal net, int dependants, YearMonth period) {
        return PayrollRules.forPeriod(period).map(rules -> {
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
        });
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

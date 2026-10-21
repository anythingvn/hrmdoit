package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Một bộ mức theo luật dùng để <b>đối chiếu</b> bảng lương và quy đổi Net → Gross: giảm trừ gia cảnh, trần đóng bảo hiểm,
 * biểu thuế TNCN. Các bộ theo <b>ngày hiệu lực</b> nằm trong {@link RuleBook}; app nạp từ cơ sở dữ liệu, lõi có sẵn
 * {@link RuleBook#defaults()} (các mốc đã biết khi viết lõi).
 */
public record PayrollRules(
        BigDecimal personalDeduction,
        BigDecimal dependantDeduction,
        BigDecimal socialInsuranceCap,
        BigDecimal unemploymentInsuranceCap,
        List<Bracket> pitBrackets) {

    /** Bậc thuế: phần thu nhập tính thuế tháng đến {@code upTo} (null = không giới hạn) chịu thuế suất {@code rate}. */
    public record Bracket(BigDecimal upTo, BigDecimal rate) {
    }

    /** Kết quả tra quy tắc: {@code estimated} = kỳ nằm sau mốc luật đã biết, đang dùng bộ mới nhất (hiệu lực từ {@code effectiveFrom}). */
    public record RulesLookup(PayrollRules rules, boolean estimated, YearMonth effectiveFrom) {
    }

    /** Như {@link RuleBook#forPeriod} trên {@link RuleBook#defaults()}. */
    public static Optional<PayrollRules> forPeriod(YearMonth period) {
        return RuleBook.defaults().forPeriod(period);
    }

    /** Như {@link RuleBook#forPeriodOrLatest} trên {@link RuleBook#defaults()}. */
    public static Optional<RulesLookup> forPeriodOrLatest(YearMonth period) {
        return RuleBook.defaults().forPeriodOrLatest(period);
    }

    /** Thuế TNCN lũy tiến từng phần trên thu nhập tính thuế của tháng. */
    public BigDecimal monthlyPit(BigDecimal taxableIncome) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (Bracket b : pitBrackets) {
            if (taxableIncome.compareTo(lower) <= 0) {
                break;
            }
            BigDecimal upper = b.upTo() == null ? taxableIncome : taxableIncome.min(b.upTo());
            tax = tax.add(upper.subtract(lower).multiply(b.rate()));
            if (b.upTo() == null) {
                break;
            }
            lower = b.upTo();
        }
        return tax;
    }
}

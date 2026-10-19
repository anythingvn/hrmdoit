package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Các mức theo luật dùng để <b>đối chiếu</b> bảng lương và quy đổi Net → Gross, theo <b>ngày hiệu lực</b> (từ 07/2020).
 *
 * <p>Mỗi mốc dưới đây là một bộ quy tắc áp dụng từ tháng ghi ở mốc đến trước mốc kế tiếp. Đã đối chiếu (10/2026):
 * <ul>
 *   <li>07/2020: giảm trừ 11 tr / 4,4 tr (NQ 954/2020/UBTVQH14); biểu 7 bậc 5–35% tại 5/10/18/32/52/80 tr (Điều 22
 *       Luật Thuế TNCN 2007, sửa 2012); trần BHXH/BHYT 20 × lương cơ sở 1.490.000 (NĐ 38/2019) = 29,8 tr; trần BHTN
 *       20 × lương tối thiểu vùng I 4.420.000 (NĐ 90/2019) = 88,4 tr.</li>
 *   <li>07/2022: vùng I 4.680.000 (NĐ 38/2022) → trần BHTN 93,6 tr.</li>
 *   <li>07/2023: lương cơ sở 1.800.000 (NĐ 24/2023) → trần BHXH 36 tr.</li>
 *   <li>07/2024: lương cơ sở 2.340.000 (NĐ 73/2024) → 46,8 tr; vùng I 4.960.000 (NĐ 74/2024) → 99,2 tr.</li>
 *   <li>01/2026: giảm trừ 15,5 tr / 6,2 tr (NQ 110/2025/UBTVQH15); biểu 5 bậc 5–10–20–30–35% (Luật Thuế TNCN 2025,
 *       áp dụng cho tiền lương từ kỳ tính thuế 2026); vùng I 5.310.000 (NĐ 293/2025) → 106,2 tr.</li>
 *   <li>07/2026: mức tham chiếu/lương cơ sở 2.530.000 (NĐ 161/2026) → trần BHXH 50,6 tr.</li>
 * </ul>
 *
 * <p>Khi có văn bản mới: thêm một mốc vào {@link #MILESTONES} và dời {@link #KNOWN_THROUGH} tới tháng cuối cùng đã
 * biết chắc luật. Kỳ sau {@code KNOWN_THROUGH} chỉ có bộ quy tắc mới nhất ở dạng "ước tính" ({@link #forPeriodOrLatest}).
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

    private record Milestone(YearMonth from, PayrollRules rules) {
    }

    private static final List<Bracket> PIT_2009 = List.of(
            new Bracket(m(5), new BigDecimal("0.05")),
            new Bracket(m(10), new BigDecimal("0.10")),
            new Bracket(m(18), new BigDecimal("0.15")),
            new Bracket(m(32), new BigDecimal("0.20")),
            new Bracket(m(52), new BigDecimal("0.25")),
            new Bracket(m(80), new BigDecimal("0.30")),
            new Bracket(null, new BigDecimal("0.35")));

    private static final List<Bracket> PIT_2026 = List.of(
            new Bracket(m(10), new BigDecimal("0.05")),
            new Bracket(m(30), new BigDecimal("0.10")),
            new Bracket(m(60), new BigDecimal("0.20")),
            new Bracket(m(100), new BigDecimal("0.30")),
            new Bracket(null, new BigDecimal("0.35")));

    /** Theo thứ tự thời gian tăng dần. */
    private static final List<Milestone> MILESTONES = List.of(
            milestone(2020, 7, "11000000", "4400000", "29800000", "88400000", PIT_2009),
            milestone(2022, 7, "11000000", "4400000", "29800000", "93600000", PIT_2009),
            milestone(2023, 7, "11000000", "4400000", "36000000", "93600000", PIT_2009),
            milestone(2024, 7, "11000000", "4400000", "46800000", "99200000", PIT_2009),
            milestone(2026, 1, "15500000", "6200000", "46800000", "106200000", PIT_2026),
            milestone(2026, 7, "15500000", "6200000", "50600000", "106200000", PIT_2026));

    /** Tháng cuối cùng đã biết chắc luật áp dụng; sau tháng này chỉ còn "ước tính". */
    static final YearMonth KNOWN_THROUGH = YearMonth.of(2026, 12);

    /**
     * Bộ quy tắc chính thức cho kỳ lương; rỗng nếu trước 07/2020 hoặc sau {@link #KNOWN_THROUGH} (khi đó bỏ qua các
     * bước đối chiếu theo luật).
     */
    public static Optional<PayrollRules> forPeriod(YearMonth period) {
        if (period.isAfter(KNOWN_THROUGH)) {
            return Optional.empty();
        }
        return effective(period).map(Milestone::rules);
    }

    /**
     * Như {@link #forPeriod}, nhưng kỳ sau mốc luật đã biết thì trả bộ quy tắc mới nhất kèm cờ {@code estimated}.
     * Rỗng nếu trước 07/2020.
     */
    public static Optional<RulesLookup> forPeriodOrLatest(YearMonth period) {
        boolean estimated = period.isAfter(KNOWN_THROUGH);
        return effective(period).map(ms -> new RulesLookup(ms.rules(), estimated, ms.from()));
    }

    private static Optional<Milestone> effective(YearMonth period) {
        Milestone found = null;
        for (Milestone ms : MILESTONES) {
            if (ms.from().isAfter(period)) {
                break;
            }
            found = ms;
        }
        return Optional.ofNullable(found);
    }

    private static Milestone milestone(int year, int month, String personal, String dependant, String siCap,
                                       String uiCap, List<Bracket> brackets) {
        return new Milestone(YearMonth.of(year, month), new PayrollRules(new BigDecimal(personal),
                new BigDecimal(dependant), new BigDecimal(siCap), new BigDecimal(uiCap), brackets));
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

    private static BigDecimal m(long millions) {
        return BigDecimal.valueOf(millions * 1_000_000L);
    }
}

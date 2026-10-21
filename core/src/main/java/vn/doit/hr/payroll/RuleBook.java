package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Các bộ mức luật định theo ngày hiệu lực. Mỗi mốc áp dụng từ tháng {@code from} đến trước mốc kế tiếp. Nhiều mốc cùng tháng
 * thì mốc đứng sau trong danh sách đầu vào thắng (app truyền theo thứ tự thêm). Kỳ sau {@code knownThrough} chỉ có bộ mới
 * nhất ở dạng "ước tính" ({@link #forPeriodOrLatest}).
 *
 * <p>Các mốc mặc định ({@link #defaults()}), đã đối chiếu 10/2026:
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
 */
public record RuleBook(List<RuleBook.Milestone> milestones, YearMonth knownThrough) {

    public record Milestone(YearMonth from, PayrollRules rules) {
    }

    public RuleBook {
        // Giữ mốc sau cùng cho mỗi tháng rồi sắp theo tháng.
        Map<YearMonth, Milestone> byMonth = new LinkedHashMap<>();
        for (Milestone m : milestones) {
            byMonth.put(m.from(), m);
        }
        List<Milestone> sorted = new ArrayList<>(byMonth.values());
        sorted.sort(Comparator.comparing(Milestone::from));
        milestones = List.copyOf(sorted);
    }

    private static final List<PayrollRules.Bracket> PIT_2009 = List.of(
            new PayrollRules.Bracket(m(5), new BigDecimal("0.05")),
            new PayrollRules.Bracket(m(10), new BigDecimal("0.10")),
            new PayrollRules.Bracket(m(18), new BigDecimal("0.15")),
            new PayrollRules.Bracket(m(32), new BigDecimal("0.20")),
            new PayrollRules.Bracket(m(52), new BigDecimal("0.25")),
            new PayrollRules.Bracket(m(80), new BigDecimal("0.30")),
            new PayrollRules.Bracket(null, new BigDecimal("0.35")));

    private static final List<PayrollRules.Bracket> PIT_2026 = List.of(
            new PayrollRules.Bracket(m(10), new BigDecimal("0.05")),
            new PayrollRules.Bracket(m(30), new BigDecimal("0.10")),
            new PayrollRules.Bracket(m(60), new BigDecimal("0.20")),
            new PayrollRules.Bracket(m(100), new BigDecimal("0.30")),
            new PayrollRules.Bracket(null, new BigDecimal("0.35")));

    private static final RuleBook DEFAULTS = new RuleBook(List.of(
            milestone(2020, 7, "11000000", "4400000", "29800000", "88400000", PIT_2009),
            milestone(2022, 7, "11000000", "4400000", "29800000", "93600000", PIT_2009),
            milestone(2023, 7, "11000000", "4400000", "36000000", "93600000", PIT_2009),
            milestone(2024, 7, "11000000", "4400000", "46800000", "99200000", PIT_2009),
            milestone(2026, 1, "15500000", "6200000", "46800000", "106200000", PIT_2026),
            milestone(2026, 7, "15500000", "6200000", "50600000", "106200000", PIT_2026)),
            YearMonth.of(2026, 12));

    /** Các mốc đã biết khi viết lõi; dùng cho test của lõi và dữ liệu nạp ban đầu của app. */
    public static RuleBook defaults() {
        return DEFAULTS;
    }

    /** Bộ quy tắc chính thức cho kỳ lương; rỗng nếu trước mốc đầu tiên hoặc sau {@code knownThrough}. */
    public Optional<PayrollRules> forPeriod(YearMonth period) {
        if (period.isAfter(knownThrough)) {
            return Optional.empty();
        }
        return effective(period).map(Milestone::rules);
    }

    /** Như {@link #forPeriod}, nhưng kỳ sau {@code knownThrough} thì trả bộ mới nhất kèm cờ {@code estimated}. */
    public Optional<PayrollRules.RulesLookup> forPeriodOrLatest(YearMonth period) {
        boolean estimated = period.isAfter(knownThrough);
        return effective(period).map(ms -> new PayrollRules.RulesLookup(ms.rules(), estimated, ms.from()));
    }

    private Optional<Milestone> effective(YearMonth period) {
        Milestone found = null;
        for (Milestone ms : milestones) {
            if (ms.from().isAfter(period)) {
                break;
            }
            found = ms;
        }
        return Optional.ofNullable(found);
    }

    private static Milestone milestone(int year, int month, String personal, String dependant, String siCap,
                                       String uiCap, List<PayrollRules.Bracket> brackets) {
        return new Milestone(YearMonth.of(year, month), new PayrollRules(new BigDecimal(personal),
                new BigDecimal(dependant), new BigDecimal(siCap), new BigDecimal(uiCap), brackets));
    }

    private static BigDecimal m(long millions) {
        return BigDecimal.valueOf(millions * 1_000_000L);
    }
}

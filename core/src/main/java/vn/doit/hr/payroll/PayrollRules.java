package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Các mức theo luật dùng để <b>đối chiếu</b> bảng lương (không dùng để tính lương).
 *
 * <p>Các con số này thay đổi theo thời gian, nên khi đưa vào hệ thống thật hãy lưu thành bảng cấu hình
 * có ngày hiệu lực thay vì để cứng trong code. Giá trị dưới đây đã đối chiếu tại thời điểm viết (10/2026):
 * <ul>
 *   <li>Giảm trừ gia cảnh 15,5 tr (bản thân) / 6,2 tr (mỗi người phụ thuộc) – NQ 110/2025/UBTVQH15, từ kỳ tính thuế 2026.</li>
 *   <li>Biểu thuế lũy tiến 5 bậc 5–10–20–30–35% – Luật Thuế TNCN 2025, áp dụng cho tiền lương từ kỳ tính thuế 2026.</li>
 *   <li>Trần lương đóng BHXH/BHYT = 20 × mức tham chiếu: 2.340.000 → 46,8 tr; từ 01/7/2026 là 2.530.000 (NĐ 161/2026/NĐ-CP) → 50,6 tr.</li>
 *   <li>Trần lương đóng BHTN = 20 × lương tối thiểu vùng I 2026 (5.310.000) = 106,2 tr.</li>
 * </ul>
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

    private static final List<Bracket> PIT_2026 = List.of(
            new Bracket(m(10), new BigDecimal("0.05")),
            new Bracket(m(30), new BigDecimal("0.10")),
            new Bracket(m(60), new BigDecimal("0.20")),
            new Bracket(m(100), new BigDecimal("0.30")),
            new Bracket(null, new BigDecimal("0.35")));

    /** Bộ quy tắc cho kỳ lương; rỗng nếu chưa khai báo (khi đó bỏ qua các bước đối chiếu theo luật). */
    public static Optional<PayrollRules> forPeriod(YearMonth period) {
        if (period.getYear() != 2026) {
            return Optional.empty();
        }
        BigDecimal bhxhCap = period.isBefore(YearMonth.of(2026, 7)) ? new BigDecimal("46800000") : new BigDecimal("50600000");
        return Optional.of(new PayrollRules(
                new BigDecimal("15500000"),
                new BigDecimal("6200000"),
                bhxhCap,
                new BigDecimal("106200000"),
                PIT_2026));
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

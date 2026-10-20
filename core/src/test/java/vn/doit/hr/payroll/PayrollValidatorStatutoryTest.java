package vn.doit.hr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.doit.hr.payroll.PayrollField.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Dòng giả lập: lương 40 tr, 1 người phụ thuộc, tính đúng theo công thức HIỆN TẠI trong file Excel
 * (trần BHXH 29,8 tr, giảm trừ NPT 4,4 tr, biểu thuế 7 bậc) → validator phải cảnh báo cả ba chỗ.
 */
class PayrollValidatorStatutoryTest {

    @Test
    void flagsOutdatedFormulaConstants() {
        Map<PayrollField, BigDecimal> a = new EnumMap<>(PayrollField.class);
        put(a, LUONG_CO_BAN, 40_000_000);
        put(a, LUONG_DONG_BHXH_BHYT, 29_800_000);       // MIN(29800000, ...) trong file
        put(a, LUONG_DONG_BHTN, 40_000_000);
        put(a, LUONG_GOP, 40_000_000);
        put(a, TONG_LUONG_GOP, 40_000_000);
        put(a, BHXH_NLD, 2_384_000);                      // 8% × 29,8 tr
        put(a, BHYT_NLD, 447_000);
        put(a, BHTN_NLD, 400_000);
        put(a, TONG_BH_NLD, 3_231_000);
        put(a, GIAM_TRU_BAN_THAN, 15_500_000);
        put(a, SO_NGUOI_PHU_THUOC, 1);
        put(a, GIAM_TRU_NPT, 4_400_000);                  // =AC*4400000 trong file
        long taxable = 40_000_000 - 3_231_000 - 15_500_000 - 4_400_000; // 16.869.000
        put(a, THU_NHAP_TINH_THUE, taxable);
        long oldTax = 750_000 + (long) ((taxable - 10_000_000) * 0.15); // biểu 7 bậc cũ
        put(a, THUE_TNCN, oldTax);
        put(a, THUC_NHAN, 40_000_000 - 3_231_000 - oldTax);
        put(a, TONG_CHI_PHI, 40_000_000);

        PayrollLine line = new PayrollLine(10, "DOIT9999", "Trưởng nhóm", "Nhân viên X", LocalDate.of(2025, 1, 1), a);
        Map<PayrollField, Integer> cols = new EnumMap<>(PayrollField.class);
        for (PayrollField f : PayrollField.values()) {
            cols.put(f, f.ordinal());
        }
        ParsedPayroll p = new ParsedPayroll(Optional.of(YearMonth.of(2026, 6)), cols, List.of(line), List.of());

        List<Issue> issues = new PayrollValidator().validate(p, YearMonth.of(2026, 6), c -> Optional.of("Nhân viên X"));
        issues.forEach(System.out::println);

        assertTrue(has(issues, LUONG_DONG_BHXH_BHYT, "46.800.000"), "trần BHXH");
        assertTrue(has(issues, GIAM_TRU_NPT, "6.200.000"), "giảm trừ NPT");
        assertTrue(has(issues, THUE_TNCN, "biểu lũy tiến"), "thuế");
        assertTrue(issues.stream().noneMatch(i -> i.field() == TONG_LUONG_GOP || i.field() == THUC_NHAN),
                "các cột tổng vẫn khớp nội bộ");
        assertEquals(0L, issues.stream().filter(i -> i.severity() == Issue.Severity.ERROR).count());
    }

    private static boolean has(List<Issue> issues, PayrollField f, String text) {
        return issues.stream().anyMatch(i -> i.field() == f && i.message().contains(text));
    }

    private static void put(Map<PayrollField, BigDecimal> m, PayrollField f, long v) {
        m.put(f, BigDecimal.valueOf(v));
    }
}

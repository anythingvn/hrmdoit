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

    /**
     * Dòng do bộ tính lương sinh ra: mức đóng lấy từ hồ sơ (khác lương cơ bản), tỷ lệ BHXH theo thông số 7%, trợ cấp thôi việc
     * không chịu thuế; và một dòng nghỉ không lương 14 ngày không đóng BH. Chế độ bản tính không cảnh báo; chế độ nhập file giữ
     * nguyên cách đối chiếu cũ.
     */
    @Test
    void calculatedRowsUseCalcOptions() {
        Map<PayrollField, BigDecimal> a = new EnumMap<>(PayrollField.class);
        put(a, LUONG_CO_BAN, 30_000_000);
        put(a, NGAY_CONG_CHUAN, 22);
        put(a, NGAY_CONG_THUC_TE, 22);
        put(a, LUONG_GOP, 30_000_000);
        put(a, TRO_CAP_THOI_VIEC, 50_000_000);
        put(a, TONG_LUONG_GOP, 80_000_000);
        put(a, LUONG_DONG_BHXH_BHYT, 10_000_000);
        put(a, LUONG_DONG_BHTN, 10_000_000);
        put(a, BHXH_NLD, 700_000);
        put(a, BHYT_NLD, 150_000);
        put(a, BHTN_NLD, 100_000);
        put(a, TONG_BH_NLD, 950_000);
        put(a, GIAM_TRU_BAN_THAN, 15_500_000);
        long taxable = 30_000_000 - 950_000 - 15_500_000;
        put(a, THU_NHAP_TINH_THUE, taxable);
        BigDecimal tax = RuleBook.defaults().forPeriod(YearMonth.of(2026, 6)).orElseThrow().monthlyPit(BigDecimal.valueOf(taxable));
        a.put(THUE_TNCN, tax);
        a.put(THUC_NHAN, BigDecimal.valueOf(80_000_000 - 950_000).subtract(tax));
        a.put(TONG_CHI_PHI, BigDecimal.valueOf(80_000_000));

        Map<PayrollField, BigDecimal> u = new EnumMap<>(PayrollField.class);
        put(u, LUONG_CO_BAN, 22_000_000);
        put(u, NGAY_CONG_CHUAN, 22);
        put(u, NGAY_CONG_THUC_TE, 8);
        put(u, LUONG_GOP, 8_000_000);
        put(u, TONG_LUONG_GOP, 8_000_000);
        put(u, GIAM_TRU_BAN_THAN, 15_500_000);
        put(u, THUC_NHAN, 8_000_000);
        put(u, TONG_CHI_PHI, 8_000_000);

        LocalDate start = LocalDate.of(2025, 1, 1);
        Map<PayrollField, Integer> cols = new EnumMap<>(PayrollField.class);
        for (PayrollField f : PayrollField.values()) {
            cols.put(f, f.ordinal());
        }
        ParsedPayroll p = new ParsedPayroll(Optional.empty(), cols,
                List.of(new PayrollLine(1, "DOIT0001", "NV", "Nhân viên X", start, a),
                        new PayrollLine(2, "DOIT0002", "NV", "Nhân viên Y", start, u)), List.of());
        PayrollValidator.Options calc = new PayrollValidator.Options(new BigDecimal("0.07"), new BigDecimal("0.015"),
                new BigDecimal("0.01"), false, true, new BigDecimal("14"));

        List<Issue> calcIssues = new PayrollValidator(RuleBook.defaults(), calc)
                .validate(p, YearMonth.of(2026, 6), code -> Optional.of(code.equals("DOIT0001") ? "Nhân viên X" : "Nhân viên Y"));
        assertEquals(List.of(), calcIssues);

        // Nhập file: giữ đối chiếu cũ (mức đóng = lương + PC, 8%, trợ cấp thôi việc trong thu nhập tính thuế, thiếu BH).
        List<Issue> importIssues = new PayrollValidator(RuleBook.defaults())
                .validate(p, YearMonth.of(2026, 6), code -> Optional.of(code.equals("DOIT0001") ? "Nhân viên X" : "Nhân viên Y"));
        assertTrue(has(importIssues, LUONG_DONG_BHXH_BHYT, "30.000.000"), importIssues.toString());
        assertTrue(has(importIssues, BHXH_NLD, "8%"), importIssues.toString());
        assertTrue(has(importIssues, THU_NHAP_TINH_THUE, "tính lại được"), importIssues.toString());
        assertTrue(has(importIssues, TONG_BH_NLD, "Có ngày ký HĐLĐ"), importIssues.toString());

        // Bản tính: mức đóng vượt trần vẫn bị cảnh báo.
        put(a, LUONG_DONG_BHXH_BHYT, 60_000_000);
        ParsedPayroll p2 = new ParsedPayroll(Optional.empty(), cols,
                List.of(new PayrollLine(1, "DOIT0001", "NV", "Nhân viên X", start, a)), List.of());
        List<Issue> overCap = new PayrollValidator(RuleBook.defaults(), calc)
                .validate(p2, YearMonth.of(2026, 6), code -> Optional.of(code.equals("DOIT0001") ? "Nhân viên X" : "Nhân viên Y"));
        assertTrue(has(overCap, LUONG_DONG_BHXH_BHYT, "trần"), overCap.toString());
    }

    private static boolean has(List<Issue> issues, PayrollField f, String text) {
        return issues.stream().anyMatch(i -> i.field() == f && i.message().contains(text));
    }

    private static void put(Map<PayrollField, BigDecimal> m, PayrollField f, long v) {
        m.put(f, BigDecimal.valueOf(v));
    }
}

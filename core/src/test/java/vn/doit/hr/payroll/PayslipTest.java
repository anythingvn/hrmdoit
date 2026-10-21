package vn.doit.hr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.doit.hr.contract.TemplateTags;

class PayslipTest {

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static Map<PayrollField, BigDecimal> sample() {
        Map<PayrollField, BigDecimal> m = new EnumMap<>(PayrollField.class);
        m.put(PayrollField.LUONG_CO_BAN, bd(20_000_000));
        m.put(PayrollField.PHU_CAP_DONG_BH, bd(1_000_000));
        m.put(PayrollField.NGAY_CONG_CHUAN, bd(22));
        m.put(PayrollField.NGAY_CONG_THUC_TE, bd(20));
        m.put(PayrollField.THUONG_DU_AN, bd(500_000));
        for (PayrollField f : new PayrollField[] { PayrollField.PC_CHUYEN_CAN, PayrollField.PC_TRACH_NHIEM, PayrollField.PC_XANG_XE,
                PayrollField.THUONG_KPI, PayrollField.OT_CHIU_THUE, PayrollField.TRO_CAP_THOI_VIEC }) {
            m.put(f, bd(100_000));
        }
        m.put(PayrollField.THUA_THIEU, bd(200_000));
        m.put(PayrollField.BHXH_NLD, bd(1_680_000));
        m.put(PayrollField.BHYT_NLD, bd(315_000));
        m.put(PayrollField.BHTN_NLD, bd(210_000));
        m.put(PayrollField.GIAM_TRU_BAN_THAN, bd(15_500_000));
        m.put(PayrollField.THUE_TNCN, bd(100_000));
        return m;
    }

    @Test
    void formulasLikeExcel() {
        Payslip s = Payslip.of(sample());
        assertTrue(s.prorated());
        BigDecimal c = bd(20_000_000).divide(bd(22), 10, RoundingMode.HALF_UP).multiply(bd(20)).add(bd(1_000_000));
        assertEquals(0, s.a().compareTo(bd(21_000_000)));
        assertEquals(0, s.c().subtract(c).abs().compareTo(new BigDecimal("0.01")) < 0 ? 0 : 1);
        BigDecimal g = c.add(bd(500_000)).add(bd(600_000));
        assertEquals(0, s.e().compareTo(bd(600_000)));
        assertTrue(s.g().subtract(g).abs().compareTo(new BigDecimal("0.01")) < 0);
        assertEquals(0, s.h().compareTo(bd(200_000)));
        BigDecimal n = g.subtract(bd(1_680_000 + 315_000 + 210_000 + 15_500_000));
        assertTrue(s.n().subtract(n).abs().compareTo(new BigDecimal("0.01")) < 0);
        assertEquals(0, s.o().compareTo(bd(100_000)));
        BigDecimal q = g.add(bd(200_000)).subtract(bd(1_680_000 + 315_000 + 210_000 + 100_000));
        assertTrue(s.q().subtract(q).abs().compareTo(new BigDecimal("0.01")) < 0);

        Map<PayrollField, BigDecimal> withNet = sample();
        withNet.put(PayrollField.THUC_NHAN, q.setScale(0, RoundingMode.HALF_UP));
        assertTrue(Payslip.of(withNet).differenceFromNet(withNet).abs().compareTo(BigDecimal.ONE) <= 0);
    }

    @Test
    void negativeTaxableIsZero() {
        Map<PayrollField, BigDecimal> m = sample();
        m.put(PayrollField.GIAM_TRU_BAN_THAN, bd(500_000_000));
        assertEquals(0, Payslip.of(m).n().signum());
    }

    @Test
    void noStandardDaysUsesMonthlyGross() {
        Map<PayrollField, BigDecimal> m = new EnumMap<>(PayrollField.class);
        m.put(PayrollField.LUONG_GOP, bd(5_000_000));
        m.put(PayrollField.THUC_NHAN, bd(5_000_000));
        Payslip s = Payslip.of(m);
        assertFalse(s.prorated());
        assertEquals(0, s.c().compareTo(bd(5_000_000)));
        assertNull(s.a());
        assertNull(s.a1());
        assertNull(s.b());
        assertNull(s.standardDays());
        assertEquals(0, s.q().compareTo(bd(5_000_000)));
    }

    @Test
    void fieldsMap() {
        PayslipFields.Notes notes = new PayslipFields.Notes(bd(46_800_000), bd(99_200_000), bd(15_500_000), bd(6_200_000), null);
        Map<String, String> f = PayslipFields.toMap(
                new PayslipFields.Header("CÔNG TY DO IT", YearMonth.of(2026, 6), "Nguyễn Văn A", "DOIT2401"), Payslip.of(sample()), notes);
        assertEquals("06/2026", f.get("kyLuong"));
        assertEquals("21.000.000", f.get("a"));
        assertTrue(f.get("q").matches("\\d{1,3}(\\.\\d{3})+"), f.get("q"));
        assertTrue(f.get("ghiChu2").contains("46.800.000") && f.get("ghiChu2").contains("99.200.000"), f.get("ghiChu2"));
        assertTrue(f.get("ghiChu3").contains("15.500.000") && f.get("ghiChu3").contains("6.200.000"));
        assertEquals("", f.get("ghiChu4"));
        assertEquals(TemplateTags.PAYSLIP_TAGS, f.keySet());

        Map<String, String> withBasis = PayslipFields.toMap(new PayslipFields.Header("X", YearMonth.of(2026, 6), "A", "B"), Payslip.of(sample()),
                new PayslipFields.Notes(bd(1), bd(1), bd(1), bd(1), "Nghị quyết 110/2025/UBTVQH15"));
        assertEquals("4. Căn cứ: Nghị quyết 110/2025/UBTVQH15.", withBasis.get("ghiChu4"));
        assertEquals("", PayslipFields.toMap(new PayslipFields.Header("X", YearMonth.of(2026, 6), "A", "B"), Payslip.of(sample()), null).get("ghiChu2"));
    }
}

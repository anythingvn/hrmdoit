package vn.doit.hr.payroll.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.doit.hr.payroll.PayrollField.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollRules;
import vn.doit.hr.payroll.RuleBook;
import vn.doit.hr.payroll.calc.CalcInput.NamedAllowance;
import vn.doit.hr.payroll.calc.CalcInput.OvertimeItem;
import vn.doit.hr.payroll.calc.CalcInput.PersonKind;
import vn.doit.hr.payroll.calc.CalcInput.SalarySegment;
import vn.doit.hr.payroll.calc.CalcSettings.DayKind;

/** Quy tắc của bộ tính lương, số tự đặt (không dùng dữ liệu lương thật). */
class PayrollCalculatorTest {

    private static final PayrollRules RULES = RuleBook.defaults().forPeriod(YearMonth.of(2026, 6)).orElseThrow();

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void assertAmount(long expected, CalcResult r, PayrollField f) {
        assertAmount(bd(expected), r, f);
    }

    private static void assertAmount(BigDecimal expected, CalcResult r, PayrollField f) {
        BigDecimal actual = r.amounts().get(f);
        assertEquals(0, expected.compareTo(actual), f + ": mong " + expected + ", được " + actual);
    }

    /** Dựng đầu vào HĐLĐ có đóng BH, đủ công. */
    private static final class In {
        PersonKind kind = PersonKind.HDLD;
        boolean insured = true;
        List<SalarySegment> salary;
        BigDecimal insuranceOverride;
        Map<DayKind, BigDecimal> days = new EnumMap<>(DayKind.class);
        BigDecimal standardDays = bd(22);
        List<NamedAllowance> allowances = List.of();
        List<OvertimeItem> overtime = List.of();
        BigDecimal unusedLeaveDays;
        int dependants;
        boolean commitment08;
        Map<PayrollField, BigDecimal> manual = new EnumMap<>(PayrollField.class);
        Map<PayrollField, BigDecimal> overrides = new EnumMap<>(PayrollField.class);

        In(long base, long insuredAllowance) {
            salary = List.of(new SalarySegment(bd(base), bd(insuredAllowance), false, BigDecimal.ONE));
            days.put(DayKind.WORK, bd(22));
        }

        In net(long net) {
            salary = List.of(new SalarySegment(bd(net), BigDecimal.ZERO, true, BigDecimal.ONE));
            return this;
        }

        In work(String d) {
            days.put(DayKind.WORK, bd(d));
            return this;
        }

        CalcInput build() {
            return new CalcInput(kind, insured, salary, insuranceOverride, days, standardDays, allowances, overtime,
                    unusedLeaveDays, dependants, commitment08, manual, overrides);
        }

        CalcResult calc() {
            return calc(CalcSettings.defaults());
        }

        CalcResult calc(CalcSettings s) {
            return PayrollCalculator.calculate(build(), s, RULES, false);
        }
    }

    /** Thông số mặc định, đổi một số giá trị. */
    private static final class S {
        CalcSettings d = CalcSettings.defaults();
        BigDecimal bhxhEmployee = d.bhxhEmployee();
        BigDecimal bhxhCompany = d.bhxhCompany();
        boolean unionFee = d.unionFeeEnabled();
        boolean otWithAllowance = d.overtimeBaseIncludesInsuredAllowance();
        boolean otAllNonTaxable = d.overtimeAllNonTaxable();
        java.util.Set<DayKind> dayKinds = d.actualDayKinds();
        boolean payLeave = d.payUnusedLeaveOnTermination();

        CalcSettings build() {
            return new CalcSettings(bhxhEmployee, d.bhytEmployee(), d.bhtnEmployee(), bhxhCompany, d.bhytCompany(),
                    d.bhtnCompany(), unionFee, d.unionFeeRate(), d.hoursPerDay(), otWithAllowance, otAllNonTaxable, dayKinds,
                    d.allowanceKeywords(), d.withholdingThreshold(), d.withholdingRate(), payLeave);
        }
    }

    private static CalcSettings unionFeeOn() {
        S s = new S();
        s.unionFee = true;
        return s.build();
    }

    @Test
    void defaultsMatchSpec() {
        CalcSettings s = CalcSettings.defaults();
        assertEquals(0, bd("0.08").compareTo(s.bhxhEmployee()));
        assertEquals(0, bd("0.175").compareTo(s.bhxhCompany()));
        assertEquals(0, bd("0.02").compareTo(s.unionFeeRate()));
        assertFalse(s.unionFeeEnabled(), "DOIT hiện không tính kinh phí công đoàn");
        assertFalse(s.overtimeAllNonTaxable());
        assertEquals(0, bd(8).compareTo(s.hoursPerDay()));
        assertFalse(s.overtimeBaseIncludesInsuredAllowance());
        assertEquals(java.util.Set.of(DayKind.WORK, DayKind.PAID_LEAVE, DayKind.HOLIDAY, DayKind.TRIP), s.actualDayKinds());
        assertEquals(List.of("chuyen can"), s.allowanceKeywords().get(PC_CHUYEN_CAN));
        assertEquals(List.of("xang"), s.allowanceKeywords().get(PC_XANG_XE));
        assertEquals(List.of("trach nhiem"), s.allowanceKeywords().get(PC_TRACH_NHIEM));
        assertEquals(List.of("dong bh", "dong bao hiem"), s.allowanceKeywords().get(PHU_CAP_DONG_BH));
        assertEquals(0, bd(2_000_000).compareTo(s.withholdingThreshold()));
        assertEquals(0, bd("0.10").compareTo(s.withholdingRate()));
        assertTrue(s.payUnusedLeaveOnTermination());
    }

    @Test
    void namedAllowanceMatchingInsuredKeywordIsNotSilentlyDropped() {
        In in = new In(20_000_000, 0);
        in.allowances = List.of(new NamedAllowance("Phụ cấp đóng BH", bd(1_000_000)));
        CalcResult r = in.calc();
        assertAmount(1_000_000, r, THUA_THIEU);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("không khớp")));
    }

    @Test
    void grossFullMonth() {
        In in = new In(20_000_000, 1_000_000);
        in.dependants = 1;
        in.manual.put(THUONG_KPI, bd(10_000_000));
        CalcResult r = in.calc();

        assertAmount(20_000_000, r, LUONG_CO_BAN);
        assertAmount(1_000_000, r, PHU_CAP_DONG_BH);
        assertAmount(22, r, NGAY_CONG_CHUAN);
        assertAmount(22, r, NGAY_CONG_THUC_TE);
        assertAmount(21_000_000, r, LUONG_GOP);
        assertAmount(10_000_000, r, THUONG_KPI);
        assertAmount(31_000_000, r, TONG_LUONG_GOP);
        assertAmount(21_000_000, r, LUONG_DONG_BHXH_BHYT);
        assertAmount(21_000_000, r, LUONG_DONG_BHTN);
        assertAmount(1_680_000, r, BHXH_NLD);
        assertAmount(315_000, r, BHYT_NLD);
        assertAmount(210_000, r, BHTN_NLD);
        assertAmount(2_205_000, r, TONG_BH_NLD);
        assertAmount(15_500_000, r, GIAM_TRU_BAN_THAN);
        assertAmount(1, r, SO_NGUOI_PHU_THUOC);
        assertAmount(6_200_000, r, GIAM_TRU_NPT);
        assertAmount(7_095_000, r, THU_NHAP_TINH_THUE);
        assertAmount(RULES.monthlyPit(bd(7_095_000)).setScale(0, RoundingMode.HALF_UP), r, THUE_TNCN);
        assertAmount(354_750, r, THUE_TNCN);
        assertAmount(28_440_250, r, THUC_NHAN);
        assertAmount(3_675_000, r, BHXH_NSDLD);
        assertAmount(630_000, r, BHYT_NSDLD);
        assertAmount(210_000, r, BHTN_NSDLD);
        assertAmount(4_515_000, r, TONG_BH_NSDLD);
        assertAmount(0, r, KPCD); // mặc định tắt
        assertAmount(31_000_000 + 4_515_000, r, TONG_CHI_PHI);
        assertEquals(PayrollField.values().length, r.amounts().size(), "đủ 36 khoản");
        assertEquals(List.of(), r.warnings());

        CalcResult union = in.calc(unionFeeOn());
        assertAmount(420_000, union, KPCD);
        assertAmount(31_000_000 + 4_515_000 + 420_000, union, TONG_CHI_PHI);
    }

    @Test
    void midMonthRaise() {
        In in = new In(0, 0);
        in.salary = List.of(new SalarySegment(bd(10_000_000), bd(1_000_000), false, bd("0.5")),
                new SalarySegment(bd(12_000_000), bd(2_000_000), false, bd("0.5")));
        CalcResult full = in.calc();
        assertAmount(12_500_000, full, LUONG_GOP); // 11tr + PC 1,5tr
        assertEquals(List.of(), full.warnings());

        in.work("20");
        CalcResult r = in.calc();
        assertAmount(11_500_000, r, LUONG_GOP); // (10tr×10 + 12tr×10) ÷ 22 + PC trả đủ 1,5tr
        assertTrue(r.amounts().get(LUONG_GOP).compareTo(bd(12_500_000)) < 0);
    }

    @Test
    void insuredAllowanceNotProrated() {
        CalcResult r = new In(22_000_000, 2_000_000).work("11").calc();
        assertAmount(13_000_000, r, LUONG_GOP); // 22tr × 11 ÷ 22 + 2tr
        assertAmount(24_000_000, r, LUONG_DONG_BHXH_BHYT);
    }

    @Test
    void segmentSharesMustSumToOne() {
        In in = new In(0, 0);
        in.salary = List.of(new SalarySegment(bd(10_000_000), BigDecimal.ZERO, false, bd("0.5")),
                new SalarySegment(bd(12_000_000), BigDecimal.ZERO, false, bd("0.4")));
        assertTrue(in.calc().warnings().stream().anyMatch(w -> w.contains("tỷ lệ")), in.calc().warnings().toString());
    }

    @Test
    void insuranceCap() {
        CalcResult r = new In(60_000_000, 0).calc();
        assertAmount(46_800_000, r, LUONG_DONG_BHXH_BHYT);
        assertAmount(60_000_000, r, LUONG_DONG_BHTN);
        assertAmount(3_744_000, r, BHXH_NLD);
        assertAmount(702_000, r, BHYT_NLD);
        assertAmount(600_000, r, BHTN_NLD);
        assertAmount(936_000, new In(60_000_000, 0).calc(unionFeeOn()), KPCD);

        CalcResult aboveBhtn = new In(120_000_000, 0).calc();
        assertAmount(46_800_000, aboveBhtn, LUONG_DONG_BHXH_BHYT);
        assertAmount(106_200_000, aboveBhtn, LUONG_DONG_BHTN);
        assertAmount(1_062_000, aboveBhtn, BHTN_NLD);
        assertAmount(1_062_000, aboveBhtn, BHTN_NSDLD);
    }

    @Test
    void notInsured() {
        In in = new In(20_000_000, 0);
        in.insured = false;
        CalcResult r = in.calc();
        for (PayrollField f : List.of(LUONG_DONG_BHXH_BHYT, LUONG_DONG_BHTN, BHXH_NLD, BHYT_NLD, BHTN_NLD, TONG_BH_NLD,
                BHXH_NSDLD, BHYT_NSDLD, BHTN_NSDLD, TONG_BH_NSDLD, KPCD)) {
            assertAmount(0, r, f);
        }
        assertAmount(15_500_000, r, GIAM_TRU_BAN_THAN);
        assertAmount(4_500_000, r, THU_NHAP_TINH_THUE);
    }

    @Test
    void insuranceOverride() {
        In in = new In(20_000_000, 0);
        in.insuranceOverride = bd(15_000_000);
        CalcResult r = in.calc();
        assertAmount(15_000_000, r, LUONG_DONG_BHXH_BHYT);
        assertAmount(15_000_000, r, LUONG_DONG_BHTN);
        assertAmount(1_200_000, r, BHXH_NLD);
        assertAmount(20_000_000, r, LUONG_GOP);
    }

    @Test
    void allowanceMapping() {
        In in = new In(20_000_000, 0);
        in.allowances = List.of(new NamedAllowance("Phụ cấp chuyên cần", bd(500_000)),
                new NamedAllowance("PC xăng xe", bd(700_000)),
                new NamedAllowance("PHỤ CẤP TRÁCH NHIỆM", bd(300_000)),
                new NamedAllowance("Ăn trưa", bd(730_000)));
        in.manual.put(THUA_THIEU, bd(100_000));
        CalcResult r = in.calc();
        assertAmount(500_000, r, PC_CHUYEN_CAN);
        assertAmount(700_000, r, PC_XANG_XE);
        assertAmount(300_000, r, PC_TRACH_NHIEM);
        assertAmount(830_000, r, THUA_THIEU);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("Ăn trưa") && w.contains("thừa/thiếu")
                && w.contains("KHÔNG tính thuế")), r.warnings().toString());
        assertAmount(21_500_000, r, TONG_LUONG_GOP);
    }

    @Test
    void overtimeSplit() {
        In in = new In(17_600_000, 4_400_000); // lương giờ theo lương cơ bản: 17,6tr ÷ 22 ÷ 8 = 100.000
        in.overtime = List.of(new OvertimeItem(bd(4), 150), new OvertimeItem(bd(8), 200), new OvertimeItem(bd(2), 300));
        CalcResult r = in.calc();
        assertAmount(1_400_000, r, OT_CHIU_THUE); // 14 giờ × 100.000
        assertAmount(1_400_000, r, OT_KHONG_CHIU_THUE); // (4×0,5 + 8×1 + 2×2) × 100.000
        assertAmount(24_800_000, r, TONG_LUONG_GOP);
        // 24,8tr − BH 2,31tr − 15,5tr − OT không chịu thuế 1,4tr
        assertAmount(5_590_000, r, THU_NHAP_TINH_THUE);

        S withAllowance = new S();
        withAllowance.otWithAllowance = true;
        CalcResult r2 = in.calc(withAllowance.build());
        assertAmount(1_750_000, r2, OT_CHIU_THUE); // 22tr ÷ 22 ÷ 8 = 125.000
        assertAmount(1_750_000, r2, OT_KHONG_CHIU_THUE);

        S allFree = new S();
        allFree.otAllNonTaxable = true;
        CalcResult r3 = in.calc(allFree.build());
        assertAmount(0, r3, OT_CHIU_THUE);
        assertAmount(2_800_000, r3, OT_KHONG_CHIU_THUE); // (4×1,5 + 8×2 + 2×3) × 100.000
        assertAmount(24_800_000, r3, TONG_LUONG_GOP);
        assertAmount(4_190_000, r3, THU_NHAP_TINH_THUE); // 24,8tr − 2,31tr − 15,5tr − 2,8tr
    }

    @Test
    void overtimeRateBelowHundredTreatedAsHundred() {
        In in = new In(17_600_000, 0);
        in.overtime = List.of(new OvertimeItem(bd(2), 50));
        CalcResult r = in.calc();
        assertAmount(200_000, r, OT_CHIU_THUE);
        assertAmount(0, r, OT_KHONG_CHIU_THUE);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("100%")), r.warnings().toString());
    }

    @Test
    void unusedLeave() {
        In in = new In(20_000_000, 2_000_000);
        assertAmount(0, in.calc(), PHEP_CHUA_SU_DUNG);
        in.unusedLeaveDays = bd(3);
        assertAmount(3_000_000, in.calc(), PHEP_CHUA_SU_DUNG);
        S noLeave = new S();
        noLeave.payLeave = false;
        assertAmount(0, in.calc(noLeave.build()), PHEP_CHUA_SU_DUNG);
    }

    private static BigDecimal salaryNet(CalcResult r) {
        return r.amounts().get(LUONG_GOP).subtract(r.amounts().get(TONG_BH_NLD)).subtract(r.amounts().get(THUE_TNCN));
    }

    @Test
    void netSalary() {
        In in = new In(0, 0).net(15_000_000);
        in.dependants = 2;
        CalcResult plain = in.calc();
        BigDecimal net = plain.amounts().get(THUC_NHAN);
        assertTrue(net.compareTo(bd(15_000_000)) >= 0 && net.compareTo(bd(15_001_000)) < 0, "thực nhận " + net);
        assertTrue(plain.amounts().get(LUONG_CO_BAN).compareTo(bd(15_000_000)) > 0);

        in.manual.put(THUONG_KPI, bd(2_000_000));
        CalcResult bonus = in.calc();
        assertAmount(plain.amounts().get(LUONG_CO_BAN), bonus, LUONG_CO_BAN);
        BigDecimal extraTax = bonus.amounts().get(THUE_TNCN).subtract(plain.amounts().get(THUE_TNCN));
        assertTrue(bonus.amounts().get(THUC_NHAN).compareTo(bd(17_000_000).subtract(extraTax)) >= 0);

        CalcResult estimated = PayrollCalculator.calculate(in.build(), CalcSettings.defaults(), RULES, true);
        assertFalse(estimated.warnings().isEmpty());
        assertTrue(plain.warnings().isEmpty(), plain.warnings().toString());
    }

    @Test
    void netSalaryWithProgressiveTaxAndBonus() {
        In in = new In(0, 0).net(40_000_000);
        in.dependants = 2;
        CalcResult plain = in.calc();
        BigDecimal net = plain.amounts().get(THUC_NHAN);
        assertTrue(net.compareTo(bd(40_000_000)) >= 0 && net.compareTo(bd(40_001_000)) < 0, "thực nhận " + net);
        assertTrue(plain.amounts().get(THUE_TNCN).signum() > 0);

        in.manual.put(THUONG_KPI, bd(5_000_000));
        CalcResult bonus = in.calc();
        assertAmount(plain.amounts().get(LUONG_CO_BAN), bonus, LUONG_CO_BAN);
        BigDecimal extraTax = bonus.amounts().get(THUE_TNCN).subtract(plain.amounts().get(THUE_TNCN));
        assertTrue(extraTax.signum() > 0, "thưởng chịu thuế bình thường");
        assertTrue(bonus.amounts().get(THUC_NHAN).compareTo(bd(45_000_000).subtract(extraTax)) >= 0);
    }

    @Test
    void netSalaryProratedByDays() {
        // Thiếu công: phần thực nhận từ lương ≥ NET × ngày công thực tế ÷ chuẩn, Gross tìm lại theo tháng.
        In in = new In(0, 0).net(30_000_000).work("11");
        CalcResult r = in.calc();
        BigDecimal target = bd(15_000_000);
        assertTrue(salaryNet(r).compareTo(target) >= 0, "thực nhận từ lương " + salaryNet(r));
        assertTrue(salaryNet(r).compareTo(target.add(bd(1_000))) < 0, "thực nhận từ lương " + salaryNet(r));
    }

    @Test
    void netWithOverriddenGrossPayDoesNotConvert() {
        for (long gop : new long[] {5_000_000, 30_000_000}) {
            In in = new In(0, 0).net(15_000_000);
            in.overrides.put(LUONG_GOP, bd(gop));
            CalcResult r = in.calc();
            assertAmount(gop, r, LUONG_GOP);
            assertAmount(15_000_000, r, LUONG_CO_BAN);
            assertTrue(r.warnings().stream().anyMatch(w -> w.contains("không quy đổi")), r.warnings().toString());
        }
    }

    @Test
    void netUnreachableStopsWithWarning() {
        S s = new S();
        s.bhxhEmployee = bd("1.5"); // 1/22 công, BH trên cả tháng lớn hơn lương: không Gross nào trong giới hạn đạt NET
        CalcResult r = new In(0, 0).net(15_000_000).work("1").calc(s.build());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("Không quy đổi được")), r.warnings().toString());
    }

    @Test
    void netWithZeroActualDaysWarns() {
        CalcResult r = new In(0, 0).net(15_000_000).work("0").calc();
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("Ngày công thực tế bằng 0")), r.warnings().toString());
        assertAmount(0, r, LUONG_GOP);
    }

    @Test
    void grossThenNetInSameMonth() {
        In gross = new In(0, 0);
        gross.salary = List.of(new SalarySegment(bd(20_000_000), BigDecimal.ZERO, false, bd("0.5")),
                new SalarySegment(BigDecimal.ZERO, BigDecimal.ZERO, false, bd("0.5")));
        BigDecimal grossHalf = salaryNet(gross.calc());

        In mixed = new In(0, 0);
        mixed.salary = List.of(new SalarySegment(bd(20_000_000), BigDecimal.ZERO, false, bd("0.5")),
                new SalarySegment(bd(15_000_000), BigDecimal.ZERO, true, bd("0.5")));
        BigDecimal netHalf = salaryNet(mixed.calc()).subtract(grossHalf);
        assertTrue(netHalf.compareTo(bd(7_500_000)) >= 0, "phần NET thực nhận " + netHalf);
        assertTrue(netHalf.compareTo(bd(7_501_000)) < 0, "phần NET thực nhận " + netHalf);
    }

    @Test
    void internWithholding() {
        In in = new In(3_000_000, 0);
        in.kind = PersonKind.THUC_TAP;
        in.insured = false;
        in.dependants = 1;
        CalcResult r = in.calc();
        assertAmount(300_000, r, THUE_TNCN);
        assertAmount(3_000_000, r, THU_NHAP_TINH_THUE);
        assertAmount(0, r, GIAM_TRU_BAN_THAN);
        assertAmount(0, r, GIAM_TRU_NPT);
        assertAmount(0, r, TONG_BH_NLD);
        assertAmount(0, r, KPCD);
        assertAmount(2_700_000, r, THUC_NHAN);

        In low = new In(1_900_000, 0);
        low.kind = PersonKind.DICH_VU;
        low.insured = false;
        assertAmount(0, low.calc(), THUE_TNCN);

        in.commitment08 = true;
        assertAmount(0, in.calc(), THUE_TNCN);

        // Thực tập dù cờ insured bật vẫn không đóng BH.
        In insuredIntern = new In(3_000_000, 0);
        insuredIntern.kind = PersonKind.THUC_TAP;
        assertAmount(0, insuredIntern.calc(), TONG_BH_NLD);
    }

    @Test
    void overridesRecompute() {
        In in = new In(22_000_000, 0);
        in.overrides.put(NGAY_CONG_THUC_TE, bd(20));
        in.overrides.put(THUC_NHAN, bd(1));
        CalcResult r = in.calc();
        assertAmount(20, r, NGAY_CONG_THUC_TE);
        assertAmount(20_000_000, r, LUONG_GOP);
        assertAmount(2_310_000, r, TONG_BH_NLD);
        assertAmount(2_190_000, r, THU_NHAP_TINH_THUE);
        assertAmount(109_500, r, THUE_TNCN);
        assertAmount(17_580_500, r, THUC_NHAN);
    }

    @Test
    void overrideOfMiddleColumnFlowsDownstream() {
        In in = new In(22_000_000, 0);
        in.overrides.put(LUONG_GOP, bd(10_000_000));
        in.overrides.put(TONG_LUONG_GOP, bd(1));
        CalcResult r = in.calc();
        assertAmount(10_000_000, r, LUONG_GOP);
        assertAmount(10_000_000, r, TONG_LUONG_GOP);
    }

    @Test
    void settingsChange() {
        In in = new In(20_000_000, 1_000_000);
        assertAmount(0, in.calc(), KPCD);
        assertAmount(420_000, in.calc(unionFeeOn()), KPCD);
        S s = new S();
        s.bhxhCompany = bd("0.17");
        assertAmount(3_570_000, in.calc(s.build()), BHXH_NSDLD);
    }

    @Test
    void sickDaysCountOnlyWhenSelected() {
        In in = new In(22_000_000, 0).work("20");
        in.days.put(DayKind.SICK, bd(2));
        assertAmount(20, in.calc(), NGAY_CONG_THUC_TE);
        S withSick = new S();
        withSick.dayKinds = java.util.EnumSet.allOf(DayKind.class);
        assertAmount(22, in.calc(withSick.build()), NGAY_CONG_THUC_TE);
    }

    @Test
    void roundingHalfUp() {
        In in = new In(1_000_001, 0).work("1");
        in.standardDays = bd(2);
        in.insuranceOverride = bd(10_000_100);
        CalcResult r = in.calc();
        assertAmount(500_001, r, LUONG_GOP); // 500.000,5
        assertAmount(150_002, r, BHYT_NLD); // 150.001,5
        assertAmount(1_750_018, r, BHXH_NSDLD); // 1.750.017,5
        assertAmount(300_003, r, BHYT_NSDLD);
    }
}

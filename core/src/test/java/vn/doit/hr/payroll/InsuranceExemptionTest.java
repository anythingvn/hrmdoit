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

/** Người không thuộc diện đóng BHXH bắt buộc (lương hưu, cộng tác viên…): Gross không cộng BH, kiểm tra bảng lương không cảnh báo sai. */
class InsuranceExemptionTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private final PayrollRules rules = PayrollRules.forPeriod(JUNE).orElseThrow();

    @Test
    void uninsuredNetHasNoInsurance() {
        BigDecimal gross = BigDecimal.valueOf(20_000_000);
        BigDecimal taxable = gross.subtract(rules.personalDeduction());
        assertEquals(0, NetGrossConverter.netFromGross(gross, 0, rules, false).compareTo(gross.subtract(rules.monthlyPit(taxable))));
        BigDecimal insuredGross = NetGrossConverter.suggestGross(BigDecimal.valueOf(18_000_000), 0, JUNE, RuleBook.defaults(), true).orElseThrow();
        BigDecimal uninsuredGross = NetGrossConverter.suggestGross(BigDecimal.valueOf(18_000_000), 0, JUNE, RuleBook.defaults(), false).orElseThrow();
        assertTrue(uninsuredGross.compareTo(insuredGross) < 0, uninsuredGross + " < " + insuredGross);
        assertEquals(0, NetGrossConverter.suggestGross(BigDecimal.valueOf(18_000_000), 0, JUNE).orElseThrow().compareTo(insuredGross));
        assertTrue(NetGrossConverter.netFromGross(uninsuredGross, 0, rules, false).compareTo(BigDecimal.valueOf(18_000_000)) >= 0);
    }

    @Test
    void exemptPersonWithoutInsuranceIsNotWarned() {
        List<Issue> issues = validate(line(false), Optional.of("Đang hưởng lương hưu"));
        assertTrue(issues.stream().noneMatch(i -> i.field() == TONG_BH_NLD), issues.toString());
        List<Issue> normal = validate(line(false), Optional.empty());
        assertTrue(normal.stream().anyMatch(i -> i.field() == TONG_BH_NLD && i.message().contains("không đóng")), normal.toString());
    }

    @Test
    void exemptPersonWithInsuranceIsWarned() {
        List<Issue> issues = validate(line(true), Optional.of("Đang hưởng lương hưu"));
        assertTrue(
            issues.stream().anyMatch(i -> i.field() == TONG_BH_NLD && i.message().contains("không thuộc diện đóng BHXH bắt buộc (Đang hưởng lương hưu)")),
            issues.toString()
        );
    }

    private List<Issue> validate(PayrollLine line, Optional<String> exemption) {
        Map<PayrollField, Integer> cols = new EnumMap<>(PayrollField.class);
        for (PayrollField f : PayrollField.values()) {
            cols.put(f, f.ordinal());
        }
        ParsedPayroll p = new ParsedPayroll(Optional.of(JUNE), cols, List.of(line), List.of());
        EmployeeDirectory directory = new EmployeeDirectory() {
            @Override
            public Optional<String> findNameByCode(String code) {
                return Optional.of("Nhân viên X");
            }

            @Override
            public Optional<String> insuranceExemption(String code) {
                return exemption;
            }
        };
        return new PayrollValidator().validate(p, JUNE, directory);
    }

    private static PayrollLine line(boolean insured) {
        Map<PayrollField, BigDecimal> a = new EnumMap<>(PayrollField.class);
        long gross = 20_000_000;
        long bh = insured ? 2_100_000 : 0;
        a.put(LUONG_CO_BAN, BigDecimal.valueOf(gross));
        a.put(LUONG_GOP, BigDecimal.valueOf(gross));
        a.put(TONG_LUONG_GOP, BigDecimal.valueOf(gross));
        if (insured) {
            a.put(LUONG_DONG_BHXH_BHYT, BigDecimal.valueOf(gross));
            a.put(LUONG_DONG_BHTN, BigDecimal.valueOf(gross));
            a.put(BHXH_NLD, BigDecimal.valueOf(1_600_000));
            a.put(BHYT_NLD, BigDecimal.valueOf(300_000));
            a.put(BHTN_NLD, BigDecimal.valueOf(200_000));
            a.put(TONG_BH_NLD, BigDecimal.valueOf(bh));
        }
        a.put(THUC_NHAN, BigDecimal.valueOf(gross - bh));
        a.put(TONG_CHI_PHI, BigDecimal.valueOf(gross));
        return new PayrollLine(10, "DOIT9998", "Chuyên gia", "Nhân viên X", LocalDate.of(2025, 1, 1), a);
    }
}

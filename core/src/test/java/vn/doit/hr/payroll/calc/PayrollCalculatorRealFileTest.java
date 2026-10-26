package vn.doit.hr.payroll.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static vn.doit.hr.payroll.PayrollField.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollRules;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.PoiSheetGrid;
import vn.doit.hr.payroll.RuleBook;
import vn.doit.hr.payroll.calc.CalcInput.NamedAllowance;
import vn.doit.hr.payroll.calc.CalcInput.PersonKind;
import vn.doit.hr.payroll.calc.CalcInput.SalarySegment;
import vn.doit.hr.payroll.calc.CalcSettings.DayKind;

/**
 * Đối chiếu bộ tính với file bảng lương thật (đọc lúc chạy, không đưa số liệu vào repo). Bỏ qua khi không có file
 * (đặt cạnh thư mục project, hoặc -Dpayroll.file=...). Chỉ in mã dòng, cột, số trong file và số tính ra – không in họ tên.
 *
 * <p>Heuristic dựng đầu vào từ chính dòng trong file:
 * <ul>
 *   <li>Lương cơ bản, PC đóng BH, ngày công chuẩn, ngày công thực tế (làm ngày làm việc), 6 khoản nhập tay, số người phụ
 *       thuộc lấy nguyên cột.</li>
 *   <li>Mức đóng BHXH/BHYT &gt; 0 → {@code insuranceSalaryOverride}; {@code insured} = BHXH NLĐ &gt; 0.</li>
 *   <li>3 cột phụ cấp → phụ cấp có tên khớp từ khóa mặc định.</li>
 *   <li>Tiền làm thêm giờ: file không có số giờ, nên hai cột OT đưa vào dạng sửa tay (override).</li>
 *   <li>Loại người: có giảm trừ bản thân → HĐLĐ; mã bắt đầu "TT" → thực tập; còn lại (không mã, không BH) → HĐ dịch vụ.</li>
 *   <li>Dòng không có lương cơ bản nhưng có lương gộp (HĐ dịch vụ/thực tập nhập thẳng số) → lương gộp đưa vào override.</li>
 *   <li>File không tính kinh phí công đoàn (cột trống) → chạy với thông số công đoàn tắt.</li>
 * </ul>
 */
class PayrollCalculatorRealFileTest {

    private static final Path REAL_FILE = Path.of(System.getProperty("payroll.file",
            "../../DO it - Bang luong thang 06_2026 them luon.xlsx"));
    private static final YearMonth PERIOD = YearMonth.of(2026, 6);
    private static final BigDecimal TOLERANCE = BigDecimal.ONE;

    @Test
    void reproducesRealPayrollFile() throws Exception {
        assumeTrue(Files.exists(REAL_FILE), "Không có file bảng lương gốc – bỏ qua");
        ParsedPayroll p;
        try (InputStream in = Files.newInputStream(REAL_FILE)) {
            p = new PayrollSheetParser().parse(PoiSheetGrid.read(in));
        }
        PayrollRules rules = RuleBook.defaults().forPeriod(PERIOD).orElseThrow();
        CalcSettings s = noUnionFee(CalcSettings.defaults());

        List<String> diffs = new ArrayList<>();
        for (PayrollLine line : p.lines()) {
            CalcResult r = PayrollCalculator.calculate(inputOf(line), s, rules, false);
            for (PayrollField f : PayrollField.values()) {
                BigDecimal expected = line.get(f);
                BigDecimal actual = r.amounts().get(f);
                if (expected.subtract(actual).abs().compareTo(TOLERANCE) > 0) {
                    diffs.add(String.format(Locale.ROOT, "dòng %d %-10s %-22s file=%s tính=%s", line.excelRow(),
                            line.hasCode() ? line.employeeCode() : "(không mã)", f, expected.toPlainString(),
                            actual.toPlainString()));
                }
            }
        }
        System.out.println("Đối chiếu " + p.lines().size() + " dòng với kỳ " + PERIOD + ": " + diffs.size() + " ô lệch");
        diffs.forEach(System.out::println);
        assertEquals(14, p.lines().size());
        assertEquals(List.of(), diffs, "Các ô lệch giữa file và bộ tính");
    }

    static CalcInput inputOf(PayrollLine l) {
        boolean insured = l.get(BHXH_NLD).signum() > 0;
        PersonKind kind;
        if (l.get(GIAM_TRU_BAN_THAN).signum() > 0) {
            kind = PersonKind.HDLD;
        } else if (l.hasCode() && l.employeeCode().toUpperCase(Locale.ROOT).startsWith("TT")) {
            kind = PersonKind.THUC_TAP;
        } else {
            kind = PersonKind.DICH_VU;
        }
        Map<DayKind, BigDecimal> days = new EnumMap<>(DayKind.class);
        days.put(DayKind.WORK, l.get(NGAY_CONG_THUC_TE));
        List<NamedAllowance> allowances = new ArrayList<>();
        addAllowance(allowances, "Phụ cấp chuyên cần", l.get(PC_CHUYEN_CAN));
        addAllowance(allowances, "Phụ cấp xăng xe", l.get(PC_XANG_XE));
        addAllowance(allowances, "Phụ cấp trách nhiệm", l.get(PC_TRACH_NHIEM));

        Map<PayrollField, BigDecimal> manual = new EnumMap<>(PayrollField.class);
        for (PayrollField f : List.of(THUONG_DU_AN, THUONG_KPI, TRO_CAP_THOI_VIEC, THUA_THIEU, QUYET_TOAN_THUE, GIAM_TRU_KHAC)) {
            manual.put(f, l.get(f));
        }
        Map<PayrollField, BigDecimal> overrides = new EnumMap<>(PayrollField.class);
        if (l.get(OT_CHIU_THUE).signum() != 0 || l.get(OT_KHONG_CHIU_THUE).signum() != 0) {
            overrides.put(OT_CHIU_THUE, l.get(OT_CHIU_THUE));
            overrides.put(OT_KHONG_CHIU_THUE, l.get(OT_KHONG_CHIU_THUE));
        }
        if (l.get(LUONG_CO_BAN).signum() == 0 && l.get(LUONG_GOP).signum() != 0) {
            overrides.put(LUONG_GOP, l.get(LUONG_GOP));
        }
        BigDecimal insuranceBase = l.get(LUONG_DONG_BHXH_BHYT).signum() > 0 ? l.get(LUONG_DONG_BHXH_BHYT) : null;
        List<SalarySegment> salary = List.of(new SalarySegment(l.get(LUONG_CO_BAN), l.get(PHU_CAP_DONG_BH), false, BigDecimal.ONE));
        return new CalcInput(kind, insured, salary, insuranceBase, days, l.get(NGAY_CONG_CHUAN), allowances, List.of(), null,
                l.get(SO_NGUOI_PHU_THUOC).intValue(), false, manual, overrides);
    }

    private static void addAllowance(List<NamedAllowance> list, String name, BigDecimal amount) {
        if (amount.signum() != 0) {
            list.add(new NamedAllowance(name, amount));
        }
    }

    private static CalcSettings noUnionFee(CalcSettings d) {
        return new CalcSettings(d.bhxhEmployee(), d.bhytEmployee(), d.bhtnEmployee(), d.bhxhCompany(), d.bhytCompany(),
                d.bhtnCompany(), false, d.unionFeeRate(), d.hoursPerDay(), d.overtimeBaseIncludesInsuredAllowance(),
                d.actualDayKinds(), d.allowanceKeywords(), d.withholdingThreshold(), d.withholdingRate(),
                d.payUnusedLeaveOnTermination());
    }
}

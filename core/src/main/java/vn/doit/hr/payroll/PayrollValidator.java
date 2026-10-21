package vn.doit.hr.payroll;

import static vn.doit.hr.payroll.PayrollField.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kiểm tra nghiệp vụ trên bảng lương đã đọc.
 *
 * <p>Nhóm kiểm tra:
 * <ol>
 *   <li><b>Liên kết nhân viên</b> (ERROR): thiếu mã, trùng mã trong file, mã không có trong hệ thống.</li>
 *   <li><b>Khớp nội bộ</b> (WARNING): các cột tổng có đúng bằng tổng các cột thành phần không – bắt lỗi
 *       kế toán gõ đè số lên ô công thức.</li>
 *   <li><b>Đối chiếu theo luật</b> (WARNING): giảm trừ gia cảnh, trần đóng BH, tỉ lệ BH người lao động,
 *       thuế TNCN lũy tiến. Chỉ áp dụng cho dòng có giảm trừ bản thân &gt; 0 (người ký HĐLĐ, cư trú).</li>
 * </ol>
 * Không có kiểm tra nào sửa số liệu; hệ thống chỉ báo để người duyệt quyết định.
 */
public class PayrollValidator {

    private static final BigDecimal TOLERANCE = BigDecimal.ONE;
    static final BigDecimal BHXH_RATE = new BigDecimal("0.08");
    static final BigDecimal BHYT_RATE = new BigDecimal("0.015");
    static final BigDecimal BHTN_RATE = new BigDecimal("0.01");

    private final RuleBook ruleBook;

    /** Đối chiếu theo {@link RuleBook#defaults()}. */
    public PayrollValidator() {
        this(RuleBook.defaults());
    }

    public PayrollValidator(RuleBook ruleBook) {
        this.ruleBook = ruleBook;
    }

    public List<Issue> validate(ParsedPayroll payroll, YearMonth period, EmployeeDirectory directory) {
        List<Issue> issues = new ArrayList<>();
        payroll.periodInFile()
                .filter(p -> !p.equals(period))
                .ifPresent(p -> issues.add(Issue.fileWarning(
                        "Tiêu đề file ghi kỳ " + fmt(p) + " nhưng đang import cho kỳ " + fmt(period) + ".")));

        Map<String, List<PayrollLine>> byCode = new HashMap<>();
        for (PayrollLine line : payroll.lines()) {
            if (line.hasCode()) {
                byCode.computeIfAbsent(line.employeeCode(), k -> new ArrayList<>()).add(line);
            }
        }

        Optional<PayrollRules> rules = ruleBook.forPeriod(period);
        if (rules.isEmpty()) {
            issues.add(Issue.fileWarning("Chưa khai báo mức luật định cho kỳ " + fmt(period) + " – bỏ qua đối chiếu theo luật."));
        }

        for (PayrollLine line : payroll.lines()) {
            checkIdentity(line, byCode, directory, issues);
            checkTotals(line, payroll, issues);
            rules.ifPresent(r -> checkStatutory(line, r, issues, line.hasCode() ? directory.insuranceExemption(line.employeeCode()) : Optional.empty()));
        }
        return issues;
    }

    private static void checkIdentity(PayrollLine line, Map<String, List<PayrollLine>> byCode,
                                      EmployeeDirectory directory, List<Issue> issues) {
        if (!line.hasCode()) {
            issues.add(error(line, null, "Thiếu mã nhân viên (" + line.fullName() + ", " + line.position()
                    + ") – không thể gắn với hồ sơ. Cấp mã cho cả thực tập sinh/HĐ dịch vụ, hoặc tách ra file riêng."));
            return;
        }
        List<PayrollLine> same = byCode.get(line.employeeCode());
        if (same.size() > 1) {
            List<String> others = same.stream().filter(l -> l != line)
                    .map(l -> "dòng " + l.excelRow() + " – " + l.fullName()).toList();
            issues.add(error(line, null, "Mã bị trùng với " + String.join(", ", others) + "."));
        }
        Optional<String> known = directory.findNameByCode(line.employeeCode());
        if (known.isEmpty()) {
            issues.add(error(line, null, "Mã không có trong hệ thống nhân sự."));
        } else if (!sameName(known.get(), line.fullName())) {
            issues.add(warning(line, null, "Họ tên trong file \"" + line.fullName()
                    + "\" khác hồ sơ \"" + known.get() + "\"."));
        }
    }

    private static void checkTotals(PayrollLine l, ParsedPayroll p, List<Issue> issues) {
        expectSum(l, p, issues, TONG_LUONG_GOP, sum(l, LUONG_GOP, THUONG_DU_AN, PC_CHUYEN_CAN, PC_XANG_XE,
                PC_TRACH_NHIEM, THUONG_KPI, OT_CHIU_THUE, OT_KHONG_CHIU_THUE, TRO_CAP_THOI_VIEC, PHEP_CHUA_SU_DUNG));
        expectSum(l, p, issues, TONG_BH_NLD, sum(l, BHXH_NLD, BHYT_NLD, BHTN_NLD));
        expectSum(l, p, issues, TONG_BH_NSDLD, sum(l, BHXH_NSDLD, BHYT_NSDLD, BHTN_NSDLD));
        expectSum(l, p, issues, THUC_NHAN, l.get(TONG_LUONG_GOP).subtract(l.get(TONG_BH_NLD))
                .subtract(l.get(THUE_TNCN)).subtract(l.get(QUYET_TOAN_THUE))
                .add(l.get(THUA_THIEU)).subtract(l.get(GIAM_TRU_KHAC)));
        // Công thức trong file mẫu: Tổng chi phí = Tổng lương gộp + BH NSDLĐ + KPCĐ + Thừa/thiếu
        expectSum(l, p, issues, TONG_CHI_PHI, sum(l, TONG_LUONG_GOP, TONG_BH_NSDLD, KPCD, THUA_THIEU));
    }

    private static void checkStatutory(PayrollLine l, PayrollRules r, List<Issue> issues, Optional<String> exemption) {
        BigDecimal contractBase = l.get(LUONG_CO_BAN).add(l.get(PHU_CAP_DONG_BH));
        boolean insured = l.get(LUONG_DONG_BHXH_BHYT).signum() > 0 || l.get(TONG_BH_NLD).signum() > 0;

        if (exemption.isPresent()) {
            // Hồ sơ ghi không thuộc diện: không đóng là đúng; có đóng thì hoặc hồ sơ sai, hoặc đang trừ nhầm tiền BH.
            if (insured) {
                issues.add(warning(l, TONG_BH_NLD, "Hồ sơ ghi không thuộc diện đóng BHXH bắt buộc (" + exemption.get()
                        + ") nhưng bảng lương có đóng BH – kiểm tra lại."));
            }
        } else if (!insured && l.contractStartDate() != null && l.hasCode()) {
            issues.add(warning(l, TONG_BH_NLD, "Có ngày ký HĐLĐ nhưng không đóng BHXH/BHYT/BHTN. "
                    + "HĐLĐ từ đủ 1 tháng trở lên thuộc diện BHXH bắt buộc – kiểm tra lại."));
        }
        if (insured) {
            BigDecimal expectedBhxhBase = contractBase.min(r.socialInsuranceCap());
            BigDecimal expectedBhtnBase = contractBase.min(r.unemploymentInsuranceCap());
            if (differs(l.get(LUONG_DONG_BHXH_BHYT), expectedBhxhBase)) {
                issues.add(warning(l, LUONG_DONG_BHXH_BHYT, "Lương đóng BHXH/BHYT là " + money(l.get(LUONG_DONG_BHXH_BHYT))
                        + ", theo luật phải là " + money(expectedBhxhBase) + " (trần " + money(r.socialInsuranceCap()) + ")."));
            }
            if (differs(l.get(LUONG_DONG_BHTN), expectedBhtnBase)) {
                issues.add(warning(l, LUONG_DONG_BHTN, "Lương đóng BHTN là " + money(l.get(LUONG_DONG_BHTN))
                        + ", theo luật phải là " + money(expectedBhtnBase) + " (trần " + money(r.unemploymentInsuranceCap()) + ")."));
            }
            checkRate(l, issues, BHXH_NLD, LUONG_DONG_BHXH_BHYT, BHXH_RATE);
            checkRate(l, issues, BHYT_NLD, LUONG_DONG_BHXH_BHYT, BHYT_RATE);
            checkRate(l, issues, BHTN_NLD, LUONG_DONG_BHTN, BHTN_RATE);
        }

        if (l.get(GIAM_TRU_BAN_THAN).signum() == 0) {
            return; // không phải dòng tính thuế lũy tiến (thực tập, HĐ dịch vụ...)
        }
        if (differs(l.get(GIAM_TRU_BAN_THAN), r.personalDeduction())) {
            issues.add(warning(l, GIAM_TRU_BAN_THAN, "Giảm trừ bản thân " + money(l.get(GIAM_TRU_BAN_THAN))
                    + " khác mức luật định " + money(r.personalDeduction()) + "."));
        }
        BigDecimal expectedDep = l.get(SO_NGUOI_PHU_THUOC).multiply(r.dependantDeduction());
        if (differs(l.get(GIAM_TRU_NPT), expectedDep)) {
            issues.add(warning(l, GIAM_TRU_NPT, "Giảm trừ người phụ thuộc " + money(l.get(GIAM_TRU_NPT))
                    + ", theo luật phải là " + money(expectedDep) + " (" + l.get(SO_NGUOI_PHU_THUOC).stripTrailingZeros().toPlainString()
                    + " × " + money(r.dependantDeduction()) + ")."));
        }
        BigDecimal expectedTaxable = l.get(TONG_LUONG_GOP).subtract(l.get(TONG_BH_NLD)).subtract(l.get(GIAM_TRU_BAN_THAN))
                .subtract(l.get(GIAM_TRU_NPT)).subtract(l.get(OT_KHONG_CHIU_THUE)).max(BigDecimal.ZERO);
        if (differs(l.get(THU_NHAP_TINH_THUE), expectedTaxable)) {
            issues.add(warning(l, THU_NHAP_TINH_THUE, "Thu nhập tính thuế " + money(l.get(THU_NHAP_TINH_THUE))
                    + ", tính lại được " + money(expectedTaxable) + "."));
        }
        BigDecimal expectedTax = r.monthlyPit(l.get(THU_NHAP_TINH_THUE));
        if (differs(l.get(THUE_TNCN), expectedTax)) {
            issues.add(warning(l, THUE_TNCN, "Thuế TNCN " + money(l.get(THUE_TNCN))
                    + ", theo biểu lũy tiến hiện hành là " + money(expectedTax) + "."));
        }
    }

    private static void checkRate(PayrollLine l, List<Issue> issues, PayrollField amount, PayrollField base, BigDecimal rate) {
        BigDecimal expected = l.get(base).multiply(rate).setScale(0, RoundingMode.HALF_UP);
        if (differs(l.get(amount), expected)) {
            issues.add(warning(l, amount, money(l.get(amount)) + " khác " + rate.movePointRight(2).stripTrailingZeros().toPlainString()
                    + "% × " + money(l.get(base)) + " = " + money(expected) + "."));
        }
    }

    private static void expectSum(PayrollLine l, ParsedPayroll p, List<Issue> issues, PayrollField total, BigDecimal expected) {
        if (!p.columns().containsKey(total)) {
            return;
        }
        if (differs(l.get(total), expected)) {
            issues.add(warning(l, total, "Giá trị " + money(l.get(total)) + " không khớp tổng các cột thành phần ("
                    + money(expected) + "). Có thể ô công thức đã bị gõ đè."));
        }
    }

    private static BigDecimal sum(PayrollLine l, PayrollField... fields) {
        BigDecimal s = BigDecimal.ZERO;
        for (PayrollField f : fields) {
            s = s.add(l.get(f));
        }
        return s;
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCE) > 0;
    }

    private static boolean sameName(String a, String b) {
        return norm(a).equals(norm(b));
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ").toLowerCase();
    }

    static String money(BigDecimal v) {
        return String.format("%,d", v.setScale(0, RoundingMode.HALF_UP).longValueExact()).replace(',', '.');
    }

    private static String fmt(YearMonth p) {
        return String.format("%02d/%d", p.getMonthValue(), p.getYear());
    }

    private static Issue error(PayrollLine l, PayrollField f, String msg) {
        return new Issue(Issue.Severity.ERROR, l.excelRow(), l.employeeCode(), f, msg);
    }

    private static Issue warning(PayrollLine l, PayrollField f, String msg) {
        return new Issue(Issue.Severity.WARNING, l.excelRow(), l.employeeCode(), f, msg);
    }
}

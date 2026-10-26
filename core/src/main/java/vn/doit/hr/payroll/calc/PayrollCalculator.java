package vn.doit.hr.payroll.calc;

import static vn.doit.hr.payroll.PayrollField.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollRules;
import vn.doit.hr.payroll.calc.CalcInput.NamedAllowance;
import vn.doit.hr.payroll.calc.CalcInput.OvertimeItem;
import vn.doit.hr.payroll.calc.CalcInput.PersonKind;
import vn.doit.hr.payroll.calc.CalcInput.SalarySegment;
import vn.doit.hr.payroll.calc.CalcSettings.DayKind;

/**
 * Bộ tính lương: từ đầu vào của một người, thông số tính lương và mức luật của kỳ ra đủ 36 khoản của mẫu DOIT (spec §2.2–2.4).
 *
 * <p>Tính lần lượt theo thứ tự cột phụ thuộc. Mỗi cột: tính từ đầu vào → nếu có trong {@code overrides} thì dùng số sửa tay
 * (trừ các cột tổng, luôn tính) → các cột sau dùng số đó. Mỗi khoản tiền làm tròn đến đồng ({@code HALF_UP}) ngay khi tính.
 *
 * <p>Lương NET: tìm Gross nhỏ nhất (số nguyên đồng) sao cho phần thực nhận từ lương (lương gộp − BH − thuế, chưa tính thưởng,
 * phụ cấp, OT, các khoản nhập tay) ≥ NET × ngày công thực tế ÷ chuẩn. Phụ cấp đóng BH của đoạn NET được nhận nguyên. Nhiều
 * đoạn NET trong tháng dùng cùng một khoản cộng thêm cho mỗi đoạn.
 */
public final class PayrollCalculator {

    /** Các cột tổng: luôn tính, bỏ qua sửa tay. */
    static final Set<PayrollField> TOTALS = EnumSet.of(TONG_LUONG_GOP, TONG_BH_NLD, TONG_BH_NSDLD, THUC_NHAN, TONG_CHI_PHI);

    /** Các khoản Kế toán nhập. */
    static final List<PayrollField> MANUAL = List.of(THUONG_DU_AN, THUONG_KPI, TRO_CAP_THOI_VIEC, THUA_THIEU, QUYET_TOAN_THUE,
            GIAM_TRU_KHAC);

    /** Cột không phải tiền (không làm tròn đến đồng). */
    private static final Set<PayrollField> NON_MONEY = EnumSet.of(NGAY_CONG_CHUAN, NGAY_CONG_THUC_TE, SO_NGUOI_PHU_THUOC);

    /** Khoản ngoài lương: bằng 0 khi tính "phần thực nhận từ lương" để quy đổi NET. */
    private static final Set<PayrollField> NOT_SALARY = EnumSet.of(THUONG_DU_AN, PC_CHUYEN_CAN, PC_XANG_XE, PC_TRACH_NHIEM,
            THUONG_KPI, OT_CHIU_THUE, OT_KHONG_CHIU_THUE, TRO_CAP_THOI_VIEC, PHEP_CHUA_SU_DUNG, QUYET_TOAN_THUE, THUA_THIEU,
            GIAM_TRU_KHAC);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 10;

    private PayrollCalculator() {
    }

    public static CalcResult calculate(CalcInput in, CalcSettings s, PayrollRules rules, boolean rulesEstimated) {
        List<String> warnings = new ArrayList<>();
        if (in.salary().isEmpty()) {
            warnings.add("Chưa có mức lương hiệu lực trong kỳ.");
        }
        BigDecimal grossUp = BigDecimal.ZERO;
        boolean net = in.salary().stream().anyMatch(SalarySegment::net);
        if (net && !in.overrides().containsKey(LUONG_CO_BAN)) {
            grossUp = netGrossUp(in, s, rules);
            if (rulesEstimated) {
                warnings.add("Kỳ lương nằm sau mốc luật đã biết: quy đổi lương NET đang dùng mức luật ước tính.");
            }
        }
        Map<PayrollField, BigDecimal> amounts = new Run(in, s, rules, grossUp, false, warnings).compute();
        return new CalcResult(amounts, warnings);
    }

    /** Khoản cộng thêm (nguyên đồng) vào lương NET của mỗi đoạn NET để ra Gross nhỏ nhất đạt NET theo ngày công. */
    private static BigDecimal netGrossUp(CalcInput in, CalcSettings s, PayrollRules rules) {
        Map<PayrollField, BigDecimal> probe = salaryOnly(in, s, rules, BigDecimal.ZERO);
        BigDecimal std = probe.get(NGAY_CONG_CHUAN);
        if (std.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal agreed = BigDecimal.ZERO;
        for (SalarySegment seg : in.salary()) {
            if (seg.net()) {
                agreed = agreed.add(nz(seg.baseSalary()).add(nz(seg.insuredAllowance())).multiply(nz(seg.workingDayShare())));
            }
        }
        BigDecimal target = agreed.multiply(probe.get(NGAY_CONG_THUC_TE)).divide(std, SCALE, RoundingMode.HALF_UP);
        if (target.signum() <= 0 || salaryNet(probe).compareTo(target) >= 0) {
            return BigDecimal.ZERO;
        }
        long lo = 0;
        long hi = Math.max(1L, agreed.setScale(0, RoundingMode.CEILING).longValueExact());
        for (int i = 0; i < 62 && salaryNet(salaryOnly(in, s, rules, BigDecimal.valueOf(hi))).compareTo(target) < 0; i++) {
            lo = hi;
            hi = Math.multiplyExact(hi, 2L);
        }
        while (lo < hi) {
            long mid = lo + (hi - lo) / 2;
            if (salaryNet(salaryOnly(in, s, rules, BigDecimal.valueOf(mid))).compareTo(target) >= 0) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return BigDecimal.valueOf(lo);
    }

    private static Map<PayrollField, BigDecimal> salaryOnly(CalcInput in, CalcSettings s, PayrollRules rules, BigDecimal grossUp) {
        return new Run(in, s, rules, grossUp, true, new ArrayList<>()).compute();
    }

    private static BigDecimal salaryNet(Map<PayrollField, BigDecimal> a) {
        return a.get(LUONG_GOP).subtract(a.get(TONG_BH_NLD)).subtract(a.get(THUE_TNCN));
    }

    /** Một lượt tính 36 cột. */
    private static final class Run {
        private final CalcInput in;
        private final CalcSettings s;
        private final PayrollRules rules;
        private final BigDecimal grossUp;
        private final boolean salaryOnly;
        private final List<String> warnings;
        private final Map<PayrollField, BigDecimal> a = new EnumMap<>(PayrollField.class);

        Run(CalcInput in, CalcSettings s, PayrollRules rules, BigDecimal grossUp, boolean salaryOnly, List<String> warnings) {
            this.in = in;
            this.s = s;
            this.rules = rules;
            this.grossUp = grossUp;
            this.salaryOnly = salaryOnly;
            this.warnings = warnings;
        }

        Map<PayrollField, BigDecimal> compute() {
            boolean contract = in.kind() == PersonKind.HDLD;
            boolean covered = contract && in.insured();

            // Mức lương: bình quân các đoạn theo tỷ lệ ngày làm việc (đoạn NET: NET + khoản quy đổi).
            BigDecimal baseExact = BigDecimal.ZERO;
            BigDecimal allowanceExact = BigDecimal.ZERO;
            for (SalarySegment seg : in.salary()) {
                BigDecimal share = nz(seg.workingDayShare());
                BigDecimal base = nz(seg.baseSalary()).add(seg.net() ? grossUp : BigDecimal.ZERO);
                baseExact = baseExact.add(base.multiply(share));
                allowanceExact = allowanceExact.add(nz(seg.insuredAllowance()).multiply(share));
            }
            baseExact = in.overrides().containsKey(LUONG_CO_BAN) ? money(in.overrides().get(LUONG_CO_BAN)) : baseExact;
            allowanceExact = in.overrides().containsKey(PHU_CAP_DONG_BH) ? money(in.overrides().get(PHU_CAP_DONG_BH)) : allowanceExact;
            BigDecimal base = put(LUONG_CO_BAN, baseExact);
            BigDecimal allowance = put(PHU_CAP_DONG_BH, allowanceExact);

            BigDecimal std = put(NGAY_CONG_CHUAN, nz(in.standardDays()));
            BigDecimal actualDays = BigDecimal.ZERO;
            for (DayKind k : s.actualDayKinds()) {
                actualDays = actualDays.add(nz(in.days().get(k)));
            }
            BigDecimal actual = put(NGAY_CONG_THUC_TE, actualDays);

            BigDecimal monthly = baseExact.add(allowanceExact);
            BigDecimal gop;
            if (std.signum() > 0) {
                gop = monthly.multiply(actual).divide(std, 0, RoundingMode.HALF_UP);
            } else {
                gop = BigDecimal.ZERO;
                if (monthly.signum() > 0 && !in.overrides().containsKey(LUONG_GOP)) {
                    warnings.add("Thiếu ngày công chuẩn của tháng – chưa tính được lương gộp.");
                }
            }
            put(LUONG_GOP, gop);

            // Phụ cấp theo từ khóa; không khớp → thừa/thiếu + cảnh báo.
            Map<PayrollField, BigDecimal> pc = new EnumMap<>(PayrollField.class);
            BigDecimal unmatched = BigDecimal.ZERO;
            for (NamedAllowance na : in.allowances()) {
                PayrollField target = columnFor(na.name());
                if (target == null) {
                    unmatched = unmatched.add(nz(na.amountPerMonth()));
                    warnings.add("Phụ cấp \"" + na.name() + "\" không khớp cột phụ cấp nào – đã cộng vào Các khoản thừa/thiếu.");
                } else {
                    pc.merge(target, nz(na.amountPerMonth()), BigDecimal::add);
                }
            }
            for (PayrollField f : List.of(PC_CHUYEN_CAN, PC_XANG_XE, PC_TRACH_NHIEM)) {
                put(f, pc.getOrDefault(f, BigDecimal.ZERO));
            }
            for (PayrollField f : MANUAL) {
                BigDecimal v = nz(in.manual().get(f));
                put(f, f == THUA_THIEU ? v.add(unmatched) : v);
            }

            // Làm thêm giờ: lương giờ = cơ sở ÷ ngày công chuẩn ÷ số giờ một ngày.
            BigDecimal otBase = s.overtimeBaseIncludesInsuredAllowance() ? base.add(allowance) : base;
            BigDecimal hourDivisor = std.multiply(nz(s.hoursPerDay()));
            BigDecimal otTaxed = BigDecimal.ZERO;
            BigDecimal otFree = BigDecimal.ZERO;
            if (hourDivisor.signum() > 0) {
                BigDecimal hours = BigDecimal.ZERO;
                BigDecimal premiumHours = BigDecimal.ZERO;
                for (OvertimeItem ot : in.overtime()) {
                    hours = hours.add(nz(ot.hours()));
                    premiumHours = premiumHours.add(nz(ot.hours())
                            .multiply(BigDecimal.valueOf(ot.ratePercent() - 100L)).divide(HUNDRED, SCALE, RoundingMode.HALF_UP));
                }
                otTaxed = hours.multiply(otBase).divide(hourDivisor, 0, RoundingMode.HALF_UP);
                otFree = premiumHours.multiply(otBase).divide(hourDivisor, 0, RoundingMode.HALF_UP);
            }
            put(OT_CHIU_THUE, otTaxed);
            put(OT_KHONG_CHIU_THUE, otFree);

            BigDecimal leave = BigDecimal.ZERO;
            if (in.unusedLeaveDays() != null && s.payUnusedLeaveOnTermination() && std.signum() > 0) {
                leave = in.unusedLeaveDays().multiply(base.add(allowance)).divide(std, 0, RoundingMode.HALF_UP);
            }
            put(PHEP_CHUA_SU_DUNG, leave);

            BigDecimal tongLuongGop = total(TONG_LUONG_GOP, sum(LUONG_GOP, THUONG_DU_AN, PC_CHUYEN_CAN, PC_XANG_XE, PC_TRACH_NHIEM,
                    THUONG_KPI, OT_CHIU_THUE, OT_KHONG_CHIU_THUE, TRO_CAP_THOI_VIEC, PHEP_CHUA_SU_DUNG));

            // Bảo hiểm.
            BigDecimal insuranceBase = in.insuranceSalaryOverride() != null ? in.insuranceSalaryOverride() : base.add(allowance);
            BigDecimal siBase = put(LUONG_DONG_BHXH_BHYT, covered ? insuranceBase.min(rules.socialInsuranceCap()) : BigDecimal.ZERO);
            BigDecimal uiBase = put(LUONG_DONG_BHTN, covered ? insuranceBase.min(rules.unemploymentInsuranceCap()) : BigDecimal.ZERO);
            put(BHXH_NLD, siBase.multiply(s.bhxhEmployee()));
            put(BHYT_NLD, siBase.multiply(s.bhytEmployee()));
            put(BHTN_NLD, uiBase.multiply(s.bhtnEmployee()));
            BigDecimal tongBh = total(TONG_BH_NLD, sum(BHXH_NLD, BHYT_NLD, BHTN_NLD));

            // Thuế.
            BigDecimal personal = put(GIAM_TRU_BAN_THAN, contract ? rules.personalDeduction() : BigDecimal.ZERO);
            BigDecimal dependants = put(SO_NGUOI_PHU_THUOC, contract ? BigDecimal.valueOf(in.dependants()) : BigDecimal.ZERO);
            BigDecimal dependantDeduction = put(GIAM_TRU_NPT, contract ? dependants.multiply(rules.dependantDeduction()) : BigDecimal.ZERO);
            if (contract) {
                BigDecimal taxable = put(THU_NHAP_TINH_THUE, tongLuongGop.subtract(tongBh).subtract(personal)
                        .subtract(dependantDeduction).subtract(a.get(OT_KHONG_CHIU_THUE)).max(BigDecimal.ZERO));
                put(THUE_TNCN, rules.monthlyPit(taxable));
            } else {
                BigDecimal income = put(THU_NHAP_TINH_THUE, tongLuongGop);
                boolean withhold = income.compareTo(s.withholdingThreshold()) >= 0 && !in.pitCommitment08();
                put(THUE_TNCN, withhold ? income.multiply(s.withholdingRate()) : BigDecimal.ZERO);
            }

            total(THUC_NHAN, tongLuongGop.subtract(tongBh).subtract(a.get(THUE_TNCN)).subtract(a.get(QUYET_TOAN_THUE))
                    .add(a.get(THUA_THIEU)).subtract(a.get(GIAM_TRU_KHAC)));

            put(BHXH_NSDLD, siBase.multiply(s.bhxhCompany()));
            put(BHYT_NSDLD, siBase.multiply(s.bhytCompany()));
            put(BHTN_NSDLD, uiBase.multiply(s.bhtnCompany()));
            BigDecimal tongBhCongTy = total(TONG_BH_NSDLD, sum(BHXH_NSDLD, BHYT_NSDLD, BHTN_NSDLD));
            put(KPCD, s.unionFeeEnabled() ? siBase.multiply(s.unionFeeRate()) : BigDecimal.ZERO);
            total(TONG_CHI_PHI, tongLuongGop.add(tongBhCongTy).add(a.get(KPCD)).add(a.get(THUA_THIEU)));
            return a;
        }

        /** Ghi cột: số tự tính, hoặc số sửa tay nếu có; tiền làm tròn đến đồng. */
        private BigDecimal put(PayrollField f, BigDecimal computed) {
            BigDecimal v;
            if (salaryOnly && NOT_SALARY.contains(f)) {
                v = BigDecimal.ZERO;
            } else {
                v = in.overrides().getOrDefault(f, computed);
            }
            if (!NON_MONEY.contains(f)) {
                v = money(v);
            }
            a.put(f, v);
            return v;
        }

        /** Cột tổng: luôn tính, không nhận sửa tay. */
        private BigDecimal total(PayrollField f, BigDecimal computed) {
            BigDecimal v = money(computed);
            a.put(f, v);
            return v;
        }

        private BigDecimal sum(PayrollField... fields) {
            BigDecimal t = BigDecimal.ZERO;
            for (PayrollField f : fields) {
                t = t.add(a.get(f));
            }
            return t;
        }

        private PayrollField columnFor(String name) {
            String n = plain(name);
            for (Map.Entry<PayrollField, List<String>> e : s.allowanceKeywords().entrySet()) {
                for (String kw : e.getValue()) {
                    String k = plain(kw);
                    if (!k.isEmpty() && n.contains(k)) {
                        return e.getKey();
                    }
                }
            }
            return null;
        }
    }

    /** Chuẩn hóa để so từ khóa: bỏ dấu, đ → d, chữ thường, gộp khoảng trắng. */
    static String plain(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

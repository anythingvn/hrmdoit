package vn.doit.hr.report;

import static vn.doit.hr.payroll.PayrollField.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollLine;

/**
 * Báo cáo chi phí lương từ các bảng lương đã import.
 *
 * <p>Cơ cấu chi phí (khớp công thức "Tổng chi phí nhân viên" trong mẫu bảng lương DOIT):
 * Lương theo ngày công + Thưởng + Phụ cấp + Làm thêm giờ + Khoản khác (trợ cấp thôi việc, phép chưa nghỉ)
 * = Tổng lương gộp; cộng BH phần công ty đóng, KPCĐ, khoản thừa/thiếu = Tổng chi phí.
 */
public final class PayrollCostReport {

    /** Một nhóm chi phí; tiền tính bằng VND. */
    public record Cost(int headcount, BigDecimal baseSalary, BigDecimal bonus, BigDecimal allowances, BigDecimal overtime,
                       BigDecimal other, BigDecimal employerInsurance, BigDecimal unionFee, BigDecimal adjustments,
                       BigDecimal totalCostInFile, BigDecimal netPay, BigDecimal personalIncomeTax) {

        static final Cost ZERO = new Cost(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        static Cost of(PayrollLine l) {
            return new Cost(1,
                    l.get(LUONG_GOP),
                    sum(l, THUONG_DU_AN, THUONG_KPI),
                    sum(l, PC_CHUYEN_CAN, PC_XANG_XE, PC_TRACH_NHIEM),
                    sum(l, OT_CHIU_THUE, OT_KHONG_CHIU_THUE),
                    sum(l, TRO_CAP_THOI_VIEC, PHEP_CHUA_SU_DUNG),
                    l.get(TONG_BH_NSDLD),
                    l.get(KPCD),
                    l.get(THUA_THIEU),
                    l.get(TONG_CHI_PHI),
                    l.get(THUC_NHAN),
                    l.get(THUE_TNCN));
        }

        Cost plus(Cost o) {
            return new Cost(headcount + o.headcount, baseSalary.add(o.baseSalary), bonus.add(o.bonus),
                    allowances.add(o.allowances), overtime.add(o.overtime), other.add(o.other),
                    employerInsurance.add(o.employerInsurance), unionFee.add(o.unionFee), adjustments.add(o.adjustments),
                    totalCostInFile.add(o.totalCostInFile), netPay.add(o.netPay), personalIncomeTax.add(o.personalIncomeTax));
        }

        public BigDecimal grossPay() {
            return baseSalary.add(bonus).add(allowances).add(overtime).add(other);
        }

        /** Tổng chi phí tính lại từ các thành phần. */
        public BigDecimal totalCost() {
            return grossPay().add(employerInsurance).add(unionFee).add(adjustments);
        }

        /** Tổng tính lại có khớp cột "Tổng chi phí nhân viên" trong file không (lệch ≤ 1đ/người). */
        public boolean consistentWithFile() {
            return totalCost().subtract(totalCostInFile).abs().compareTo(BigDecimal.valueOf(Math.max(1, headcount))) <= 0;
        }

        public BigDecimal costPerHead() {
            return headcount == 0 ? BigDecimal.ZERO
                    : totalCost().divide(BigDecimal.valueOf(headcount), 0, RoundingMode.HALF_UP);
        }
    }

    /**
     * @param changeVsPreviousMonth tỷ lệ thay đổi tổng chi phí so với tháng trước (0.05 = +5%); null nếu không có tháng trước
     */
    public record MonthCost(YearMonth month, Cost total, SortedMap<String, Cost> byGroup, BigDecimal changeVsPreviousMonth) {
    }

    private PayrollCostReport() {
    }

    /**
     * @param payrolls bảng lương theo kỳ
     * @param groupBy cách gom nhóm, vd theo vị trí ({@code PayrollLine::position}) hoặc theo phòng ban (tra mã NV)
     */
    public static List<MonthCost> monthly(SortedMap<YearMonth, List<PayrollLine>> payrolls, Function<PayrollLine, String> groupBy) {
        List<MonthCost> out = new ArrayList<>();
        Cost previous = null;
        for (Map.Entry<YearMonth, List<PayrollLine>> e : payrolls.entrySet()) {
            Cost total = Cost.ZERO;
            SortedMap<String, Cost> groups = new TreeMap<>();
            for (PayrollLine l : e.getValue()) {
                Cost c = Cost.of(l);
                total = total.plus(c);
                String g = groupBy.apply(l);
                groups.merge(g == null || g.isBlank() ? "(không xác định)" : g, c, Cost::plus);
            }
            BigDecimal change = previous == null || previous.totalCost().signum() == 0 ? null
                    : total.totalCost().subtract(previous.totalCost()).divide(previous.totalCost(), 4, RoundingMode.HALF_UP);
            out.add(new MonthCost(e.getKey(), total, groups, change));
            previous = total;
        }
        return out;
    }

    private static BigDecimal sum(PayrollLine l, PayrollField... fields) {
        BigDecimal s = BigDecimal.ZERO;
        for (PayrollField f : fields) {
            s = s.add(l.get(f));
        }
        return s;
    }
}

package vn.doit.hr.payroll.calc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.calc.CalcSettings.DayKind;

/**
 * Đầu vào tính lương của một người trong một kỳ.
 *
 * @param insured thuộc diện đóng BHXH bắt buộc (chỉ có nghĩa với HĐLĐ)
 * @param salary các đoạn mức lương hiệu lực trong kỳ, tổng {@code workingDayShare} = 1
 * @param insuranceSalaryOverride mức đóng BH ghi trên hồ sơ; null = lương cơ bản + PC đóng BH
 * @param days số ngày theo loại, từ bảng công đã chốt
 * @param unusedLeaveDays số ngày phép còn lại tại ngày nghỉ việc; null = không nghỉ việc trong kỳ
 * @param manual 6 khoản Kế toán nhập (thưởng dự án, KPI, trợ cấp thôi việc, thừa/thiếu, quyết toán thuế, giảm trừ khác)
 * @param overrides ô tự tính đã sửa tay; thay số tự tính trước khi tính các cột phía sau (trừ các cột tổng)
 */
public record CalcInput(
        PersonKind kind,
        boolean insured,
        List<SalarySegment> salary,
        BigDecimal insuranceSalaryOverride,
        Map<DayKind, BigDecimal> days,
        BigDecimal standardDays,
        List<NamedAllowance> allowances,
        List<OvertimeItem> overtime,
        BigDecimal unusedLeaveDays,
        int dependants,
        boolean pitCommitment08,
        Map<PayrollField, BigDecimal> manual,
        Map<PayrollField, BigDecimal> overrides) {

    /** HĐLĐ: BH + thuế lũy tiến; thực tập, HĐ dịch vụ: khấu trừ theo tỷ lệ. */
    public enum PersonKind {
        HDLD, THUC_TAP, DICH_VU
    }

    /**
     * Một đoạn mức lương trong tháng.
     *
     * @param baseSalary lương cơ bản (đoạn NET: lương NET thỏa thuận)
     * @param insuredAllowance phụ cấp lương tính đóng BH (đoạn NET: được nhận nguyên, không bị trừ BH/thuế)
     * @param workingDayShare tỷ lệ số ngày làm việc của đoạn trong tháng
     */
    public record SalarySegment(BigDecimal baseSalary, BigDecimal insuredAllowance, boolean net, BigDecimal workingDayShare) {
    }

    public record NamedAllowance(String name, BigDecimal amountPerMonth) {
    }

    /** Giờ làm thêm thực tế và hệ số (150, 200, 300 = %). */
    public record OvertimeItem(BigDecimal hours, int ratePercent) {
    }

    public CalcInput {
        salary = salary == null ? List.of() : List.copyOf(salary);
        days = days == null ? Map.of() : Collections.unmodifiableMap(copy(days, DayKind.class));
        allowances = allowances == null ? List.of() : List.copyOf(allowances);
        overtime = overtime == null ? List.of() : List.copyOf(overtime);
        manual = manual == null ? Map.of() : Collections.unmodifiableMap(copy(manual, PayrollField.class));
        overrides = overrides == null ? Map.of() : Collections.unmodifiableMap(copy(overrides, PayrollField.class));
    }

    private static <K extends Enum<K>> Map<K, BigDecimal> copy(Map<K, BigDecimal> src, Class<K> type) {
        Map<K, BigDecimal> m = new EnumMap<>(type);
        src.forEach((k, v) -> {
            if (v != null) {
                m.put(k, v);
            }
        });
        return m;
    }
}

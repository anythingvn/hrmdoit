package vn.doit.hr.payroll.calc;

import static vn.doit.hr.payroll.PayrollField.PC_CHUYEN_CAN;
import static vn.doit.hr.payroll.PayrollField.PC_TRACH_NHIEM;
import static vn.doit.hr.payroll.PayrollField.PC_XANG_XE;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.doit.hr.payroll.PayrollField;

/**
 * Bộ thông số tính lương (cấu hình được theo tháng hiệu lực, spec §2.1). Tỷ lệ ghi dạng thập phân (8% = 0.08).
 *
 * @param overtimeAllNonTaxable true: toàn bộ tiền làm thêm vào cột không chịu thuế; false: tách theo luật (phần 100% chịu thuế,
 *     phần vượt không chịu thuế)
 * @param actualDayKinds loại ngày cộng vào ngày công thực tế
 * @param allowanceKeywords từ khóa (không dấu, chữ thường) ghép phụ cấp vào cột; phụ cấp không khớp cộng vào thừa/thiếu
 * @param withholdingThreshold ngưỡng thu nhập tháng phải khấu trừ thuế (thực tập, HĐ dịch vụ)
 */
public record CalcSettings(
        BigDecimal bhxhEmployee,
        BigDecimal bhytEmployee,
        BigDecimal bhtnEmployee,
        BigDecimal bhxhCompany,
        BigDecimal bhytCompany,
        BigDecimal bhtnCompany,
        boolean unionFeeEnabled,
        BigDecimal unionFeeRate,
        BigDecimal hoursPerDay,
        boolean overtimeBaseIncludesInsuredAllowance,
        boolean overtimeAllNonTaxable,
        Set<DayKind> actualDayKinds,
        Map<PayrollField, List<String>> allowanceKeywords,
        BigDecimal withholdingThreshold,
        BigDecimal withholdingRate,
        boolean payUnusedLeaveOnTermination) {

    /** Loại ngày trong bảng công: làm việc, phép có lương, lễ, công tác, ốm. */
    public enum DayKind {
        WORK, PAID_LEAVE, HOLIDAY, TRIP, SICK
    }

    public CalcSettings {
        actualDayKinds = actualDayKinds == null || actualDayKinds.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(DayKind.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(actualDayKinds));
        Map<PayrollField, List<String>> kw = new EnumMap<>(PayrollField.class);
        if (allowanceKeywords != null) {
            allowanceKeywords.forEach((f, list) -> kw.put(f, List.copyOf(list)));
        }
        allowanceKeywords = Collections.unmodifiableMap(kw);
    }

    /** Giá trị mặc định theo bảng spec §2.1. */
    public static CalcSettings defaults() {
        Map<PayrollField, List<String>> kw = new EnumMap<>(PayrollField.class);
        kw.put(PC_CHUYEN_CAN, List.of("chuyen can"));
        kw.put(PC_XANG_XE, List.of("xang"));
        kw.put(PC_TRACH_NHIEM, List.of("trach nhiem"));
        return new CalcSettings(
                new BigDecimal("0.08"), new BigDecimal("0.015"), new BigDecimal("0.01"),
                new BigDecimal("0.175"), new BigDecimal("0.03"), new BigDecimal("0.01"),
                false, new BigDecimal("0.02"),
                new BigDecimal("8"), false, false,
                EnumSet.of(DayKind.WORK, DayKind.PAID_LEAVE, DayKind.HOLIDAY, DayKind.TRIP),
                kw,
                new BigDecimal("2000000"), new BigDecimal("0.10"),
                true);
    }
}

package vn.doit.hrm.service.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.calc.CalcSettings.DayKind;

/**
 * Bộ thông số tính lương có hiệu lực từ tháng {@code effectiveFrom} (yyyy-MM). Tỷ lệ ghi dạng thập phân (17,5% = 0.175).
 * {@code locked}: đã có kỳ lương đã duyệt trong khoảng hiệu lực của bộ này nên không sửa, không xóa.
 * {@code insuranceSkipOnLongUnpaid}: không đóng BH, KPCĐ khi nghỉ không lương từ 14 ngày trong tháng; null (bộ lưu trước khi có
 * thông số này) = bật như mặc định.
 */
public record CalcSettingDTO(
    Long id,
    String effectiveFrom,
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
    boolean payUnusedLeaveOnTermination,
    Boolean insuranceSkipOnLongUnpaid,
    Boolean locked
) {}

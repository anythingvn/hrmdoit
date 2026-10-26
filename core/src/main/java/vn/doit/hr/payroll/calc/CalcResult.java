package vn.doit.hr.payroll.calc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import vn.doit.hr.payroll.PayrollField;

/** Kết quả tính một dòng: đủ 36 khoản (số tiền đã làm tròn đến đồng) và cảnh báo. */
public record CalcResult(Map<PayrollField, BigDecimal> amounts, List<String> warnings) {

    public CalcResult {
        amounts = Collections.unmodifiableMap(new EnumMap<>(amounts));
        warnings = List.copyOf(warnings);
    }
}

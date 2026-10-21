package vn.doit.hrm.service.payroll;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.payroll.PayrollField;

/** Đọc cột {@code amounts} (JSON tên trường → số dạng chuỗi) của một dòng lương đã lưu. */
public final class PayrollAmounts {

    private PayrollAmounts() {}

    public static Map<PayrollField, BigDecimal> parse(ObjectMapper objectMapper, String json) {
        Map<String, String> raw = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        Map<PayrollField, BigDecimal> out = new EnumMap<>(PayrollField.class);
        for (PayrollField f : PayrollField.values()) {
            String v = raw.get(f.name());
            if (v != null) {
                out.put(f, new BigDecimal(v));
            }
        }
        return out;
    }
}

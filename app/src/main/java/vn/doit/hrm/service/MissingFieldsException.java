package vn.doit.hrm.service;

import java.util.List;

/** Hồ sơ thiếu trường bắt buộc để sinh văn bản; HR có thể "Vẫn tạo". */
public class MissingFieldsException extends EmployeeRuleException {

    private final List<String> fields;

    public MissingFieldsException(List<String> fields) {
        super("Hồ sơ còn thiếu: " + String.join(", ", fields), "missingfields");
        this.fields = List.copyOf(fields);
    }

    public List<String> getFields() {
        return fields;
    }
}

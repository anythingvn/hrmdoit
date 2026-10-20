package vn.doit.hrm.service;

/** Vi phạm quy tắc nghiệp vụ khi tạo/sửa nhân viên; errorKey dùng để hiển thị thông báo. */
public class EmployeeRuleException extends RuntimeException {

    private final String errorKey;

    public EmployeeRuleException(String message, String errorKey) {
        super(message);
        this.errorKey = errorKey;
    }

    public String getErrorKey() {
        return errorKey;
    }
}

package vn.doit.hr.employee;

/**
 * Một vấn đề phát hiện khi đọc hoặc kiểm tra file nhân viên.
 *
 * @param severity ERROR chặn việc lưu; WARNING cho lưu nhưng cần người dùng xác nhận
 * @param excelRow số dòng trong Excel (1-based), 0 nếu là vấn đề cấp file
 * @param fullName họ và tên của dòng (có thể null)
 * @param field cột liên quan (có thể null)
 */
public record EmployeeIssue(Severity severity, int excelRow, String fullName, EmployeeField field, String message) {

    public enum Severity { ERROR, WARNING }

    public static EmployeeIssue fileError(String message) {
        return new EmployeeIssue(Severity.ERROR, 0, null, null, message);
    }

    public static EmployeeIssue fileWarning(String message) {
        return new EmployeeIssue(Severity.WARNING, 0, null, null, message);
    }
}

package vn.doit.hr.payroll;

/**
 * Một vấn đề phát hiện khi import.
 *
 * @param severity ERROR chặn việc lưu; WARNING cho phép lưu nhưng cần người duyệt xác nhận
 * @param excelRow số dòng trong Excel (1-based), 0 nếu là lỗi cấp file
 * @param employeeCode mã nhân viên của dòng (có thể null)
 * @param field cột liên quan (có thể null)
 */
public record Issue(Severity severity, int excelRow, String employeeCode, PayrollField field, String message) {

    public enum Severity { ERROR, WARNING }

    static Issue fileError(String message) {
        return new Issue(Severity.ERROR, 0, null, null, message);
    }

    static Issue fileWarning(String message) {
        return new Issue(Severity.WARNING, 0, null, null, message);
    }

    @Override
    public String toString() {
        String where = excelRow > 0 ? "Dòng " + excelRow : "File";
        if (employeeCode != null) {
            where += " [" + employeeCode + "]";
        }
        if (field != null) {
            where += " (" + field.label() + ")";
        }
        return severity + " " + where + ": " + message;
    }
}

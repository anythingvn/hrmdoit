package vn.doit.hrm.service.employee.dto;

import java.util.List;

/**
 * Kết quả kiểm tra file nhập nhân viên. Chỉ gồm các cột cần để soát lỗi: không có MST, số tài khoản, lương đóng BHXH.
 *
 * @param canSave không có lỗi (cảnh báo vẫn cần người dùng xác nhận khi lưu)
 */
public record EmployeeImportCheckDTO(
    int rowCount,
    List<Row> rows,
    List<Issue> issues,
    List<NewDepartment> newDepartments,
    long errorCount,
    long warningCount,
    boolean canSave
) {
    /**
     * Một dòng sẽ được tạo.
     *
     * @param personType tên enum {@code PersonType}
     * @param code mã dự kiến hoặc mã ghi tay; null cho HĐ dịch vụ (tự sinh khi lưu)
     */
    public record Row(
        int excelRow,
        String fullName,
        String idNumber,
        String personType,
        String code,
        boolean codeProvisional,
        String departmentName,
        String managerRef
    ) {}

    /** Lỗi ({@code ERROR}) hoặc cảnh báo ({@code WARNING}); {@code excelRow} = 0 là cấp file. */
    public record Issue(String severity, int excelRow, String fullName, String field, String fieldLabel, String message) {}

    /** Phòng ban chưa có, sẽ được tạo khi lưu, kèm các dòng dùng tên đó. */
    public record NewDepartment(String name, List<Integer> excelRows) {}
}

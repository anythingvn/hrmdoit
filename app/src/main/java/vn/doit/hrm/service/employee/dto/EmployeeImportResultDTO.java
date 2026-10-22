package vn.doit.hrm.service.employee.dto;

import java.util.List;

/** Kết quả lưu file nhập nhân viên, với mã thật đã cấp. */
public record EmployeeImportResultDTO(int createdCount, List<Created> created, List<String> createdDepartments) {
    public record Created(int excelRow, String fullName, String code) {}
}

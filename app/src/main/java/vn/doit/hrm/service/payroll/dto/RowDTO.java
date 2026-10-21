package vn.doit.hrm.service.payroll.dto;

/**
 * Một dòng nhân viên trong file: mã, tên, chức danh như trong file; hồ sơ đã giải được (gắn tay, hoặc tự khớp khi mã chỉ xuất hiện
 * một lần và có hồ sơ) hoặc null.
 */
public record RowDTO(
    int excelRow,
    String fileCode,
    String fileName,
    String position,
    Long employeeId,
    String employeeCode,
    String employeeName,
    boolean mappedManually
) {}

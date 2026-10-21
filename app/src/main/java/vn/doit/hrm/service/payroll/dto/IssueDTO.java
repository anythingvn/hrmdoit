package vn.doit.hrm.service.payroll.dto;

/** Lỗi ({@code ERROR}) hoặc cảnh báo ({@code WARNING}); {@code excelRow} = 0 là cấp file. */
public record IssueDTO(String severity, int excelRow, String employeeCode, String field, String fieldLabel, String message) {}

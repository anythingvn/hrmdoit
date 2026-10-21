package vn.doit.hrm.service.payroll.dto;

/** Một trường của mẫu bảng lương và cột Excel tìm được ({@code column} dạng "F", null nếu không thấy). */
public record ColumnDTO(String field, String label, String column, boolean required) {}

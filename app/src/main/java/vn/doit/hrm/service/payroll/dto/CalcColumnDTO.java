package vn.doit.hrm.service.payroll.dto;

/** Một cột của bản tính nháp: tiêu đề chính / phụ như mẫu bảng lương; {@code input} = ô Kế toán nhập tay. */
public record CalcColumnDTO(String field, String header, String subHeader, boolean input) {}

package vn.doit.hrm.service.payroll.dto;

/** Bản nháp đã xác nhận thành phiên bản {@code version} của kỳ {@code period}. */
public record ConfirmedDTO(String period, int version) {}

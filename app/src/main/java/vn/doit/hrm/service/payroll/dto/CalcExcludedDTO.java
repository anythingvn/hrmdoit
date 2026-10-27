package vn.doit.hrm.service.payroll.dto;

/** Dòng đã bỏ khỏi bản tính lương nháp (khôi phục được). */
public record CalcExcludedDTO(Long lineId, Long employeeId, String code, String fullName) {}

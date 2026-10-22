package vn.doit.hrm.service.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Một dòng của vùng lưu trữ. Không có dữ liệu nhạy cảm (CCCD, ngân hàng...). */
public record ArchivedEmployeeDTO(
    Long id,
    String code,
    String fullName,
    String departmentName,
    LocalDate lastWorkingDay,
    Instant archivedAt,
    String archivedBy,
    String archiveNote
) {}

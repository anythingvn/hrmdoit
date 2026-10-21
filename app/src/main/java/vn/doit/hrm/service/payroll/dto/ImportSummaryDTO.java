package vn.doit.hrm.service.payroll.dto;

import java.time.Instant;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;

/** Một bản nháp trong danh sách (không đọc lại file). */
public record ImportSummaryDTO(Long id, String period, String fileName, PayrollImportStatus status, String createdBy, Instant createdAt) {}

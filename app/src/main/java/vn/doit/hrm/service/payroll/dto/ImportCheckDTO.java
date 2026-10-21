package vn.doit.hrm.service.payroll.dto;

import java.time.Instant;
import java.util.List;
import vn.doit.hrm.domain.enumeration.PayrollImportStatus;

/** Bản nháp import kèm kết quả đọc và kiểm tra hiện tại. */
public record ImportCheckDTO(
    Long id,
    String period,
    String fileName,
    PayrollImportStatus status,
    boolean warningsAcknowledged,
    String createdBy,
    Instant createdAt,
    List<ColumnDTO> columns,
    List<String> missingRequired,
    List<RowDTO> rows,
    List<IssueDTO> issues,
    int errorCount,
    int warningCount,
    boolean canConfirm
) {}

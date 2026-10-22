package vn.doit.hrm.service.leave.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import vn.doit.hrm.domain.LeaveLedgerEntry;

/** Một dòng sổ phép. */
public record LeaveLedgerEntryDTO(
    Long id,
    Long employeeId,
    String pool,
    String bucket,
    int year,
    BigDecimal amount,
    LocalDate effectiveDate,
    String reason,
    String refType,
    Long refId,
    String note,
    String createdBy,
    Instant createdAt
) {
    public static LeaveLedgerEntryDTO of(LeaveLedgerEntry e) {
        return new LeaveLedgerEntryDTO(
            e.getId(),
            e.getEmployeeId(),
            e.getPool().name(),
            e.getBucket().name(),
            e.getYear(),
            e.getAmount(),
            e.getEffectiveDate(),
            e.getReason().name(),
            e.getRefType(),
            e.getRefId(),
            e.getNote(),
            e.getCreatedBy(),
            e.getCreatedAt()
        );
    }
}

package vn.doit.hrm.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Một mốc mức luật định; {@code effectiveFrom} dạng yyyy-MM, bậc thuế theo thứ tự tăng dần, bậc cuối {@code upTo = null}; {@code used} = mốc đã dùng (không sửa, không xóa được). */
public record RuleMilestoneDTO(
    Long id,
    String effectiveFrom,
    BigDecimal personalDeduction,
    BigDecimal dependantDeduction,
    BigDecimal socialInsuranceCap,
    BigDecimal unemploymentInsuranceCap,
    List<BracketDTO> brackets,
    String legalBasis,
    String createdBy,
    Instant createdAt,
    Boolean used
) {
    public record BracketDTO(BigDecimal upTo, BigDecimal rate) {}
}

package vn.doit.hrm.service.dto;

import java.util.List;
import java.util.Set;
import vn.doit.hrm.domain.enumeration.TemplateKind;

/**
 * Một loại mẫu và các phiên bản (mới nhất trước).
 *
 * @param activeVersion phiên bản đang dùng: 0 = mẫu gốc; null = chưa có mẫu nào
 */
public record TemplateKindDTO(
    TemplateKind kind,
    String label,
    Integer activeVersion,
    boolean hasDefault,
    Set<String> allowedTags,
    List<DocumentTemplateDTO> versions
) {}

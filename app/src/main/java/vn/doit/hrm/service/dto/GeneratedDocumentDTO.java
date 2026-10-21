package vn.doit.hrm.service.dto;

import java.time.Instant;
import vn.doit.hrm.domain.enumeration.GeneratedFileKind;
import vn.doit.hrm.domain.enumeration.TemplateKind;

/** Một file văn bản (không lộ đường dẫn lưu trữ). */
public record GeneratedDocumentDTO(
    Long id,
    GeneratedFileKind kind,
    String fileName,
    TemplateKind templateKind,
    Integer templateVersion,
    Long sourceDocumentId,
    String createdBy,
    Instant createdAt
) {}

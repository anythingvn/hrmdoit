package vn.doit.hrm.service.dto;

import java.time.Instant;
import vn.doit.hrm.domain.enumeration.TemplateKind;

/** Một phiên bản mẫu đã tải lên (không lộ đường dẫn lưu trữ). */
public record DocumentTemplateDTO(Long id, TemplateKind kind, int version, String fileName, String uploadedBy, Instant uploadedAt, boolean active) {}

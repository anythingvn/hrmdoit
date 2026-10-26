package vn.doit.hrm.service.dto;

import java.util.Map;
import vn.doit.hrm.domain.enumeration.DocumentType;

/**
 * Cấu hình số văn bản và mã nhân viên. Trường null = dùng mặc định; {@code defaults} chỉ có khi đọc (giá trị mặc định để hiển thị
 * gợi ý), bị bỏ qua khi ghi.
 */
public record NumberingSettingsDTO(
    String documentPrefix,
    Map<DocumentType, String> documentCodes,
    String staffCodePrefix,
    String internCodePrefix,
    String serviceCodePrefix,
    NumberingSettingsDTO defaults
) {}

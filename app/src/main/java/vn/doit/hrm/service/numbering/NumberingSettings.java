package vn.doit.hrm.service.numbering;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.domain.enumeration.PersonType;

/** Giá trị hiệu lực (đã thay mặc định) của tiền tố số văn bản, mã loại văn bản và tiền tố mã nhân viên. */
public record NumberingSettings(
    String documentPrefix,
    Map<DocumentType, String> documentCodes,
    String staffCodePrefix,
    String internCodePrefix,
    String serviceCodePrefix
) {
    public static final String DEFAULT_DOCUMENT_PREFIX = "DOIT";
    public static final String DEFAULT_STAFF_PREFIX = "DOIT";
    public static final String DEFAULT_INTERN_PREFIX = "TT";
    public static final String DEFAULT_SERVICE_PREFIX = "HDDV";

    public static NumberingSettings defaults() {
        Map<DocumentType, String> codes = new EnumMap<>(DocumentType.class);
        for (DocumentType t : DocumentType.values()) {
            codes.put(t, t.code());
        }
        return new NumberingSettings(DEFAULT_DOCUMENT_PREFIX, codes, DEFAULT_STAFF_PREFIX, DEFAULT_INTERN_PREFIX, DEFAULT_SERVICE_PREFIX);
    }

    public String documentCode(DocumentType t) {
        String code = documentCodes.get(t);
        return code == null ? t.code() : code;
    }

    /** Tiền tố dãy mã tuần tự; dịch vụ không dùng dãy tuần tự nên trả null. */
    public String sequencePrefix(PersonType t) {
        return switch (t) {
            case NHAN_VIEN -> staffCodePrefix;
            case THUC_TAP -> internCodePrefix;
            case DICH_VU -> null;
        };
    }

    public List<String> sequencePrefixes() {
        return List.of(staffCodePrefix, internCodePrefix);
    }
}

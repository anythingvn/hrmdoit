package vn.doit.hrm.domain.enumeration;

import java.util.Set;
import vn.doit.hr.contract.TemplateTags;

/**
 * Loại mẫu văn bản: tập thẻ được dùng và mẫu gốc (trong classpath của {@code core}) khi chưa tải mẫu nào lên.
 * HĐ thử việc, đào tạo, dịch vụ chưa có mẫu gốc: HR tải mẫu lên trước khi sinh văn bản.
 */
public enum TemplateKind {
    HDLD("Hợp đồng lao động", TemplateTags.CONTRACT_TAGS, "templates/DOIT_HDLD_template.docx"),
    HDTV("Hợp đồng thử việc", TemplateTags.CONTRACT_TAGS, null),
    HDDT("Hợp đồng đào tạo", TemplateTags.CONTRACT_TAGS, null),
    HDDV("Hợp đồng dịch vụ", TemplateTags.CONTRACT_TAGS, null),
    QD_CU_NHAN_SU("Quyết định cử nhân sự", TemplateTags.DECISION_TAGS, "templates/DOIT_QD_CuNhanSu_template.docx");

    private final String label;
    private final Set<String> allowedTags;
    private final String defaultResource;

    TemplateKind(String label, Set<String> allowedTags, String defaultResource) {
        this.label = label;
        this.allowedTags = allowedTags;
        this.defaultResource = defaultResource;
    }

    public String label() {
        return label;
    }

    public Set<String> allowedTags() {
        return allowedTags;
    }

    /** Đường dẫn classpath của mẫu gốc; null nếu chưa có. */
    public String defaultResource() {
        return defaultResource;
    }

    public static TemplateKind forContract(ContractType type) {
        return switch (type) {
            case HDLD_XDTH, HDLD_KXDTH -> HDLD;
            case HDTV -> HDTV;
            case HDDT -> HDDT;
            case HDDV -> HDDV;
        };
    }
}

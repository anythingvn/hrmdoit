package vn.doit.hrm.domain.enumeration;

/** Loại văn bản có dãy số riêng: {@code DOIT/{năm}/{code}/{số}}. */
public enum DocumentType {
    HDLD("HĐLĐ"),
    HDTV("HĐTV"),
    HDDT("HĐĐT"),
    HDDV("HĐDV"),
    QD_CU_NHAN_SU("QĐCNS");

    private final String code;

    DocumentType(String code) {
        this.code = code;
    }

    /** Mã in trong số văn bản. */
    public String code() {
        return code;
    }

    /** HĐLĐ xác định và không xác định thời hạn dùng chung một dãy. */
    public static DocumentType forContract(ContractType type) {
        return switch (type) {
            case HDLD_XDTH, HDLD_KXDTH -> HDLD;
            case HDTV -> HDTV;
            case HDDT -> HDDT;
            case HDDV -> HDDV;
        };
    }
}

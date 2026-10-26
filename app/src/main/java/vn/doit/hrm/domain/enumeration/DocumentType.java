package vn.doit.hrm.domain.enumeration;

/** Loại văn bản có dãy số riêng: {@code {tiền tố}/{năm}/{mã loại}/{số}}; {@link #code()} là mã mặc định, Cài đặt có thể đổi. */
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

    /** Mã mặc định in trong số văn bản (mã hiệu lực lấy từ cấu hình số văn bản). */
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

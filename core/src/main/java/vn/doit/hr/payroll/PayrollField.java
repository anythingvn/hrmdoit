package vn.doit.hr.payroll;

/**
 * Các cột số tiền/số liệu trong mẫu "BẢNG LƯƠNG NHÂN VIÊN" của DOIT.
 *
 * <p>Cột được nhận diện theo <b>tên tiêu đề</b> (dòng tiêu đề chính + dòng tiêu đề phụ),
 * không theo chữ cái cột. Nhờ vậy kế toán chèn/xóa cột trong Excel thì import vẫn chạy.
 * Phần chú thích trong ngoặc cuối tiêu đề (vd "BHXH\n(8%)") được bỏ qua khi so khớp.
 */
public enum PayrollField {
    LUONG_CO_BAN("Mức lương", "Lương cơ bản", false),
    PHU_CAP_DONG_BH("Mức lương", "Phụ cấp lương tính đóng BH", false),
    LUONG_DONG_BHXH_BHYT("Mức lương tính đóng BHXH, BHYT", null, false),
    LUONG_DONG_BHTN("Mức lương tính đóng BHTN", null, false),
    NGAY_CONG_CHUAN("Số ngày công chuẩn trong tháng", null, false),
    NGAY_CONG_THUC_TE("Ngày công thực tế trong tháng", null, false),
    LUONG_GOP("Lương gộp trong tháng", null, false),
    THUONG_DU_AN("Thưởng Dự án", null, false),
    PC_CHUYEN_CAN("Phụ cấp chuyên cần", null, false),
    PC_XANG_XE("Phụ cấp xăng xe", null, false),
    PC_TRACH_NHIEM("Phụ cấp trách nhiệm", null, false),
    THUONG_KPI("Tiền thưởng KPI", null, false),
    OT_CHIU_THUE("Tiền làm thêm giờ", "Chịu thuế", false),
    OT_KHONG_CHIU_THUE("Tiền làm thêm giờ", "Không chịu thuế", false),
    TRO_CAP_THOI_VIEC("Trợ cấp thôi việc", null, false),
    PHEP_CHUA_SU_DUNG("Phép năm chưa sử dụng", null, false),
    TONG_LUONG_GOP("Tổng lương gộp trong tháng", null, true),
    BHXH_NLD(Headers.BH_NLD, "BHXH", false),
    BHYT_NLD(Headers.BH_NLD, "BHYT", false),
    BHTN_NLD(Headers.BH_NLD, "BHTN", false),
    TONG_BH_NLD(Headers.BH_NLD, "Tổng", false),
    GIAM_TRU_BAN_THAN("Giảm trừ bản thân", null, false),
    SO_NGUOI_PHU_THUOC("Số người phụ thuộc", null, false),
    GIAM_TRU_NPT("Giảm trừ người phụ thuộc", null, false),
    THU_NHAP_TINH_THUE("Thu nhập tính thuế", null, false),
    THUE_TNCN("Thuế TNCN", null, false),
    QUYET_TOAN_THUE("Quyết toán thuế TNCN", null, false),
    THUA_THIEU("Các khoản thừa/thiếu", null, false),
    GIAM_TRU_KHAC("Các khoản giảm trừ khác", null, false),
    THUC_NHAN("Lương thực nhận", null, true),
    BHXH_NSDLD(Headers.BH_NSDLD, "BHXH", false),
    BHYT_NSDLD(Headers.BH_NSDLD, "BHYT", false),
    BHTN_NSDLD(Headers.BH_NSDLD, "BHTN", false),
    TONG_BH_NSDLD(Headers.BH_NSDLD, "Tổng", false),
    KPCD("Kinh phí công đoàn", null, false),
    TONG_CHI_PHI("Tổng chi phí nhân viên", null, false);

    private final String header;
    private final String subHeader;
    private final boolean required;

    PayrollField(String header, String subHeader, boolean required) {
        this.header = header;
        this.subHeader = subHeader;
        this.required = required;
    }

    public String header() {
        return header;
    }

    public String subHeader() {
        return subHeader;
    }

    public boolean required() {
        return required;
    }

    /** Nhãn hiển thị trong thông báo lỗi. */
    public String label() {
        return subHeader == null ? header : header + " / " + subHeader;
    }

    boolean matches(String normalizedHeader, String normalizedSub) {
        if (!Text.normalize(header).equals(normalizedHeader)) {
            return false;
        }
        return subHeader == null || Text.normalize(subHeader).equals(normalizedSub);
    }

    private static final class Headers {
        static final String BH_NLD = "Đóng góp BHXH, BHYT, BHTN thuộc trách nhiệm NLĐ";
        static final String BH_NSDLD = "Đóng góp BHXH, BHYT, BHTN thuộc trách nhiệm NSDLĐ";
    }
}

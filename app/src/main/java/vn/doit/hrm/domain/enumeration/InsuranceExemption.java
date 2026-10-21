package vn.doit.hrm.domain.enumeration;

/** Lý do không thuộc diện đóng BHXH, BHYT, BHTN bắt buộc (hồ sơ để trống = thuộc diện đóng). */
public enum InsuranceExemption {
    LUONG_HUU("Đang hưởng lương hưu"),
    CONG_TAC_VIEN("Cộng tác viên, HĐ dịch vụ"),
    HD_DUOI_1_THANG("HĐLĐ dưới 1 tháng"),
    THU_VIEC_RIENG("Thử việc theo HĐ thử việc riêng"),
    KHAC("Khác");

    private final String label;

    InsuranceExemption(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

package vn.doit.hr.employee;

import java.util.List;

/** Các cột của file nhập danh sách nhân viên, theo đúng thứ tự trong file mẫu. */
public enum EmployeeField {
    CODE("Mã NV", false, Kind.TEXT, 20, null, true,
            "Để trống thì hệ thống tự gán mã.", "DOIT2601"),
    FULL_NAME("Họ và tên", true, Kind.TEXT, 100, null, false,
            "Bắt buộc.", "Nguyễn Văn A"),
    PERSON_TYPE("Loại hồ sơ", false, Kind.CHOICE, 0, Choices.PERSON_TYPES, false,
            "Chọn một trong: Nhân viên, Thực tập, HĐ dịch vụ. Để trống là Nhân viên.", "Nhân viên"),
    GENDER("Giới tính", false, Kind.CHOICE, 0, Choices.GENDERS, false,
            "Chọn Nam hoặc Nữ.", "Nam"),
    BIRTH_DATE("Ngày sinh", false, Kind.DATE, 0, null, false,
            "Ngày dạng dd/MM/yyyy.", "01/05/1990"),
    ID_NUMBER("Số CCCD", true, Kind.TEXT, 20, null, true,
            "Bắt buộc. 12 chữ số (hoặc 9 chữ số CMND cũ).", "079123456789"),
    ID_ISSUE_DATE("Ngày cấp", false, Kind.DATE, 0, null, false,
            "Ngày dạng dd/MM/yyyy.", "15/08/2021"),
    ID_ISSUE_PLACE("Nơi cấp", false, Kind.TEXT, 200, null, false,
            "Nơi cấp trên giấy tờ.", "Cục Cảnh sát QLHC về TTXH"),
    RESIDENCE("Địa chỉ thường trú", false, Kind.TEXT, 300, null, false,
            "Địa chỉ ghi trên giấy tờ.", "12 Đường Số 1, Quận 1, TP. Hồ Chí Minh"),
    PHONE("Điện thoại", false, Kind.TEXT, 20, null, true,
            "Chữ số, giữ số 0 đầu.", "0900000000"),
    EMAIL("Email", false, Kind.TEXT, 100, null, false,
            "Địa chỉ email.", "a.nguyen@example.com"),
    EDUCATION("Trình độ", false, Kind.TEXT, 100, null, false,
            "Trình độ học vấn cao nhất.", "Đại học"),
    TAX_CODE("MST cá nhân", false, Kind.TEXT, 20, null, true,
            "Mã số thuế cá nhân.", "8000000000"),
    BANK_ACCOUNT("Số tài khoản", false, Kind.TEXT, 30, null, true,
            "Chữ số, giữ số 0 đầu.", "0123456789"),
    BANK_NAME("Ngân hàng", false, Kind.TEXT, 100, null, false,
            "Tên ngân hàng.", "Ngân hàng ABC"),
    SOCIAL_INSURANCE_NO("Số sổ BHXH", false, Kind.TEXT, 20, null, true,
            "Mã số BHXH.", "7900000000"),
    SOCIAL_INSURANCE_START("Ngày bắt đầu đóng BHXH", false, Kind.DATE, 0, null, false,
            "Ngày dạng dd/MM/yyyy.", "01/07/2020"),
    SOCIAL_INSURANCE_SALARY("Lương đóng BHXH", false, Kind.INTEGER, 0, null, false,
            "Số nguyên ≥ 0, đơn vị đồng.", "10000000"),
    INSURANCE_EXEMPTION("Không thuộc diện BH bắt buộc", false, Kind.CHOICE, 0, Choices.EXEMPTIONS, false,
            "Để trống nếu thuộc diện đóng. Nếu không, chọn một lý do trong danh sách.", ""),
    INSURANCE_EXEMPTION_NOTE("Lý do khác", false, Kind.TEXT, 500, null, false,
            "Bắt buộc khi chọn \"Khác\" ở cột trước.", "Làm việc theo thỏa thuận riêng"),
    DEPENDANTS("Số người phụ thuộc", false, Kind.INTEGER, 0, null, false,
            "Số nguyên ≥ 0. Để trống là 0.", "2"),
    DEPARTMENT("Phòng ban", false, Kind.TEXT, 100, null, false,
            "Tên phòng ban. Chưa có thì hệ thống tạo mới.", "Phòng Kinh doanh"),
    POSITION("Vị trí", false, Kind.TEXT, 100, null, false,
            "Chức danh công việc.", "Chuyên viên"),
    MANAGER("Quản lý trực tiếp", false, Kind.TEXT, 20, null, true,
            "Mã NV hoặc số CCCD của người quản lý.", "DOIT2601"),
    JOIN_DATE("Ngày vào làm", false, Kind.DATE, 0, null, false,
            "Ngày dạng dd/MM/yyyy.", "01/03/2024"),
    NOTE("Ghi chú", false, Kind.TEXT, 0, null, false,
            "Ghi chú tự do.", "");

    public static final List<String> PERSON_TYPES = Choices.PERSON_TYPES;
    public static final List<String> GENDERS = Choices.GENDERS;
    public static final List<String> EXEMPTIONS = Choices.EXEMPTIONS;

    /** Danh sách lựa chọn khai báo ở lớp riêng vì hằng enum không tham chiếu tới trường tĩnh khai báo sau nó. */
    private static final class Choices {
        static final List<String> PERSON_TYPES = List.of("Nhân viên", "Thực tập", "HĐ dịch vụ");
        static final List<String> GENDERS = List.of("Nam", "Nữ");
        static final List<String> EXEMPTIONS = List.of(
                "Đang hưởng lương hưu", "Cộng tác viên, HĐ dịch vụ", "HĐLĐ dưới 1 tháng",
                "Thử việc theo HĐ thử việc riêng", "Khác");
    }

    /** Kiểu giá trị của cột. */
    public enum Kind { TEXT, DATE, INTEGER, CHOICE }

    private final String header;
    private final boolean required;
    private final Kind kind;
    private final int maxLength;
    private final List<String> choices;
    private final boolean textFormat;
    private final String hint;
    private final String example;

    EmployeeField(String header, boolean required, Kind kind, int maxLength, List<String> choices,
                  boolean textFormat, String hint, String example) {
        this.header = header;
        this.required = required;
        this.kind = kind;
        this.maxLength = maxLength;
        this.choices = choices == null ? List.of() : choices;
        this.textFormat = textFormat;
        this.hint = hint;
        this.example = example;
    }

    public String header() {
        return header;
    }

    public boolean required() {
        return required;
    }

    public Kind kind() {
        return kind;
    }

    /** Độ dài tối đa; 0 là không giới hạn. */
    public int maxLength() {
        return maxLength;
    }

    public List<String> choices() {
        return choices;
    }

    /** Ô nên đặt định dạng chữ trong file mẫu (giữ số 0 đầu). */
    public boolean textFormat() {
        return textFormat;
    }

    public String hint() {
        return hint;
    }

    /** Ví dụ (dữ liệu giả) hiện ở sheet Hướng dẫn; rỗng nếu không có. */
    public String example() {
        return example;
    }
}

package vn.doit.hrm.service.payroll;

/** Dữ liệu của file test ẩn danh {@code payroll/bang-luong-mau-an-danh.xlsx} (kỳ 06/2026). */
public final class PayrollFixture {

    public static final String RESOURCE = "/payroll/bang-luong-mau-an-danh.xlsx";
    public static final long TOTAL_NET = 219_821_925L;
    public static final long TOTAL_COST = 245_709_000L;

    /** Hồ sơ có sẵn (mã, họ tên giả như trong file). DOIT2515/2516 là người mang mã trùng ở dòng 18/19. */
    public static final String[][] PROFILES = {
        { "DOIT2401", "Phạm Văn An" },
        { "DOIT2402", "Lê Văn Bình" },
        { "DOIT2403", "Hoàng Văn Cường" },
        { "DOIT2505", "Vũ Đức Dũng" },
        { "DOIT2508", "Đặng Minh Giang" },
        { "DOIT2513", "Bùi Thị Hà" },
        { "DOIT2509", "Đỗ Quang Khải" },
        { "DOIT2507", "Ngô Thị Lan" },
        { "DOIT2515", "Dương Tuấn Minh" },
        { "DOIT2516", "Lý Thu Ngân" },
        { "DOIT2514", "Trịnh Hồng Oanh" },
    };

    private PayrollFixture() {}
}

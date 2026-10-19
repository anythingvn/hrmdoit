package vn.doit.hr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Chạy trên bảng lương tháng 06/2026 thật của DOIT (họ tên đã ẩn danh). */
class PayrollImportTest {

    /** Danh bạ giả: mỗi mã gắn với người xuất hiện đầu tiên trong file. */
    private static final Map<String, String> KNOWN = Map.of(
            "DOIT2401", "Nhân viên 01", "DOIT2402", "Nhân viên 02", "DOIT2403", "Nhân viên 03",
            "DOIT2505", "Nhân viên 04", "DOIT2508", "Nhân viên 05", "DOIT2513", "Nhân viên 06",
            "DOIT2509", "Nhân viên 07", "DOIT2507", "Nhân viên 08", "DOIT2514", "Nhân viên 11");
    private static final EmployeeDirectory DIRECTORY = code -> Optional.ofNullable(KNOWN.get(code));

    private ParsedPayroll parse() throws Exception {
        return new PayrollSheetParser().parse(TsvSheetGrid.load("/bang-luong-06-2026.tsv"));
    }

    @Test
    void mapsEveryColumnOfTheDoitTemplate() throws Exception {
        ParsedPayroll p = parse();
        assertEquals(PayrollField.values().length, p.columns().size(), "Phải nhận diện đủ cột: " + p.issues());
        assertEquals(List.of(), p.issues());
        // Cột AJ = index 35 (A=0)
        assertEquals(35, p.columns().get(PayrollField.THUC_NHAN));
        assertEquals(5, p.columns().get(PayrollField.LUONG_CO_BAN));
        assertEquals(6, p.columns().get(PayrollField.PHU_CAP_DONG_BH));
        assertEquals(19, p.columns().get(PayrollField.OT_KHONG_CHIU_THUE)); // cột T
        assertEquals(36, p.columns().get(PayrollField.BHXH_NSDLD));        // cột AK, khác BHXH_NLD (X)
    }

    @Test
    void readsFourteenEmployeeRowsAndStopsAtGrandTotal() throws Exception {
        ParsedPayroll p = parse();
        assertEquals(14, p.lines().size()); // dòng 10–23 (cột STT nhảy từ 4 lên 6)
        PayrollLine first = p.lines().get(0);
        assertEquals(10, first.excelRow());
        assertEquals("DOIT2401", first.employeeCode());
        assertEquals(0, new BigDecimal("16546425").compareTo(first.get(PayrollField.THUC_NHAN)));
        assertEquals(0, new BigDecimal("55075").compareTo(first.get(PayrollField.THUE_TNCN)));
        BigDecimal totalNet = p.lines().stream().map(l -> l.get(PayrollField.THUC_NHAN)).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("219821925").compareTo(totalNet), "Khớp dòng GRAND TOTAL của file");
    }

    @Test
    void detectsPeriodMismatchDuplicatesAndMissingCodes() throws Exception {
        ParsedPayroll p = parse();
        assertEquals(Optional.of(YearMonth.of(2025, 8)), p.periodInFile());
        List<Issue> issues = new PayrollValidator().validate(p, YearMonth.of(2026, 6), DIRECTORY);
        issues.forEach(System.out::println);

        assertTrue(issues.stream().anyMatch(i -> i.excelRow() == 0 && i.message().contains("08/2025")));
        assertEquals(Set.of(14, 18), rowsWith(issues, "DOIT2508", "Mã bị trùng"));
        assertEquals(Set.of(15, 19), rowsWith(issues, "DOIT2513", "Mã bị trùng"));
        assertEquals(Set.of(21, 22, 23), rowsWith(issues, null, "Thiếu mã nhân viên"));
        assertEquals(Set.of(12, 16), rowsWith(issues, null, "không đóng BHXH"));
        // Người thứ hai dùng chung mã → tên không khớp hồ sơ
        assertEquals(Set.of(18), rowsWith(issues, "DOIT2508", "khác hồ sơ"));
        assertTrue(issues.stream().noneMatch(i -> i.excelRow() > 0 && i.excelRow() < 10), "Không đọc nhầm dòng tiêu đề");
        // Số liệu nội bộ của file khớp nhau → không có cảnh báo lệch tổng
        assertTrue(issues.stream().noneMatch(i -> i.message().contains("không khớp tổng")));
    }

    private static Set<Integer> rowsWith(List<Issue> issues, String code, String text) {
        return issues.stream()
                .filter(i -> code == null || code.equals(i.employeeCode()))
                .filter(i -> i.message().contains(text))
                .map(Issue::excelRow)
                .collect(java.util.stream.Collectors.toSet());
    }
}

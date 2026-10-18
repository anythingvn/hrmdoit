package vn.doit.hr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.AssignmentDecision;
import vn.doit.hr.assignment.WorkLocationType;
import vn.doit.hr.contract.ContractData;
import vn.doit.hr.contract.ContractGenerator;
import vn.doit.hr.contract.DocxTemplateRenderer;
import vn.doit.hr.payroll.Issue;
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.PayrollValidator;
import vn.doit.hr.payroll.PoiSheetGrid;

/**
 * Chạy thật các lớp dùng Apache POI / poi-tl.
 * Test đọc Excel chỉ chạy khi có file bảng lương gốc (đặt cạnh thư mục project, hoặc chỉ định bằng -Dpayroll.file=...).
 */
class PoiIntegrationTest {

    private static final Path REAL_FILE = Path.of(System.getProperty("payroll.file",
            "../DO it - Bang luong thang 06_2026 them luon.xlsx"));

    @Test
    void readsRealExcelWithPoi() throws Exception {
        assumeTrue(Files.exists(REAL_FILE), "Không có file bảng lương gốc – bỏ qua");
        ParsedPayroll p;
        try (InputStream in = Files.newInputStream(REAL_FILE)) {
            p = new PayrollSheetParser().parse(PoiSheetGrid.read(in));
        }
        assertEquals(List.of(), p.issues(), "Đọc file không được có lỗi cấu trúc");
        assertEquals(PayrollField.values().length, p.columns().size());
        assertEquals(14, p.lines().size());
        assertEquals(Optional.of(YearMonth.of(2025, 8)), p.periodInFile());

        PayrollLine first = p.lines().get(0);
        assertEquals("DOIT2401", first.employeeCode());
        assertEquals(LocalDate.of(2024, 9, 1), first.contractStartDate(), "Ô ngày phải đọc ra LocalDate");
        assertEquals(0, new BigDecimal("16546425").compareTo(first.get(PayrollField.THUC_NHAN)), "Giá trị công thức đã tính sẵn");
        BigDecimal totalNet = p.lines().stream().map(l -> l.get(PayrollField.THUC_NHAN)).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("219821925").compareTo(totalNet), "Khớp GRAND TOTAL");

        List<Issue> issues = new PayrollValidator().validate(p, YearMonth.of(2026, 6), code -> Optional.of("?"));
        long dup = issues.stream().filter(i -> i.message().contains("Mã bị trùng")).count();
        long missing = issues.stream().filter(i -> i.message().contains("Thiếu mã nhân viên")).count();
        assertEquals(4, dup);
        assertEquals(3, missing);
        assertTrue(issues.stream().noneMatch(i -> i.message().contains("không khớp tổng")));
    }

    @Test
    void rendersContractTemplateWithPoiTl() throws Exception {
        ContractData data = new ContractData("DOIT/2026/HĐLĐ/0001", "Nguyễn Văn A", "Nam", LocalDate.of(1995, 3, 15),
                "Phường Cầu Giấy, Hà Nội", "0900000000", "a@doit.com.vn", "Đại học", "001095000000",
                LocalDate.of(2021, 5, 10), "Cục Cảnh sát QLHC về TTXH", "0123456789", "Vietcombank",
                12, LocalDate.of(2026, 1, 1), "Phòng Phát triển phần mềm", "Lập trình viên", 5_700_000,
                List.of(new ContractData.Allowance("Phụ cấp xăng xe", 1_000_000)),
                "Xét nâng lương mỗi năm một lần", "Trần Ngọc Nga");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream tpl = getClass().getResourceAsStream("/templates/DOIT_HDLD_template.docx")) {
            new ContractGenerator().render(tpl, data, out);
        }
        String text = textOf(out.toByteArray());
        assertFalse(text.contains("{{"), "Còn thẻ chưa điền");
        assertTrue(text.contains("NGUYỄN VĂN A"));
        assertTrue(text.contains("31/12/2026"));
        assertTrue(text.contains("Năm triệu bảy trăm nghìn đồng"));
        Files.write(Path.of("target", "HDLD_test.docx"), out.toByteArray());
    }

    @Test
    void rendersDecisionTemplateWithPoiTl() throws Exception {
        Assignment a = new Assignment(0, "DOIT2401", WorkLocationType.KHACH_HANG, "Ngân hàng ABC", "Hà Nội",
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 31), "15/2026/QĐ-DOIT", null);
        AssignmentDecision dec = new AssignmentDecision("15/2026/QĐ-DOIT", LocalDate.of(2026, 10, 28), "Trần Ngọc Nga",
                "Giám đốc", "Nguyễn Văn A", "DOIT2401", "Lập trình viên", "Phòng Phát triển phần mềm",
                "DOIT/2026/HĐLĐ/0001", a, "Triển khai dự án", "Giữ nguyên lương");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream tpl = getClass().getResourceAsStream("/templates/DOIT_QD_CuNhanSu_template.docx")) {
            new DocxTemplateRenderer().render(tpl, dec.toFields(), out);
        }
        String text = textOf(out.toByteArray());
        assertFalse(text.contains("{{"), "Còn thẻ chưa điền");
        assertTrue(text.contains("Ngân hàng ABC"));
        assertTrue(text.contains("ngày 28 tháng 10 năm 2026"));
        Files.write(Path.of("target", "QD_test.docx"), out.toByteArray());
    }

    private static String textOf(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }
}

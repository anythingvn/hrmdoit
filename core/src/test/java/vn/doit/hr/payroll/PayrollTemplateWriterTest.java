package vn.doit.hr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** File mẫu bảng lương để tải về: bộ đọc nhận đủ mọi cột; điền một dòng thì đọc được dòng đó. */
class PayrollTemplateWriterTest {

    @Test
    void templateHasEveryColumnTheParserReads() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PayrollTemplateWriter().write(out);

        ParsedPayroll empty = new PayrollSheetParser().parse(PoiSheetGrid.read(new ByteArrayInputStream(out.toByteArray())));
        assertEquals(PayrollField.values().length, empty.columns().size());
        assertTrue(empty.periodInFile().isEmpty(), "chưa ghi kỳ");
        assertEquals(List.of("Không có dòng nhân viên nào."), empty.issues().stream().map(Issue::message).toList());

        byte[] filled;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray())); ByteArrayOutputStream o2 = new ByteArrayOutputStream()) {
            assertEquals(List.of("Bảng lương", "Hướng dẫn"), List.of(wb.getSheetName(0), wb.getSheetName(1)));
            Sheet s = wb.getSheet("Bảng lương");
            ParsedPayroll layout = empty;
            int firstDataRow = s.getLastRowNum() + 1;
            Row r = s.createRow(firstDataRow);
            r.createCell(1).setCellValue("DOIT2601");
            r.createCell(2).setCellValue("Nguyễn Văn Mẫu");
            r.createCell(layout.columns().get(PayrollField.LUONG_CO_BAN)).setCellValue(15_000_000);
            r.createCell(layout.columns().get(PayrollField.THUC_NHAN)).setCellValue(13_500_000);
            wb.write(o2);
            filled = o2.toByteArray();
        }
        ParsedPayroll one = new PayrollSheetParser().parse(PoiSheetGrid.read(new ByteArrayInputStream(filled)));
        assertEquals(1, one.lines().size());
        assertEquals("DOIT2601", one.lines().get(0).employeeCode());
        assertEquals(0, one.lines().get(0).get(PayrollField.LUONG_CO_BAN).compareTo(BigDecimal.valueOf(15_000_000)));
    }

    @Test
    void prefillWritesDays() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PayrollTemplateWriter()
            .write(
                out,
                List.of(
                    new PayrollTemplateWriter.PrefillRow(
                        "DOIT2601",
                        "Nguyễn Văn Mẫu",
                        "Lập trình viên",
                        LocalDate.of(2025, 3, 1),
                        new BigDecimal("21"),
                        new BigDecimal("20.50")
                    ),
                    new PayrollTemplateWriter.PrefillRow("TT2601", "Trần Thực Tập", null, null, null, null)
                )
            );

        ParsedPayroll parsed = new PayrollSheetParser().parse(PoiSheetGrid.read(new ByteArrayInputStream(out.toByteArray())));
        assertEquals(2, parsed.lines().size());
        PayrollLine first = parsed.lines().get(0);
        assertEquals("DOIT2601", first.employeeCode());
        assertEquals("Nguyễn Văn Mẫu", first.fullName());
        assertEquals("Lập trình viên", first.position());
        assertEquals(LocalDate.of(2025, 3, 1), first.contractStartDate());
        assertEquals(0, first.amounts().get(PayrollField.NGAY_CONG_CHUAN).compareTo(new BigDecimal("21")));
        assertEquals(0, first.amounts().get(PayrollField.NGAY_CONG_THUC_TE).compareTo(new BigDecimal("20.5")));

        PayrollLine intern = parsed.lines().get(1);
        assertEquals("TT2601", intern.employeeCode());
        assertFalse(intern.amounts().containsKey(PayrollField.NGAY_CONG_CHUAN), "số ngày null thì ô để trống");
        assertFalse(intern.amounts().containsKey(PayrollField.NGAY_CONG_THUC_TE), "số ngày null thì ô để trống");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Row r = wb.getSheet("Bảng lương").getRow(parsed.lines().get(1).excelRow() - 1);
            assertNull(r.getCell(parsed.columns().get(PayrollField.NGAY_CONG_CHUAN)), "ô trống, không phải số 0");
        }
    }

    @Test
    void prefillWithPeriodWritesPeriodTitle() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PayrollTemplateWriter().write(out, YearMonth.of(2026, 11), List.of());
        ParsedPayroll parsed = new PayrollSheetParser().parse(PoiSheetGrid.read(new ByteArrayInputStream(out.toByteArray())));
        assertEquals(java.util.Optional.of(YearMonth.of(2026, 11)), parsed.periodInFile());
    }
}

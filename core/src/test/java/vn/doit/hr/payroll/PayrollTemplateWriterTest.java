package vn.doit.hr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
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
}

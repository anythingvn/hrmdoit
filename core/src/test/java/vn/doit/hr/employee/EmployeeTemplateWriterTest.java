package vn.doit.hr.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import vn.doit.hr.payroll.PoiSheetGrid;

/** File mẫu nhập nhân viên: bộ đọc nhận đủ cột, ô chữ giữ số 0 đầu, có danh sách chọn và sheet hướng dẫn. */
class EmployeeTemplateWriterTest {

    private static byte[] template() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new EmployeeTemplateWriter().write(out);
        return out.toByteArray();
    }

    @Test
    void templateRoundTrips() throws Exception {
        byte[] bytes = template();
        byte[] filled;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes)); ByteArrayOutputStream o2 = new ByteArrayOutputStream()) {
            assertEquals(List.of("Danh sách nhân viên", "Hướng dẫn"), List.of(wb.getSheetName(0), wb.getSheetName(1)));
            Sheet s = wb.getSheetAt(0);
            ParsedEmployees empty = new EmployeeSheetParser().parse(PoiSheetGrid.of(s));
            assertEquals(EmployeeField.values().length, empty.columns().size());
            assertTrue(empty.rows().isEmpty());
            assertTrue(empty.issues().isEmpty(), empty.issues().toString());

            Row r = s.getRow(1) != null ? s.getRow(1) : s.createRow(1);
            r.getCell(empty.columns().get(EmployeeField.FULL_NAME), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellValue("Nguyễn Văn Mẫu");
            r.getCell(empty.columns().get(EmployeeField.ID_NUMBER), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellValue("001090000001");
            wb.write(o2);
            filled = o2.toByteArray();
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(filled))) {
            ParsedEmployees one = new EmployeeSheetParser().parse(PoiSheetGrid.of(wb.getSheetAt(0)));
            assertEquals(1, one.rows().size(), one.issues().toString());
            assertEquals("Nguyễn Văn Mẫu", one.rows().get(0).text(EmployeeField.FULL_NAME));
            assertEquals("001090000001", one.rows().get(0).text(EmployeeField.ID_NUMBER));
        }
    }

    @Test
    void textColumnsAndDropdowns() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            Sheet s = wb.getSheetAt(0);
            assertTrue(s.getPaneInformation().isFreezePane());
            Row r = s.getRow(1);
            for (EmployeeField f : EmployeeField.values()) {
                if (f.textFormat()) {
                    assertEquals("@", r.getCell(f.ordinal()).getCellStyle().getDataFormatString(), f.header());
                }
            }
            List<String> found = new ArrayList<>();
            for (DataValidation v : s.getDataValidations()) {
                int col = v.getRegions().getCellRangeAddress(0).getFirstColumn();
                EmployeeField f = EmployeeField.values()[col];
                org.apache.poi.ss.util.AreaReference ref = new org.apache.poi.ss.util.AreaReference(
                        v.getValidationConstraint().getFormula1(), wb.getSpreadsheetVersion());
                List<String> shown = new ArrayList<>();
                for (org.apache.poi.ss.util.CellReference cr : ref.getAllReferencedCells()) {
                    shown.add(wb.getSheet(cr.getSheetName()).getRow(cr.getRow()).getCell(cr.getCol()).getStringCellValue());
                }
                assertEquals(f.choices(), shown, f.header());
                found.add(f.name());
            }
            assertEquals(List.of("PERSON_TYPE", "GENDER", "INSURANCE_EXEMPTION"), found.stream().sorted(
                    java.util.Comparator.comparingInt(n -> EmployeeField.valueOf(n).ordinal())).toList());
        }
    }

    @Test
    void guideHasOneLinePerField() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            Sheet g = wb.getSheet("Hướng dẫn");
            List<String> colA = new ArrayList<>();
            StringBuilder all = new StringBuilder();
            for (Row row : g) {
                if (row.getCell(0) != null) {
                    colA.add(row.getCell(0).getStringCellValue());
                    all.append(row.getCell(0).getStringCellValue()).append('\n');
                }
            }
            for (EmployeeField f : EmployeeField.values()) {
                assertEquals(1, colA.stream().filter(f.header()::equals).count(), f.header());
            }
            String text = all.toString();
            assertTrue(text.contains("DOIT") && text.contains("TT") && text.contains("HDDV"));
        }
    }
}

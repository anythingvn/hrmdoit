package vn.doit.hrm.service.employee;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vn.doit.hr.employee.EmployeeField;
import vn.doit.hr.employee.EmployeeSheetParser;
import vn.doit.hr.employee.EmployeeTemplateWriter;

/** Dựng file nhập nhân viên giả (tên và số giả) từ file mẫu, để kiểm thử. */
public final class EmployeeImportFiles {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private EmployeeImportFiles() {}

    /** Mỗi phần tử là một dòng dữ liệu (từ dòng 2); số ghi thành ô số, ngày ghi thành chữ dd/MM/yyyy. */
    public static byte[] xlsx(List<Map<EmployeeField, Object>> rows) {
        try {
            ByteArrayOutputStream template = new ByteArrayOutputStream();
            new EmployeeTemplateWriter().write(template);
            try (
                XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template.toByteArray()));
                ByteArrayOutputStream out = new ByteArrayOutputStream()
            ) {
                Sheet sheet = wb.getSheet(EmployeeSheetParser.SHEET);
                int r = 1;
                for (Map<EmployeeField, Object> values : rows) {
                    Row row = sheet.getRow(r) != null ? sheet.getRow(r) : sheet.createRow(r);
                    for (Map.Entry<EmployeeField, Object> e : values.entrySet()) {
                        int c = e.getKey().ordinal();
                        Cell cell = row.getCell(c) != null ? row.getCell(c) : row.createCell(c);
                        Object v = e.getValue();
                        if (v instanceof Number n) {
                            cell.setCellValue(n.doubleValue());
                        } else if (v instanceof LocalDate d) {
                            cell.setCellValue(d.format(DMY));
                        } else if (v != null) {
                            cell.setCellValue(v.toString());
                        }
                    }
                    r++;
                }
                wb.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

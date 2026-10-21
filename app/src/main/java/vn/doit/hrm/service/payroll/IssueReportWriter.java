package vn.doit.hrm.service.payroll;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vn.doit.hrm.service.payroll.dto.IssueDTO;

/** Báo cáo lỗi / cảnh báo của một lần import thành file Excel để Kế toán sửa file gốc. */
final class IssueReportWriter {

    private IssueReportWriter() {}

    /** @param namesByRow họ tên trong file theo dòng Excel */
    static byte[] write(List<IssueDTO> issues, Map<Integer, String> namesByRow) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Lỗi và cảnh báo");
            String[] headers = { "Dòng", "Mã", "Họ tên", "Mức độ", "Nội dung" };
            Row h = sh.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (IssueDTO i : issues) {
                Row row = sh.createRow(r++);
                if (i.excelRow() > 0) {
                    row.createCell(0).setCellValue(i.excelRow());
                } else {
                    row.createCell(0).setCellValue("File");
                }
                row.createCell(1).setCellValue(i.employeeCode() == null ? "" : i.employeeCode());
                row.createCell(2).setCellValue(namesByRow.getOrDefault(i.excelRow(), ""));
                row.createCell(3).setCellValue("ERROR".equals(i.severity()) ? "Lỗi" : "Cảnh báo");
                row.createCell(4).setCellValue((i.fieldLabel() == null ? "" : i.fieldLabel() + ": ") + i.message());
            }
            sh.setColumnWidth(2, 28 * 256);
            sh.setColumnWidth(4, 100 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

package vn.doit.hr.payroll;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * File mẫu bảng lương để tải về (công ty hoặc người mới dùng phần mềm): sheet "Bảng lương" có đúng các tiêu đề cột mà
 * {@link PayrollSheetParser} đọc được, dựng từ {@link PayrollField} nên luôn khớp; sheet "Hướng dẫn" giải thích từng cột.
 *
 * <p>Bản điền sẵn ({@link #write(OutputStream, YearMonth, List)}): mỗi người một dòng với mã, họ tên, vị trí, ngày ký HĐLĐ và hai
 * cột ngày công lấy từ bảng công đã chốt; kế toán điền phần còn lại rồi tải lên như file mẫu trống.
 */
public class PayrollTemplateWriter {

    /** Dòng tiêu đề chính (0-based); dòng ngay dưới là tiêu đề phụ, dữ liệu bắt đầu sau đó. */
    static final int HEADER_ROW = 3;
    static final String[] IDENTITY = {"STT", "Mã nhân viên", "Họ và tên", "Vị trí công việc", "Ngày bắt đầu ký HĐLĐ"};

    /**
     * Một dòng điền sẵn. {@code standardDays}, {@code actualDays} null (thực tập, HĐ dịch vụ: không chấm công) thì ô để trống; các
     * trường chữ / ngày null cũng để trống.
     */
    public record PrefillRow(
            String code, String name, String position, LocalDate contractStart, BigDecimal standardDays, BigDecimal actualDays) {}

    /** File mẫu trống. */
    public void write(OutputStream out) throws IOException {
        write(out, null, List.of());
    }

    /** File mẫu điền sẵn các dòng {@code rows} (theo đúng thứ tự), không ghi kỳ lương. */
    public void write(OutputStream out, List<PrefillRow> rows) throws IOException {
        write(out, null, rows);
    }

    /** File mẫu điền sẵn các dòng {@code rows}; {@code period} khác null thì ghi "Tháng MM/YYYY" ở ô kỳ lương. */
    public void write(OutputStream out, YearMonth period, List<PrefillRow> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle title = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            bold.setFontHeightInPoints((short) 14);
            title.setFont(bold);

            CellStyle header = headerStyle(wb, IndexedColors.PALE_BLUE);
            CellStyle required = headerStyle(wb, IndexedColors.LIGHT_ORANGE);

            Sheet s = wb.createSheet(PoiSheetGrid.DEFAULT_SHEET);
            Row r0 = s.createRow(0);
            r0.createCell(0).setCellValue("BẢNG LƯƠNG NHÂN VIÊN");
            r0.getCell(0).setCellStyle(title);
            s.createRow(1).createCell(0).setCellValue(period == null
                    ? "Ghi kỳ lương vào ô này, dạng: Tháng MM/YYYY"
                    : String.format("Tháng %02d/%d", period.getMonthValue(), period.getYear()));

            Row h = s.createRow(HEADER_ROW);
            Row sub = s.createRow(HEADER_ROW + 1);
            h.setHeightInPoints(48);
            sub.setHeightInPoints(32);
            int c = 0;
            for (String name : IDENTITY) {
                h.createCell(c).setCellValue(name);
                h.getCell(c).setCellStyle(name.equals("Mã nhân viên") || name.equals("Họ và tên") ? required : header);
                sub.createCell(c).setCellStyle(header);
                s.setColumnWidth(c, (name.equals("Họ và tên") ? 28 : 16) * 256);
                c++;
            }
            for (PayrollField f : PayrollField.values()) {
                h.createCell(c).setCellValue(f.header());
                h.getCell(c).setCellStyle(f.required() ? required : header);
                sub.createCell(c).setCellValue(f.subHeader() == null ? "" : f.subHeader());
                sub.getCell(c).setCellStyle(header);
                s.setColumnWidth(c, 18 * 256);
                c++;
            }
            s.createFreezePane(3, HEADER_ROW + 2);
            writeRows(wb, s, rows);

            Sheet guide = wb.createSheet("Hướng dẫn");
            guide.setColumnWidth(0, 40 * 256);
            guide.setColumnWidth(1, 22 * 256);
            guide.setColumnWidth(2, 90 * 256);
            int g = 0;
            String[] lines = {
                "Cách dùng file mẫu bảng lương",
                "1. Mỗi người một dòng ở sheet \"Bảng lương\", từ dòng " + (HEADER_ROW + 3) + " trở xuống. Không đổi tên tiêu đề cột; được thêm cột riêng của công ty (hệ thống bỏ qua).",
                "2. \"Mã nhân viên\" phải trùng mã hồ sơ trên hệ thống (người chưa có hồ sơ: gắn hoặc tạo hồ sơ ở bước Kiểm tra khi import).",
                "3. Ô số: nhập số hoặc công thức Excel; hệ thống đọc giá trị đã tính. Ô trống được hiểu là 0. Lưu file bằng Excel hoặc LibreOffice.",
                "4. Cột tô cam là bắt buộc; thiếu cột không bắt buộc thì coi như bằng 0.",
                "5. Người không thuộc diện đóng BHXH bắt buộc (lương hưu, cộng tác viên...): để trống các cột bảo hiểm và đánh dấu trên hồ sơ.",
                "6. Có thể thêm dòng \"Tổng cộng\" ở cuối; hệ thống dừng đọc tại dòng đó.",
            };
            for (String line : lines) {
                guide.createRow(g++).createCell(0).setCellValue(line);
            }
            guide.getRow(0).getCell(0).setCellStyle(title);
            g++;
            Row gh = guide.createRow(g++);
            gh.createCell(0).setCellValue("Cột");
            gh.createCell(1).setCellValue("Bắt buộc");
            gh.createCell(2).setCellValue("Ghi chú");
            for (int i = 0; i < 3; i++) {
                gh.getCell(i).setCellStyle(header);
            }
            for (String name : IDENTITY) {
                Row row = guide.createRow(g++);
                row.createCell(0).setCellValue(name);
                boolean must = name.equals("Mã nhân viên") || name.equals("Họ và tên");
                row.createCell(1).setCellValue(must ? "Có" : "Không");
                row.createCell(2).setCellValue(name.equals("Ngày bắt đầu ký HĐLĐ") ? "Ngày (định dạng ngày của Excel)" : "Chữ");
            }
            for (PayrollField f : PayrollField.values()) {
                Row row = guide.createRow(g++);
                row.createCell(0).setCellValue(f.label());
                row.createCell(1).setCellValue(f.required() ? "Có" : "Không");
                row.createCell(2).setCellValue("Số (đồng, hoặc ngày công / số người với các cột tương ứng)");
            }
            wb.write(out);
        }
    }

    private static void writeRows(XSSFWorkbook wb, Sheet s, List<PrefillRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        CellStyle date = wb.createCellStyle();
        date.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
        int standardCol = IDENTITY.length + PayrollField.NGAY_CONG_CHUAN.ordinal();
        int actualCol = IDENTITY.length + PayrollField.NGAY_CONG_THUC_TE.ordinal();
        int r = HEADER_ROW + 2;
        int stt = 1;
        for (PrefillRow p : rows) {
            Row row = s.createRow(r++);
            row.createCell(0).setCellValue(stt++);
            text(row, 1, p.code());
            text(row, 2, p.name());
            text(row, 3, p.position());
            if (p.contractStart() != null) {
                Cell c = row.createCell(4);
                c.setCellValue(p.contractStart());
                c.setCellStyle(date);
            }
            number(row, standardCol, p.standardDays());
            number(row, actualCol, p.actualDays());
        }
    }

    private static void text(Row row, int col, String value) {
        if (value != null) {
            row.createCell(col).setCellValue(value);
        }
    }

    private static void number(Row row, int col, BigDecimal value) {
        if (value != null) {
            row.createCell(col).setCellValue(value.doubleValue());
        }
    }

    private static CellStyle headerStyle(XSSFWorkbook wb, IndexedColors color) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        st.setFont(f);
        st.setWrapText(true);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setFillForegroundColor(color.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
        return st;
    }
}

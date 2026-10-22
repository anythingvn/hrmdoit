package vn.doit.hr.employee;

import java.io.IOException;
import java.io.OutputStream;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * File mẫu nhập danh sách nhân viên: sheet "Danh sách nhân viên" có đúng các tiêu đề cột mà {@link EmployeeSheetParser}
 * đọc được, dựng từ {@link EmployeeField} nên luôn khớp; sheet "Hướng dẫn" giải thích từng cột và quy tắc mã tự gán.
 */
public class EmployeeTemplateWriter {

    /** Dòng cuối (0-based) được định dạng và gắn danh sách chọn: 1 dòng tiêu đề + MAX_ROWS dòng dữ liệu. */
    private static final String LISTS_SHEET = "DanhMuc";
    private static final int LAST_ROW = EmployeeSheetParser.MAX_ROWS;

    public void write(OutputStream out) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb, IndexedColors.PALE_BLUE);
            CellStyle required = headerStyle(wb, IndexedColors.LIGHT_ORANGE);
            CellStyle text = wb.createCellStyle();
            DataFormat fmt = wb.createDataFormat();
            text.setDataFormat(fmt.getFormat("@"));
            CellStyle title = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            bold.setFontHeightInPoints((short) 14);
            title.setFont(bold);

            Sheet s = wb.createSheet(EmployeeSheetParser.SHEET);
            Row h = s.createRow(0);
            h.setHeightInPoints(36);
            EmployeeField[] fields = EmployeeField.values();
            for (EmployeeField f : fields) {
                int c = f.ordinal();
                h.createCell(c).setCellValue(f.header());
                h.getCell(c).setCellStyle(f.required() ? required : header);
                s.setColumnWidth(c, (f == EmployeeField.RESIDENCE || f == EmployeeField.NOTE ? 36 : 20) * 256);
            }
            // Định dạng chữ để giữ số 0 đầu (CCCD, điện thoại, số tài khoản...).
            for (int r = 1; r <= LAST_ROW; r++) {
                Row row = s.createRow(r);
                for (EmployeeField f : fields) {
                    if (f.textFormat()) {
                        row.createCell(f.ordinal()).setCellStyle(text);
                    }
                }
            }
            // Danh sách chọn lấy từ sheet ẩn vì có lựa chọn chứa dấu phẩy (danh sách nhập thẳng sẽ bị tách đôi).
            Sheet guide = wb.createSheet("Hướng dẫn");
            Sheet lists = wb.createSheet(LISTS_SHEET);
            DataValidationHelper dv = s.getDataValidationHelper();
            int listCol = 0;
            for (EmployeeField f : fields) {
                if (f.choices().isEmpty()) {
                    continue;
                }
                for (int i = 0; i < f.choices().size(); i++) {
                    Row lr = lists.getRow(i) != null ? lists.getRow(i) : lists.createRow(i);
                    lr.createCell(listCol).setCellValue(f.choices().get(i));
                }
                String col = CellReference.convertNumToColString(listCol);
                DataValidationConstraint list = dv.createFormulaListConstraint(
                        "'" + LISTS_SHEET + "'!$" + col + "$1:$" + col + "$" + f.choices().size());
                DataValidation v = dv.createValidation(list,
                        new CellRangeAddressList(1, LAST_ROW, f.ordinal(), f.ordinal()));
                v.setShowErrorBox(true);
                v.setSuppressDropDownArrow(true);
                s.addValidationData(v);
                listCol++;
            }
            s.createFreezePane(0, 1);

            guide.setColumnWidth(0, 32 * 256);
            guide.setColumnWidth(1, 14 * 256);
            guide.setColumnWidth(2, 90 * 256);
            String[] lines = {
                "Cách dùng file mẫu nhập danh sách nhân viên",
                "1. Mỗi người một dòng ở sheet \"" + EmployeeSheetParser.SHEET + "\", từ dòng 2 trở xuống (tối đa "
                        + EmployeeSheetParser.MAX_ROWS + " dòng). Không đổi tên tiêu đề cột.",
                "2. Cột tô cam là bắt buộc. Cột có mũi tên: chọn từ danh sách.",
                "3. Ngày nhập dạng dd/MM/yyyy hoặc ô ngày của Excel. Các cột mã, CCCD, điện thoại, số tài khoản đã đặt dạng chữ để giữ số 0 đầu.",
                "4. Mã NV để trống: hệ thống tự cấp. Nhân viên: DOIT + 2 số cuối năm vào làm + số thứ tự (vd DOIT2601); "
                        + "Thực tập: TT + năm + số (vd TT2601); HĐ dịch vụ: luôn tự cấp HDDV1, HDDV2… "
                        + "Có ghi mã thì giữ nguyên mã đó.",
                "5. Hệ thống kiểm tra từng dòng; chỉ khi mọi dòng hợp lệ mới lưu tất cả, nếu không thì không lưu dòng nào.",
            };
            int g = 0;
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
            for (EmployeeField f : fields) {
                Row row = guide.createRow(g++);
                row.createCell(0).setCellValue(f.header());
                row.createCell(1).setCellValue(f.required() ? "Có" : "Không");
                row.createCell(2).setCellValue(f.hint());
            }
            wb.setSheetHidden(wb.getSheetIndex(lists), true);
            wb.write(out);
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

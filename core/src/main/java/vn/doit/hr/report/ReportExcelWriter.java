package vn.doit.hr.report;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vn.doit.hr.report.EmploymentRecord.ExitType;
import vn.doit.hr.report.PayrollCostReport.Cost;
import vn.doit.hr.report.PayrollCostReport.MonthCost;
import vn.doit.hr.report.TurnoverReport.TenureBand;

/**
 * Xuất {@link HrReport} ra file Excel 4 sheet: Tổng hợp, Biến động, Nghỉ việc, Chi phí lương.
 * Dòng tổng dùng công thức SUM để người xem đối chiếu được ngay trong Excel.
 */
public class ReportExcelWriter {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Workbook wb;
    private CellStyle title;
    private CellStyle plain;
    private CellStyle subtitle;
    private CellStyle header;
    private CellStyle text;
    private CellStyle money;
    private CellStyle integer;
    private CellStyle percent;
    private CellStyle totalText;
    private CellStyle totalMoney;
    private CellStyle totalInt;
    private CellStyle totalDecimal;

    public void write(HrReport report, OutputStream out) throws IOException {
        try (Workbook w = new XSSFWorkbook()) {
            this.wb = w;
            initStyles();
            summarySheet(report);
            headcountSheet(report);
            turnoverSheet(report);
            costSheet(report);
            wb.setForceFormulaRecalculation(true);
            wb.write(out);
        }
    }

    // ------------------------------------------------------------------ sheets

    private void summarySheet(HrReport r) {
        Sheet s = wb.createSheet("Tổng hợp");
        int row = heading(s, r, "BÁO CÁO NHÂN SỰ");
        row = tableHeader(s, row, "Chỉ tiêu", "Giá trị");
        HeadcountReport.Summary m = r.summary();
        row = kv(s, row, "Nhân sự đầu kỳ", m.opening(), integer);
        row = kv(s, row, "Tuyển mới", m.hires(), integer);
        row = kv(s, row, "Nghỉ việc", m.leavers(), integer);
        row = kv(s, row, "   trong đó tự nghỉ", m.voluntaryLeavers(), integer);
        row = kv(s, row, "Nhân sự cuối kỳ", m.closing(), integer);
        row = kv(s, row, "Nhân sự bình quân", m.averageHeadcount().doubleValue(), oneDecimal());
        row = kv(s, row, "Tỷ lệ nghỉ việc", m.turnoverRate().doubleValue(), percent);
        row = kv(s, row, "Tỷ lệ tự nghỉ việc", m.voluntaryTurnoverRate().doubleValue(), percent);
        row = kv(s, row, "Nghỉ việc trong 6 tháng đầu", r.turnover().earlyLeavers(), integer);
        row = kv(s, row, "Tổng chi phí lương (các tháng có bảng lương)", r.totalPayrollCost().doubleValue(), money);
        row = kv(s, row, "Chi phí bình quân / người / tháng", r.averageCostPerHeadPerMonth().doubleValue(), money);
        row++;
        note(s, row, "Tỷ lệ nghỉ việc = số người nghỉ ÷ nhân sự bình quân. Nhân sự bình quân = trung bình (đầu kỳ + cuối kỳ) / 2 của từng tháng.");
        widths(s, 48, 22);
    }

    private void headcountSheet(HrReport r) {
        Sheet s = wb.createSheet("Biến động");
        int row = heading(s, r, "BIẾN ĐỘNG NHÂN SỰ THEO THÁNG");
        int first = row + 2; // số dòng Excel (1-based) của dòng dữ liệu đầu tiên
        row = tableHeader(s, row, "Tháng", "Đầu kỳ", "Tuyển mới", "Nghỉ việc", "Tự nghỉ", "Cuối kỳ", "Bình quân", "Tỷ lệ nghỉ việc");
        for (HeadcountReport.MonthRow m : r.headcount()) {
            Row x = s.createRow(row++);
            str(x, 0, month(m.month()), text);
            num(x, 1, m.opening(), integer);
            num(x, 2, m.hires(), integer);
            num(x, 3, m.leavers(), integer);
            num(x, 4, m.voluntaryLeavers(), integer);
            num(x, 5, m.closing(), integer);
            num(x, 6, m.averageHeadcount().doubleValue(), oneDecimal());
            num(x, 7, m.turnoverRate().doubleValue(), percent);
        }
        Row t = s.createRow(row++);
        str(t, 0, "Cả kỳ", totalText);
        num(t, 1, r.summary().opening(), totalInt);
        sum(t, 2, first, row - 1, totalInt);
        sum(t, 3, first, row - 1, totalInt);
        sum(t, 4, first, row - 1, totalInt);
        num(t, 5, r.summary().closing(), totalInt);
        num(t, 6, r.summary().averageHeadcount().doubleValue(), totalDecimal);
        num(t, 7, r.summary().turnoverRate().doubleValue(), percent);

        row += 1;
        str(s.createRow(row++), 0, "Nhân sự cuối kỳ theo phòng ban (" + month(r.to()) + ")", subtitle);
        row = countTable(s, row, "Phòng ban", r.closingByDepartment());
        widths(s, 34, 11, 11, 11, 11, 11, 11, 16);
        s.createFreezePane(1, 4);
    }

    private void turnoverSheet(HrReport r) {
        Sheet s = wb.createSheet("Nghỉ việc");
        int row = heading(s, r, "PHÂN TÍCH NGHỈ VIỆC");
        TurnoverReport.Result t = r.turnover();

        str(s.createRow(row++), 0, "Theo hình thức nghỉ", subtitle);
        Map<String, Integer> byType = new java.util.LinkedHashMap<>();
        for (ExitType e : ExitType.values()) {
            byType.put(e.label(), t.byExitType().getOrDefault(e, 0));
        }
        row = countTable(s, row, "Hình thức", byType) + 1;

        str(s.createRow(row++), 0, "Theo thâm niên khi nghỉ", subtitle);
        Map<String, Integer> byTenure = new java.util.LinkedHashMap<>();
        for (TenureBand b : TenureBand.values()) {
            byTenure.put(b.label(), t.byTenure().get(b));
        }
        row = countTable(s, row, "Thâm niên", byTenure) + 1;

        str(s.createRow(row++), 0, "Theo phòng ban", subtitle);
        row = countTable(s, row, "Phòng ban", t.byDepartment()) + 1;

        str(s.createRow(row++), 0, "Theo lý do", subtitle);
        row = countTable(s, row, "Lý do", t.byReason()) + 1;

        str(s.createRow(row++), 0, "Danh sách người nghỉ việc", subtitle);
        row = tableHeader(s, row, "Mã NV", "Họ tên", "Phòng ban", "Vị trí", "Ngày vào", "Ngày nghỉ", "Thâm niên (tháng)", "Hình thức", "Lý do");
        for (EmploymentRecord e : t.leavers()) {
            Row x = s.createRow(row++);
            str(x, 0, e.code(), text);
            str(x, 1, e.fullName(), text);
            str(x, 2, e.department(), text);
            str(x, 3, e.position(), text);
            str(x, 4, date(e.hireDate()), text);
            str(x, 5, date(e.terminationDate()), text);
            num(x, 6, e.tenureMonthsAtExit(), integer);
            str(x, 7, e.exitType() == null ? ExitType.KHAC.label() : e.exitType().label(), text);
            str(x, 8, e.exitReason(), text);
        }
        widths(s, 30, 24, 22, 20, 12, 12, 12, 24, 36);
    }

    private void costSheet(HrReport r) {
        Sheet s = wb.createSheet("Chi phí lương");
        int row = heading(s, r, "CHI PHÍ LƯƠNG");
        String[] cols = {"Số người", "Lương theo ngày công", "Thưởng", "Phụ cấp", "Làm thêm giờ", "Khác",
                "BH công ty đóng", "KPCĐ", "Thừa/thiếu", "Tổng chi phí", "Bình quân/người", "So với tháng trước"};

        str(s.createRow(row++), 0, "Theo tháng", subtitle);
        int first = row + 2;
        row = tableHeader(s, row, concat("Tháng", cols));
        for (MonthCost m : r.costs()) {
            Row x = s.createRow(row++);
            str(x, 0, month(m.month()), text);
            costCells(x, m.total());
            if (m.changeVsPreviousMonth() != null) {
                num(x, 12, m.changeVsPreviousMonth().doubleValue(), percent);
            }
        }
        if (!r.costs().isEmpty()) {
            Row t = s.createRow(row++);
            str(t, 0, "Cả kỳ", totalText);
            for (int c = 1; c <= 10; c++) {
                sum(t, c, first, row - 1, c == 1 ? totalInt : totalMoney);
            }
            num(t, 11, r.averageCostPerHeadPerMonth().doubleValue(), totalMoney);
        }

        for (MonthCost m : r.costs()) {
            row++;
            str(s.createRow(row++), 0, "Theo " + r.costGroupLabel().toLowerCase() + " – " + month(m.month()), subtitle);
            int gFirst = row + 2;
            row = tableHeader(s, row, concat(r.costGroupLabel(), java.util.Arrays.copyOf(cols, 11)));
            for (Map.Entry<String, Cost> g : m.byGroup().entrySet()) {
                Row x = s.createRow(row++);
                str(x, 0, g.getKey(), text);
                costCells(x, g.getValue());
            }
            Row t = s.createRow(row++);
            str(t, 0, "Cộng", totalText);
            for (int c = 1; c <= 10; c++) {
                sum(t, c, gFirst, row - 1, c == 1 ? totalInt : totalMoney);
            }
            if (!m.total().consistentWithFile()) {
                note(s, row++, "Lưu ý: tổng tính lại (" + m.total().totalCost().toPlainString()
                        + ") lệch cột \"Tổng chi phí nhân viên\" trong file (" + m.total().totalCostInFile().toPlainString() + ").");
            }
        }
        row++;
        note(s, row, "Tổng chi phí = Lương theo ngày công + Thưởng + Phụ cấp + Làm thêm giờ + Khác + BH công ty đóng + KPCĐ + Thừa/thiếu"
                + " (khớp công thức cột \"Tổng chi phí nhân viên\" của bảng lương). Số liệu gồm cả HĐ dịch vụ và thực tập sinh nếu có trong bảng lương.");
        widths(s, 26, 10, 18, 15, 15, 15, 13, 16, 12, 13, 18, 16, 14);
        s.createFreezePane(1, 0);
    }

    // ------------------------------------------------------------------ helpers

    private void costCells(Row x, Cost c) {
        num(x, 1, c.headcount(), integer);
        num(x, 2, c.baseSalary().doubleValue(), money);
        num(x, 3, c.bonus().doubleValue(), money);
        num(x, 4, c.allowances().doubleValue(), money);
        num(x, 5, c.overtime().doubleValue(), money);
        num(x, 6, c.other().doubleValue(), money);
        num(x, 7, c.employerInsurance().doubleValue(), money);
        num(x, 8, c.unionFee().doubleValue(), money);
        num(x, 9, c.adjustments().doubleValue(), money);
        num(x, 10, c.totalCost().doubleValue(), money);
        num(x, 11, c.costPerHead().doubleValue(), money);
    }

    private int heading(Sheet s, HrReport r, String name) {
        str(s.createRow(0), 0, r.companyName(), subtitle);
        str(s.createRow(1), 0, name, title);
        str(s.createRow(2), 0, r.periodLabel(), plain);
        s.getPrintSetup().setLandscape(true);
        s.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        s.getPrintSetup().setFitWidth((short) 1);
        s.getPrintSetup().setFitHeight((short) 0);
        s.setFitToPage(true);
        return 4;
    }

    private int tableHeader(Sheet s, int row, String... names) {
        Row x = s.createRow(row);
        x.setHeightInPoints(32);
        for (int i = 0; i < names.length; i++) {
            str(x, i, names[i], header);
        }
        return row + 1;
    }

    private int kv(Sheet s, int row, String label, double value, CellStyle style) {
        Row x = s.createRow(row);
        str(x, 0, label, text);
        num(x, 1, value, style);
        return row + 1;
    }

    private int countTable(Sheet s, int row, String keyHeader, Map<String, Integer> data) {
        row = tableHeader(s, row, keyHeader, "Số người");
        int first = row + 1;
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            Row x = s.createRow(row++);
            str(x, 0, e.getKey(), text);
            num(x, 1, e.getValue(), integer);
        }
        Row t = s.createRow(row++);
        str(t, 0, "Cộng", totalText);
        if (data.isEmpty()) {
            num(t, 1, 0, totalInt);
        } else {
            sum(t, 1, first, row - 1, totalInt);
        }
        return row;
    }

    private void note(Sheet s, int row, String msg) {
        Row x = s.getRow(row) == null ? s.createRow(row) : s.getRow(row);
        Cell c = x.createCell(0);
        c.setCellValue(msg);
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setItalic(true);
        f.setFontHeightInPoints((short) 10);
        st.setFont(f);
        c.setCellStyle(st);
    }

    private static void str(Row x, int col, String value, CellStyle style) {
        Cell c = x.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static void num(Row x, int col, double value, CellStyle style) {
        Cell c = x.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    /** Ô tổng = SUM của cột từ dòng Excel {@code firstExcelRow} đến {@code lastExcelRow} (1-based). */
    private static void sum(Row x, int col, int firstExcelRow, int lastExcelRow, CellStyle style) {
        String colName = CellReference.convertNumToColString(col);
        Cell c = x.createCell(col);
        c.setCellFormula("SUM(" + colName + firstExcelRow + ":" + colName + lastExcelRow + ")");
        c.setCellStyle(style);
    }

    private static void widths(Sheet s, int... chars) {
        for (int i = 0; i < chars.length; i++) {
            s.setColumnWidth(i, chars[i] * 256);
        }
    }

    private static String[] concat(String first, String... rest) {
        String[] out = new String[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    private static String month(YearMonth m) {
        return String.format("%02d/%d", m.getMonthValue(), m.getYear());
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(D);
    }

    private CellStyle oneDecimal;

    private CellStyle oneDecimal() {
        return oneDecimal;
    }

    private void initStyles() {
        Font bold = wb.createFont();
        bold.setBold(true);
        Font big = wb.createFont();
        big.setBold(true);
        big.setFontHeightInPoints((short) 14);

        plain = wb.createCellStyle();
        title = wb.createCellStyle();
        title.setFont(big);
        subtitle = wb.createCellStyle();
        subtitle.setFont(bold);

        header = bordered();
        header.setFont(bold);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        text = bordered();
        money = bordered();
        money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        integer = bordered();
        integer.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        oneDecimal = bordered();
        oneDecimal.setDataFormat(wb.createDataFormat().getFormat("#,##0.0"));
        percent = bordered();
        percent.setDataFormat(wb.createDataFormat().getFormat("0.0%"));

        totalText = bordered();
        totalText.setFont(bold);
        totalMoney = bordered();
        totalMoney.setFont(bold);
        totalMoney.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        totalInt = bordered();
        totalInt.setFont(bold);
        totalInt.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        totalDecimal = bordered();
        totalDecimal.setFont(bold);
        totalDecimal.setDataFormat(wb.createDataFormat().getFormat("#,##0.0"));
    }

    private CellStyle bordered() {
        CellStyle st = wb.createCellStyle();
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        return st;
    }
}

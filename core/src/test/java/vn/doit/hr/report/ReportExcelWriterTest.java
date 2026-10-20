package vn.doit.hr.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.TsvSheetGrid;

/** Chạy thật ReportExcelWriter (Apache POI) và tính lại công thức SUM để kiểm tra không lệch dòng. */
class ReportExcelWriterTest {

    @Test
    void writesWorkbookWhoseFormulasMatchComputedTotals() throws Exception {
        List<PayrollLine> june = new PayrollSheetParser().parse(TsvSheetGrid.load("/bang-luong-06-2026.tsv")).lines();
        TreeMap<YearMonth, List<PayrollLine>> payrolls = new TreeMap<>();
        payrolls.put(YearMonth.of(2026, 1), new ArrayList<>(june.subList(0, 10)));
        payrolls.put(YearMonth.of(2026, 3), june);
        HrReport report = HrReport.build("CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT",
                YearMonth.of(2026, 1), YearMonth.of(2026, 3), HrReportTest.sample(), payrolls, PayrollLine::position, "Vị trí");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ReportExcelWriter().write(report, out);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "BaoCaoNhanSu_test.xlsx"), out.toByteArray());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals(List.of("Tổng hợp", "Biến động", "Nghỉ việc", "Chi phí lương"),
                    List.of(wb.getSheetName(0), wb.getSheetName(1), wb.getSheetName(2), wb.getSheetName(3)));
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();

            Row movement = findRow(wb.getSheet("Biến động"), "Cả kỳ");
            assertEquals(3.0, value(ev, movement.getCell(2)), "Tổng tuyển mới");
            assertEquals(3.0, value(ev, movement.getCell(3)), "Tổng nghỉ việc");
            assertEquals(2.0, value(ev, movement.getCell(4)), "Tổng tự nghỉ");

            Sheet cost = wb.getSheet("Chi phí lương");
            Row costTotal = findRow(cost, "Cả kỳ");
            double expected = report.totalPayrollCost().doubleValue();
            assertEquals(expected, value(ev, costTotal.getCell(10)), 0.5, "Tổng chi phí cả kỳ");
            assertEquals(24.0, value(ev, costTotal.getCell(1)), "Số người-tháng 10 + 14");

            // Bảng theo vị trí của tháng 03 (dùng nguyên bảng lương 06/2026) phải cộng ra 245.709.000
            Row lastGroupTotal = findLastRow(cost, "Cộng");
            assertEquals(245_709_000.0, value(ev, lastGroupTotal.getCell(10)), 0.5);

            // Tháng 03 không có tháng 02 liền trước → ô "So với tháng trước" để trống
            Row march = findRow(cost, "03/2026");
            assertEquals(null, march.getCell(12), "Không so với tháng 01");
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                assertEquals(true, wb.getSheetAt(i).getPrintSetup().getLandscape(), "In ngang");
                assertEquals(true, wb.getSheetAt(i).getFitToPage(), "Co vừa khổ giấy");
            }

            Row leaversTotal = findRow(wb.getSheet("Nghỉ việc"), "Cộng");
            assertEquals(3.0, value(ev, leaversTotal.getCell(1)), "Bảng theo hình thức nghỉ");
        }
    }

    private static double value(FormulaEvaluator ev, Cell c) {
        assertNotNull(c);
        return c.getCellType() == CellType.FORMULA ? ev.evaluate(c).getNumberValue() : c.getNumericCellValue();
    }

    private static Row findRow(Sheet s, String firstCell) {
        for (Row r : s) {
            Cell c = r.getCell(0);
            if (c != null && c.getCellType() == CellType.STRING && firstCell.equals(c.getStringCellValue())) {
                return r;
            }
        }
        throw new AssertionError("Không thấy dòng \"" + firstCell + "\" trong sheet " + s.getSheetName());
    }

    private static Row findLastRow(Sheet s, String firstCell) {
        Row found = null;
        for (Row r : s) {
            Cell c = r.getCell(0);
            if (c != null && c.getCellType() == CellType.STRING && firstCell.equals(c.getStringCellValue())) {
                found = r;
            }
        }
        if (found == null) {
            throw new AssertionError("Không thấy dòng \"" + firstCell + "\"");
        }
        return found;
    }
}

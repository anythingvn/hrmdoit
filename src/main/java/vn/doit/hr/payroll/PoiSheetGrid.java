package vn.doit.hr.payroll;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.NumberToTextConverter;

/**
 * Đọc sheet Excel bằng Apache POI thành {@link SheetGrid}.
 *
 * <p>Ô công thức lấy <b>giá trị đã lưu sẵn</b> (cached) – đúng cái kế toán thấy khi mở file.
 * Không tự tính lại công thức: file mẫu có tham chiếu bảng (structured reference) và #REF! ở sheet khác,
 * tính lại dễ lỗi và có thể ra số khác Excel. Vì thế file phải được <b>lưu bằng Excel/LibreOffice</b>
 * (file sinh tự động bằng code có thể không có giá trị cache).
 */
public final class PoiSheetGrid implements SheetGrid {

    public static final String DEFAULT_SHEET = "Bảng lương";

    private final Object[][] cells;
    private final int columns;

    private PoiSheetGrid(Object[][] cells, int columns) {
        this.cells = cells;
        this.columns = columns;
    }

    /** Đọc sheet "Bảng lương"; nếu không có thì lấy sheet đầu tiên. */
    public static PoiSheetGrid read(InputStream xlsx) throws IOException {
        try (Workbook wb = WorkbookFactory.create(xlsx)) {
            Sheet sheet = wb.getSheet(DEFAULT_SHEET);
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
            }
            return of(sheet);
        }
    }

    public static PoiSheetGrid of(Sheet sheet) {
        int rows = sheet.getLastRowNum() + 1;
        int cols = 0;
        for (Row row : sheet) {
            cols = Math.max(cols, row.getLastCellNum());
        }
        Object[][] grid = new Object[rows][cols];
        for (Row row : sheet) {
            for (Cell cell : row) {
                grid[cell.getRowIndex()][cell.getColumnIndex()] = valueOf(cell);
            }
        }
        for (CellRangeAddress m : sheet.getMergedRegions()) {
            if (m.getFirstRow() >= rows || m.getFirstColumn() >= cols) {
                continue;
            }
            Object v = grid[m.getFirstRow()][m.getFirstColumn()];
            for (int r = m.getFirstRow(); r <= Math.min(m.getLastRow(), rows - 1); r++) {
                for (int c = m.getFirstColumn(); c <= Math.min(m.getLastColumn(), cols - 1); c++) {
                    grid[r][c] = v;
                }
            }
        }
        return new PoiSheetGrid(grid, cols);
    }

    private static Object valueOf(Cell cell) {
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate()
                    : new BigDecimal(NumberToTextConverter.toText(cell.getNumericCellValue()));
            case STRING -> {
                String s = cell.getStringCellValue();
                yield s.isBlank() ? null : s;
            }
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case ERROR -> new CellError(FormulaError.forInt(cell.getErrorCellValue()).getString());
            default -> null;
        };
    }

    @Override
    public int rowCount() {
        return cells.length;
    }

    @Override
    public int columnCount() {
        return columns;
    }

    @Override
    public Object get(int row, int col) {
        if (row < 0 || row >= cells.length || col < 0 || col >= columns) {
            return null;
        }
        return cells[row][col];
    }
}

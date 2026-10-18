package vn.doit.hr.payroll;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lưới đọc từ TSV có tiền tố kiểu (n:/s:/d:/e:) – fixture xuất từ file Excel thật (đã ẩn danh họ tên),
 * dùng để test logic mà không cần Apache POI.
 */
final class TsvSheetGrid implements SheetGrid {

    private final List<Object[]> rows = new ArrayList<>();
    private int columns;

    static TsvSheetGrid load(String resource) throws IOException {
        TsvSheetGrid g = new TsvSheetGrid();
        try (InputStream in = TsvSheetGrid.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Không thấy " + resource);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1)) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                Object[] row = new Object[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    row[i] = decode(parts[i]);
                }
                g.rows.add(row);
                g.columns = Math.max(g.columns, parts.length);
            }
        }
        return g;
    }

    private static Object decode(String s) {
        if (s.isEmpty()) {
            return null;
        }
        String v = s.substring(2);
        return switch (s.charAt(0)) {
            case 'n' -> new BigDecimal(v);
            case 'd' -> LocalDate.parse(v);
            case 'e' -> new CellError(v);
            default -> v.replace("\\n", "\n");
        };
    }

    @Override
    public int rowCount() {
        return rows.size();
    }

    @Override
    public int columnCount() {
        return columns;
    }

    @Override
    public Object get(int row, int col) {
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        Object[] r = rows.get(row);
        return col < 0 || col >= r.length ? null : r[col];
    }
}

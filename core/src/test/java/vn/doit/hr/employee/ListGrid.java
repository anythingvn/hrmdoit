package vn.doit.hr.employee;

import java.util.List;

import vn.doit.hr.payroll.SheetGrid;

/** Lưới dựng từ danh sách dòng, dùng cho test. */
final class ListGrid implements SheetGrid {

    private final List<List<Object>> rows;
    private final int columns;

    ListGrid(List<List<Object>> rows) {
        this.rows = rows;
        this.columns = rows.stream().mapToInt(List::size).max().orElse(0);
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
        List<Object> r = rows.get(row);
        return col < 0 || col >= r.size() ? null : r.get(col);
    }
}

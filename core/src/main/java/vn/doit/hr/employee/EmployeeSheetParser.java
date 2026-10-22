package vn.doit.hr.employee;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vn.doit.hr.employee.EmployeeIssue.Severity;
import vn.doit.hr.payroll.SheetGrid;

/**
 * Đọc sheet "Danh sách nhân viên".
 *
 * <p>Dòng tiêu đề là dòng đầu tiên (trong 10 dòng đầu) có ô "Họ và tên". Cột nhận theo tên, không phân biệt
 * hoa thường, dấu và khoảng trắng. Mọi dòng sau tiêu đề là dữ liệu; dòng trống hoàn toàn bị bỏ qua.
 * Lỗi định dạng của một ô không chặn các ô khác cùng dòng.
 */
public class EmployeeSheetParser {

    public static final String SHEET = "Danh sách nhân viên";
    public static final int MAX_ROWS = 2000;

    private static final int HEADER_SCAN_ROWS = 10;
    private static final Pattern DATE_TEXT = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");
    private static final Pattern NON_NEGATIVE_INT = Pattern.compile("\\d{1,18}");

    public ParsedEmployees parse(SheetGrid grid) {
        List<EmployeeIssue> issues = new ArrayList<>();
        int headerRow = findHeaderRow(grid);
        if (headerRow < 0) {
            issues.add(EmployeeIssue.fileError(
                    "Không tìm thấy dòng tiêu đề có cột \"" + EmployeeField.FULL_NAME.header() + "\". Sai mẫu file?"));
            return new ParsedEmployees(Map.of(), List.of(), issues);
        }

        Map<EmployeeField, Integer> columns = mapColumns(grid, headerRow, issues);
        for (EmployeeField f : EmployeeField.values()) {
            if (f.required() && !columns.containsKey(f)) {
                issues.add(EmployeeIssue.fileError("Không tìm thấy cột \"" + f.header() + "\"."));
            }
        }
        if (hasError(issues)) {
            return new ParsedEmployees(columns, List.of(), issues);
        }

        List<Integer> dataRows = new ArrayList<>();
        for (int r = headerRow + 1; r < grid.rowCount(); r++) {
            if (!isBlankRow(grid, r)) {
                dataRows.add(r);
            }
        }
        if (dataRows.size() > MAX_ROWS) {
            issues.add(EmployeeIssue.fileError(
                    "File có " + dataRows.size() + " dòng, tối đa " + MAX_ROWS + " dòng mỗi lần nhập"));
            return new ParsedEmployees(columns, List.of(), issues);
        }

        List<EmployeeRow> rows = new ArrayList<>();
        for (int r : dataRows) {
            rows.add(readRow(grid, r, columns, issues));
        }
        return new ParsedEmployees(columns, rows, issues);
    }

    private static EmployeeRow readRow(SheetGrid grid, int r, Map<EmployeeField, Integer> columns,
                                       List<EmployeeIssue> issues) {
        int excelRow = r + 1;
        Integer nameCol = columns.get(EmployeeField.FULL_NAME);
        String fullName = nameCol == null ? null : plainText(grid.get(r, nameCol));
        Map<EmployeeField, Object> values = new EnumMap<>(EmployeeField.class);
        for (Map.Entry<EmployeeField, Integer> e : columns.entrySet()) {
            EmployeeField field = e.getKey();
            Object cell = grid.get(r, e.getValue());
            if (isBlank(cell)) {
                continue;
            }
            if (cell instanceof SheetGrid.CellError err) {
                issues.add(new EmployeeIssue(Severity.ERROR, excelRow, fullName, field,
                        "Ô bị lỗi công thức " + err.code()));
                continue;
            }
            Object value = switch (field.kind()) {
                case TEXT -> readText(field, cell, excelRow, fullName, issues);
                case DATE -> readDate(field, cell, excelRow, fullName, issues);
                case INTEGER -> readInteger(field, cell, excelRow, fullName, issues);
                case CHOICE -> readChoice(field, cell, excelRow, fullName, issues);
            };
            if (value != null) {
                values.put(field, value);
            }
        }
        return new EmployeeRow(excelRow, values);
    }

    private static String readText(EmployeeField field, Object cell, int excelRow, String fullName,
                                   List<EmployeeIssue> issues) {
        String s = plainText(cell);
        if (s == null) {
            return null;
        }
        if (field == EmployeeField.ID_NUMBER && cell instanceof BigDecimal && s.length() == 11) {
            s = "0" + s;
            issues.add(new EmployeeIssue(Severity.WARNING, excelRow, fullName, field,
                    "Đã thêm số 0 ở đầu số CCCD, kiểm tra lại"));
        }
        if (field.maxLength() > 0 && s.length() > field.maxLength()) {
            issues.add(new EmployeeIssue(Severity.ERROR, excelRow, fullName, field,
                    "Dài quá " + field.maxLength() + " ký tự"));
            return null;
        }
        return s;
    }

    private static LocalDate readDate(EmployeeField field, Object cell, int excelRow, String fullName,
                                      List<EmployeeIssue> issues) {
        if (cell instanceof LocalDate d) {
            return d;
        }
        String s = cell.toString().trim();
        Matcher m = DATE_TEXT.matcher(s);
        if (m.matches()) {
            try {
                return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(1)));
            } catch (DateTimeException ex) {
                // rơi xuống báo lỗi bên dưới
            }
        }
        issues.add(new EmployeeIssue(Severity.ERROR, excelRow, fullName, field,
                "Ngày không hợp lệ: \"" + s + "\" (dạng dd/MM/yyyy)"));
        return null;
    }

    private static Long readInteger(EmployeeField field, Object cell, int excelRow, String fullName,
                                    List<EmployeeIssue> issues) {
        String s = cell instanceof BigDecimal n ? n.stripTrailingZeros().toPlainString() : cell.toString().trim();
        if (NON_NEGATIVE_INT.matcher(s).matches()) {
            return Long.parseLong(s);
        }
        issues.add(new EmployeeIssue(Severity.ERROR, excelRow, fullName, field, "Phải là số nguyên ≥ 0"));
        return null;
    }

    private static String readChoice(EmployeeField field, Object cell, int excelRow, String fullName,
                                     List<EmployeeIssue> issues) {
        String key = Fold.fold(cell);
        for (String choice : field.choices()) {
            if (Fold.fold(choice).equals(key)) {
                return choice;
            }
        }
        issues.add(new EmployeeIssue(Severity.ERROR, excelRow, fullName, field,
                "Giá trị không có trong danh sách: " + String.join(", ", field.choices())));
        return null;
    }

    private static int findHeaderRow(SheetGrid grid) {
        String key = Fold.fold(EmployeeField.FULL_NAME.header());
        for (int r = 0; r < Math.min(HEADER_SCAN_ROWS, grid.rowCount()); r++) {
            for (int c = 0; c < grid.columnCount(); c++) {
                if (key.equals(Fold.fold(grid.get(r, c)))) {
                    return r;
                }
            }
        }
        return -1;
    }

    private static Map<EmployeeField, Integer> mapColumns(SheetGrid grid, int headerRow, List<EmployeeIssue> issues) {
        Map<EmployeeField, Integer> columns = new EnumMap<>(EmployeeField.class);
        for (int c = 0; c < grid.columnCount(); c++) {
            Object cell = grid.get(headerRow, c);
            if (isBlank(cell)) {
                continue;
            }
            String key = Fold.fold(cell);
            EmployeeField match = null;
            for (EmployeeField f : EmployeeField.values()) {
                if (Fold.fold(f.header()).equals(key)) {
                    match = f;
                    break;
                }
            }
            if (match == null) {
                issues.add(EmployeeIssue.fileWarning(
                        "Cột \"" + cell.toString().trim() + "\" không có trong mẫu, bỏ qua."));
            } else {
                columns.putIfAbsent(match, c);
            }
        }
        return columns;
    }

    private static boolean isBlankRow(SheetGrid grid, int r) {
        for (int c = 0; c < grid.columnCount(); c++) {
            if (!isBlank(grid.get(r, c))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlank(Object v) {
        return v == null || (v instanceof String s && s.isBlank());
    }

    private static boolean hasError(List<EmployeeIssue> issues) {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    /** Chữ của ô: cắt hai đầu, số đổi thành chữ không có phần thập phân thừa; rỗng là null. */
    private static String plainText(Object v) {
        if (v == null) {
            return null;
        }
        String s = v instanceof BigDecimal n ? n.stripTrailingZeros().toPlainString() : v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}

package vn.doit.hr.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc sheet "Bảng lương" theo mẫu DOIT.
 *
 * <p>Cách nhận diện:
 * <ul>
 *   <li>Dòng tiêu đề: dòng đầu tiên (trong 30 dòng đầu) có ô "Mã nhân viên". Dòng ngay dưới là tiêu đề phụ.</li>
 *   <li>Dòng dữ liệu: các dòng sau tiêu đề có ô "Họ và tên" là chữ (bỏ qua dòng đơn vị "VND" và dòng đánh số cột).</li>
 *   <li>Dừng khi gặp dòng "GRAND TOTAL" / "Tổng cộng".</li>
 *   <li>Kỳ lương: chuỗi "Tháng MM/YYYY" phía trên dòng tiêu đề.</li>
 * </ul>
 * Giá trị đọc là <b>giá trị đã tính sẵn</b> của công thức (cái kế toán nhìn thấy trên Excel).
 */
public class PayrollSheetParser {

    private static final int HEADER_SCAN_ROWS = 30;
    private static final Pattern PERIOD = Pattern.compile("(?i)tháng\\s*(\\d{1,2})\\s*/\\s*(\\d{4})");

    public ParsedPayroll parse(SheetGrid grid) {
        List<Issue> issues = new ArrayList<>();

        int headerRow = findHeaderRow(grid);
        if (headerRow < 0) {
            issues.add(Issue.fileError("Không tìm thấy dòng tiêu đề có cột \"Mã nhân viên\". Sai mẫu file?"));
            return new ParsedPayroll(Optional.empty(), Map.of(), List.of(), issues);
        }

        Optional<YearMonth> period = findPeriod(grid, headerRow);
        int codeCol = findColumn(grid, headerRow, "Mã nhân viên");
        int nameCol = findColumn(grid, headerRow, "Họ và tên");
        int positionCol = findColumn(grid, headerRow, "Vị trí công việc");
        int contractDateCol = findColumn(grid, headerRow, "Ngày bắt đầu ký HĐLĐ");
        if (nameCol < 0) {
            issues.add(Issue.fileError("Không tìm thấy cột \"Họ và tên\"."));
            return new ParsedPayroll(period, Map.of(), List.of(), issues);
        }

        Map<PayrollField, Integer> columns = mapColumns(grid, headerRow, issues);
        if (issues.stream().anyMatch(i -> i.severity() == Issue.Severity.ERROR)) {
            return new ParsedPayroll(period, columns, List.of(), issues);
        }

        List<PayrollLine> lines = new ArrayList<>();
        for (int r = headerRow + 2; r < grid.rowCount(); r++) {
            if (isTotalRow(grid, r)) {
                break;
            }
            Object name = grid.get(r, nameCol);
            if (!(name instanceof String s) || s.isBlank()) {
                continue;
            }
            // Ô tiêu đề "Họ và tên" gộp dọc xuống dòng đơn vị (E6:E8) → không phải dòng nhân viên
            if (Text.normalize(s).equals(Text.normalize("Họ và tên"))) {
                continue;
            }
            int excelRow = r + 1;
            String code = asText(grid, r, codeCol);
            Map<PayrollField, BigDecimal> amounts = new EnumMap<>(PayrollField.class);
            for (Map.Entry<PayrollField, Integer> e : columns.entrySet()) {
                Object v = grid.get(r, e.getValue());
                if (v instanceof BigDecimal n) {
                    amounts.put(e.getKey(), n);
                } else if (v instanceof SheetGrid.CellError err) {
                    issues.add(new Issue(Issue.Severity.ERROR, excelRow, code, e.getKey(),
                            "Ô bị lỗi công thức " + err.code()));
                } else if (!Text.isBlank(v)) {
                    issues.add(new Issue(Issue.Severity.ERROR, excelRow, code, e.getKey(),
                            "Không phải số: \"" + v + "\""));
                }
            }
            LocalDate contractDate = contractDateCol >= 0 && grid.get(r, contractDateCol) instanceof LocalDate d ? d : null;
            lines.add(new PayrollLine(excelRow, code, asText(grid, r, positionCol), s.trim(), contractDate, amounts));
        }
        if (lines.isEmpty()) {
            issues.add(Issue.fileError("Không có dòng nhân viên nào."));
        }
        return new ParsedPayroll(period, columns, lines, issues);
    }

    private static int findHeaderRow(SheetGrid grid) {
        String key = Text.normalize("Mã nhân viên");
        for (int r = 0; r < Math.min(HEADER_SCAN_ROWS, grid.rowCount()); r++) {
            for (int c = 0; c < grid.columnCount(); c++) {
                if (key.equals(Text.normalize(grid.get(r, c)))) {
                    return r;
                }
            }
        }
        return -1;
    }

    private static Optional<YearMonth> findPeriod(SheetGrid grid, int headerRow) {
        for (int r = 0; r < headerRow; r++) {
            for (int c = 0; c < grid.columnCount(); c++) {
                if (grid.get(r, c) instanceof String s) {
                    Matcher m = PERIOD.matcher(s);
                    if (m.find()) {
                        return Optional.of(YearMonth.of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static int findColumn(SheetGrid grid, int headerRow, String header) {
        String key = Text.normalize(header);
        for (int c = 0; c < grid.columnCount(); c++) {
            if (key.equals(Text.normalize(grid.get(headerRow, c)))) {
                return c;
            }
        }
        return -1;
    }

    private static Map<PayrollField, Integer> mapColumns(SheetGrid grid, int headerRow, List<Issue> issues) {
        Map<PayrollField, Integer> columns = new EnumMap<>(PayrollField.class);
        for (PayrollField f : PayrollField.values()) {
            List<Integer> found = new ArrayList<>();
            for (int c = 0; c < grid.columnCount(); c++) {
                String h = Text.normalize(grid.get(headerRow, c));
                String sub = headerRow + 1 < grid.rowCount() ? Text.normalize(grid.get(headerRow + 1, c)) : "";
                if (f.matches(h, sub)) {
                    found.add(c);
                }
            }
            if (found.size() == 1) {
                columns.put(f, found.get(0));
            } else if (found.isEmpty()) {
                String msg = "Không tìm thấy cột \"" + f.label() + "\"";
                issues.add(f.required() ? Issue.fileError(msg) : Issue.fileWarning(msg + " – coi như bằng 0."));
            } else {
                issues.add(Issue.fileError("Có " + found.size() + " cột cùng tên \"" + f.label() + "\"."));
            }
        }
        return columns;
    }

    private static boolean isTotalRow(SheetGrid grid, int r) {
        for (int c = 0; c < grid.columnCount(); c++) {
            String s = Text.normalize(grid.get(r, c));
            if (s.equals("grand total") || s.equals("tổng cộng")) {
                return true;
            }
        }
        return false;
    }

    private static String asText(SheetGrid grid, int r, int c) {
        if (c < 0) {
            return null;
        }
        Object v = grid.get(r, c);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal n) {
            return n.stripTrailingZeros().toPlainString();
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}

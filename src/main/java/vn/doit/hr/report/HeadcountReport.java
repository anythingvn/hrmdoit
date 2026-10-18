package vn.doit.hr.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Báo cáo biến động nhân sự theo tháng.
 *
 * <p>Quy ước (giữ đẳng thức Cuối kỳ = Đầu kỳ + Tuyển mới − Nghỉ việc):
 * <ul>
 *   <li>Đầu kỳ: số người còn làm việc sau ngày cuối tháng trước.</li>
 *   <li>Tuyển mới: ngày vào làm nằm trong tháng.</li>
 *   <li>Nghỉ việc: ngày làm việc cuối cùng nằm trong tháng.</li>
 *   <li>Cuối kỳ: số người còn làm việc sau ngày cuối tháng (nghỉ đúng ngày cuối tháng thì không tính).</li>
 *   <li>Tỷ lệ nghỉ việc tháng = Nghỉ việc ÷ bình quân (Đầu kỳ + Cuối kỳ) / 2.</li>
 * </ul>
 */
public final class HeadcountReport {

    public record MonthRow(YearMonth month, int opening, int hires, int leavers, int voluntaryLeavers, int closing) {

        public BigDecimal averageHeadcount() {
            return BigDecimal.valueOf(opening + closing).divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
        }

        /** Tỷ lệ nghỉ việc (0.05 = 5%). */
        public BigDecimal turnoverRate() {
            return rate(leavers, averageHeadcount());
        }
    }

    /** Tổng hợp cả kỳ. */
    public record Summary(YearMonth from, YearMonth to, int opening, int hires, int leavers, int voluntaryLeavers,
                          int closing, BigDecimal averageHeadcount) {

        public BigDecimal turnoverRate() {
            return rate(leavers, averageHeadcount);
        }

        public BigDecimal voluntaryTurnoverRate() {
            return rate(voluntaryLeavers, averageHeadcount);
        }
    }

    private HeadcountReport() {
    }

    public static List<MonthRow> monthly(List<EmploymentRecord> records, YearMonth from, YearMonth to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Kỳ báo cáo không hợp lệ");
        }
        List<MonthRow> rows = new ArrayList<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            LocalDate start = m.atDay(1);
            LocalDate end = m.atEndOfMonth();
            int opening = 0, hires = 0, leavers = 0, voluntary = 0, closing = 0;
            for (EmploymentRecord r : records) {
                if (r.onPayrollAfter(start.minusDays(1))) {
                    opening++;
                }
                if (r.hiredBetween(start, end)) {
                    hires++;
                }
                if (r.leftBetween(start, end)) {
                    leavers++;
                    if (r.exitType() != null && r.exitType().voluntary()) {
                        voluntary++;
                    }
                }
                if (r.onPayrollAfter(end)) {
                    closing++;
                }
            }
            rows.add(new MonthRow(m, opening, hires, leavers, voluntary, closing));
        }
        return rows;
    }

    /** Tổng hợp kỳ; bình quân nhân sự = trung bình các bình quân tháng. */
    public static Summary summarize(List<MonthRow> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không có dữ liệu");
        }
        BigDecimal avg = rows.stream().map(MonthRow::averageHeadcount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rows.size()), 1, RoundingMode.HALF_UP);
        return new Summary(rows.get(0).month(), rows.get(rows.size() - 1).month(),
                rows.get(0).opening(),
                rows.stream().mapToInt(MonthRow::hires).sum(),
                rows.stream().mapToInt(MonthRow::leavers).sum(),
                rows.stream().mapToInt(MonthRow::voluntaryLeavers).sum(),
                rows.get(rows.size() - 1).closing(),
                avg);
    }

    /** Số nhân sự cuối tháng theo phòng ban. */
    public static Map<String, Integer> closingByDepartment(List<EmploymentRecord> records, YearMonth month) {
        Map<String, Integer> out = new TreeMap<>();
        for (EmploymentRecord r : records) {
            if (r.onPayrollAfter(month.atEndOfMonth())) {
                out.merge(r.department() == null ? "(chưa gán phòng ban)" : r.department(), 1, Integer::sum);
            }
        }
        return out;
    }

    static BigDecimal rate(long count, BigDecimal base) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(count).divide(base, 4, RoundingMode.HALF_UP);
    }
}

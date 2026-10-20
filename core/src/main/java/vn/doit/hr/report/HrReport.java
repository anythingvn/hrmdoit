package vn.doit.hr.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import vn.doit.hr.payroll.PayrollLine;

/** Gói toàn bộ số liệu báo cáo nhân sự của một kỳ – đầu vào cho {@link ReportExcelWriter}. */
public record HrReport(
        String companyName,
        YearMonth from,
        YearMonth to,
        List<HeadcountReport.MonthRow> headcount,
        HeadcountReport.Summary summary,
        Map<String, Integer> closingByDepartment,
        TurnoverReport.Result turnover,
        List<PayrollCostReport.MonthCost> costs,
        String costGroupLabel) {

    /**
     * @param payrolls bảng lương đã import theo kỳ (chỉ lấy các kỳ nằm trong [from, to])
     * @param costGroupLabel tên cách gom nhóm chi phí, vd "Vị trí" hoặc "Phòng ban"
     */
    public static HrReport build(String companyName, YearMonth from, YearMonth to, List<EmploymentRecord> employees,
                                 SortedMap<YearMonth, List<PayrollLine>> payrolls,
                                 Function<PayrollLine, String> costGroupBy, String costGroupLabel) {
        List<HeadcountReport.MonthRow> rows = HeadcountReport.monthly(employees, from, to);
        SortedMap<YearMonth, List<PayrollLine>> inRange = new TreeMap<>(payrolls.subMap(from, to.plusMonths(1)));
        return new HrReport(companyName, from, to, rows, HeadcountReport.summarize(rows),
                HeadcountReport.closingByDepartment(employees, to),
                TurnoverReport.analyze(employees, from, to),
                PayrollCostReport.monthly(inRange, costGroupBy),
                costGroupLabel);
    }

    public BigDecimal totalPayrollCost() {
        return costs.stream().map(c -> c.total().totalCost()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Chi phí bình quân một người một tháng trên các tháng có bảng lương. */
    public BigDecimal averageCostPerHeadPerMonth() {
        int headMonths = costs.stream().mapToInt(c -> c.total().headcount()).sum();
        return headMonths == 0 ? BigDecimal.ZERO
                : totalPayrollCost().divide(BigDecimal.valueOf(headMonths), 0, RoundingMode.HALF_UP);
    }

    public String periodLabel() {
        String f = String.format("%02d/%d", from.getMonthValue(), from.getYear());
        String t = String.format("%02d/%d", to.getMonthValue(), to.getYear());
        return from.equals(to) ? "Tháng " + f : "Từ " + f + " đến " + t;
    }
}

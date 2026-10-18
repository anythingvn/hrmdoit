package vn.doit.hr.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.TsvSheetGrid;
import vn.doit.hr.report.EmploymentRecord.ExitType;
import vn.doit.hr.report.PayrollCostReport.Cost;
import vn.doit.hr.report.TurnoverReport.TenureBand;

class HrReportTest {

    private static EmploymentRecord emp(String code, String dept, String hire, String term, ExitType type, String reason) {
        return new EmploymentRecord(code, "NV " + code, dept, "Lập trình viên", LocalDate.parse(hire),
                term == null ? null : LocalDate.parse(term), type, reason);
    }

    /** 10 người đầu năm; Q1/2026: 3 người vào, 3 người nghỉ. */
    static List<EmploymentRecord> sample() {
        return List.of(
                emp("A01", "Phát triển phần mềm", "2023-01-01", null, null, null),
                emp("A02", "Phát triển phần mềm", "2023-05-01", null, null, null),
                emp("A03", "Phát triển phần mềm", "2024-03-01", "2026-01-31", ExitType.TU_NGHI, "Lương thấp hơn thị trường"),
                emp("A04", "Phát triển phần mềm", "2024-09-01", null, null, null),
                emp("A05", "Hành chính – Nhân sự", "2022-01-01", null, null, null),
                emp("A06", "Hành chính – Nhân sự", "2025-08-01", "2026-02-15", ExitType.TU_NGHI, "Chuyển nơi ở"),
                emp("A07", "Kiểm thử", "2025-06-01", null, null, null),
                emp("A08", "Kiểm thử", "2025-12-01", "2025-12-31", ExitType.KHONG_DAT_THU_VIEC, null),  // nghỉ trước kỳ
                emp("A09", "Kiểm thử", "2021-04-01", null, null, null),
                emp("A10", "Phát triển phần mềm", "2025-10-01", null, null, null),
                emp("A11", "Phát triển phần mềm", "2025-11-15", null, null, null),
                emp("B01", "Phát triển phần mềm", "2026-01-05", null, null, null),
                emp("B02", "Kiểm thử", "2026-02-01", "2026-03-20", ExitType.KHONG_DAT_THU_VIEC, "Không đạt yêu cầu"),
                emp("B03", "Phát triển phần mềm", "2026-03-31", null, null, null));
    }

    @Test
    void monthlyMovementKeepsIdentityAndCountsLastDayLeaversOut() {
        List<HeadcountReport.MonthRow> rows = HeadcountReport.monthly(sample(), YearMonth.of(2026, 1), YearMonth.of(2026, 3));
        // Tháng 1: đầu 10 (A08 đã nghỉ 31/12), vào B01, A03 nghỉ 31/01 → cuối 10
        assertEquals(new HeadcountReport.MonthRow(YearMonth.of(2026, 1), 10, 1, 1, 1, 10), rows.get(0));
        assertEquals(new HeadcountReport.MonthRow(YearMonth.of(2026, 2), 10, 1, 1, 1, 10), rows.get(1));
        assertEquals(new HeadcountReport.MonthRow(YearMonth.of(2026, 3), 10, 1, 1, 0, 10), rows.get(2));
        for (HeadcountReport.MonthRow r : rows) {
            assertEquals(r.closing(), r.opening() + r.hires() - r.leavers(), "Cuối = Đầu + Vào − Nghỉ");
        }
        assertEquals(0, new BigDecimal("0.1000").compareTo(rows.get(0).turnoverRate()));   // 1 / 10

        HeadcountReport.Summary s = HeadcountReport.summarize(rows);
        assertEquals(3, s.hires());
        assertEquals(3, s.leavers());
        assertEquals(2, s.voluntaryLeavers());
        assertEquals(0, new BigDecimal("0.3000").compareTo(s.turnoverRate()));              // 3 / 10
        assertEquals(0, new BigDecimal("0.2000").compareTo(s.voluntaryTurnoverRate()));

        Map<String, Integer> dept = HeadcountReport.closingByDepartment(sample(), YearMonth.of(2026, 3));
        assertEquals(Map.of("Phát triển phần mềm", 7, "Hành chính – Nhân sự", 1, "Kiểm thử", 2), dept);
    }

    @Test
    void turnoverBreakdowns() {
        TurnoverReport.Result t = TurnoverReport.analyze(sample(), YearMonth.of(2026, 1), YearMonth.of(2026, 3));
        assertEquals(List.of("A03", "A06", "B02"), t.leavers().stream().map(EmploymentRecord::code).toList());
        assertEquals(2, t.byExitType().get(ExitType.TU_NGHI));
        assertEquals(1, t.byExitType().get(ExitType.KHONG_DAT_THU_VIEC));
        assertEquals(1, t.byTenure().get(TenureBand.DUOI_2_THANG));        // B02: 01/02 → 20/03
        assertEquals(1, t.byTenure().get(TenureBand.TU_6_DEN_12_THANG));   // A06: 01/08/25 → 15/02/26 = 6 tháng
        assertEquals(1, t.byTenure().get(TenureBand.TU_1_DEN_3_NAM));      // A03: 23 tháng
        assertEquals(1, t.earlyLeavers());
        assertEquals(1, t.byReason().get("Lương thấp hơn thị trường"));
    }

    @Test
    void tenureMonthsCountsFullMonths() {
        assertEquals(3, emp("X", "D", "2026-01-01", "2026-03-31", ExitType.TU_NGHI, null).tenureMonthsAtExit());
        assertEquals(2, emp("X", "D", "2026-01-01", "2026-03-30", ExitType.TU_NGHI, null).tenureMonthsAtExit());
        assertEquals(TenureBand.TU_3_NAM, TenureBand.of(36));
    }

    @Test
    void payrollCostMatchesGrandTotalOfRealFile() throws Exception {
        List<PayrollLine> june = new PayrollSheetParser().parse(TsvSheetGrid.load("/bang-luong-06-2026.tsv")).lines();
        TreeMap<YearMonth, List<PayrollLine>> payrolls = new TreeMap<>();
        payrolls.put(YearMonth.of(2026, 5), june.subList(0, 10));      // giả lập tháng trước có ít người hơn
        payrolls.put(YearMonth.of(2026, 6), june);
        List<PayrollCostReport.MonthCost> costs = PayrollCostReport.monthly(payrolls, PayrollLine::position);

        Cost t = costs.get(1).total();
        // Đối chiếu dòng GRAND TOTAL của file: M=155.100.000; N+R=61.000.000; O+P+Q=15.500.000; S+T=2.800.000;
        // W=234.400.000; AN=11.309.000; AP=245.709.000
        assertEquals(14, t.headcount());
        assertEquals(0, new BigDecimal("155100000").compareTo(t.baseSalary()));
        assertEquals(0, new BigDecimal("61000000").compareTo(t.bonus()));
        assertEquals(0, new BigDecimal("15500000").compareTo(t.allowances()));
        assertEquals(0, new BigDecimal("2800000").compareTo(t.overtime()));
        assertEquals(0, new BigDecimal("234400000").compareTo(t.grossPay()));
        assertEquals(0, new BigDecimal("11309000").compareTo(t.employerInsurance()));
        assertEquals(0, new BigDecimal("245709000").compareTo(t.totalCost()));
        assertTrue(t.consistentWithFile());
        assertEquals(0, new BigDecimal("219821925").compareTo(t.netPay()));

        Cost dev = costs.get(1).byGroup().get("Lập trình viên");
        assertEquals(8, dev.headcount());
        assertTrue(costs.get(1).changeVsPreviousMonth().signum() > 0);
        assertEquals(null, costs.get(0).changeVsPreviousMonth());

        HrReport r = HrReport.build("CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT", YearMonth.of(2026, 6), YearMonth.of(2026, 6),
                sample(), payrolls, PayrollLine::position, "Vị trí");
        assertEquals(1, r.costs().size(), "Chỉ lấy bảng lương trong kỳ");
        assertEquals(0, new BigDecimal("17550643").compareTo(r.averageCostPerHeadPerMonth()));   // 245.709.000 / 14
        assertEquals("Tháng 06/2026", r.periodLabel());
    }
}

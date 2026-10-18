package vn.doit.hr.report;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import vn.doit.hr.report.EmploymentRecord.ExitType;

/** Phân tích những người nghỉ việc trong kỳ: theo loại nghỉ, phòng ban, thâm niên, lý do. */
public final class TurnoverReport {

    public enum TenureBand {
        DUOI_2_THANG("Dưới 2 tháng", 0, 2),
        TU_2_DEN_6_THANG("2 – dưới 6 tháng", 2, 6),
        TU_6_DEN_12_THANG("6 – dưới 12 tháng", 6, 12),
        TU_1_DEN_3_NAM("1 – dưới 3 năm", 12, 36),
        TU_3_NAM("Từ 3 năm", 36, Integer.MAX_VALUE);

        private final String label;
        private final int fromMonths;
        private final int toMonths;

        TenureBand(String label, int fromMonths, int toMonths) {
            this.label = label;
            this.fromMonths = fromMonths;
            this.toMonths = toMonths;
        }

        public String label() {
            return label;
        }

        public static TenureBand of(long months) {
            for (TenureBand b : values()) {
                if (months >= b.fromMonths && months < b.toMonths) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Thâm niên âm: " + months);
        }
    }

    public record Result(
            List<EmploymentRecord> leavers,
            Map<ExitType, Integer> byExitType,
            Map<String, Integer> byDepartment,
            Map<TenureBand, Integer> byTenure,
            Map<String, Integer> byReason) {

        /** Người nghỉ trong vòng 6 tháng đầu – chỉ báo về tuyển dụng / hội nhập. */
        public int earlyLeavers() {
            return byTenure.getOrDefault(TenureBand.DUOI_2_THANG, 0) + byTenure.getOrDefault(TenureBand.TU_2_DEN_6_THANG, 0);
        }
    }

    private TurnoverReport() {
    }

    public static Result analyze(List<EmploymentRecord> records, YearMonth from, YearMonth to) {
        List<EmploymentRecord> leavers = records.stream()
                .filter(r -> r.leftBetween(from.atDay(1), to.atEndOfMonth()))
                .sorted(Comparator.comparing(EmploymentRecord::terminationDate).thenComparing(EmploymentRecord::code))
                .toList();
        Map<ExitType, Integer> byType = new EnumMap<>(ExitType.class);
        Map<String, Integer> byDept = new TreeMap<>();
        Map<TenureBand, Integer> byTenure = new EnumMap<>(TenureBand.class);
        Map<String, Integer> byReason = new TreeMap<>();
        for (TenureBand b : TenureBand.values()) {
            byTenure.put(b, 0);
        }
        for (EmploymentRecord r : leavers) {
            byType.merge(r.exitType() == null ? ExitType.KHAC : r.exitType(), 1, Integer::sum);
            byDept.merge(r.department() == null ? "(chưa gán phòng ban)" : r.department(), 1, Integer::sum);
            byTenure.merge(TenureBand.of(r.tenureMonthsAtExit()), 1, Integer::sum);
            String reason = r.exitReason() == null || r.exitReason().isBlank() ? "(không ghi lý do)" : r.exitReason().trim();
            byReason.merge(reason, 1, Integer::sum);
        }
        return new Result(leavers, byType, byDept, byTenure, byReason);
    }
}

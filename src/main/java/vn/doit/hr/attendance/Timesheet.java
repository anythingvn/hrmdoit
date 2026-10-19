package vn.doit.hr.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import vn.doit.hr.attendance.PunchRequest.Type;
import vn.doit.hr.attendance.WorkSchedule.Shift;

/**
 * Tính công từ các lần chấm công đã được chấp nhận (ACCEPTED, hoặc NEEDS_REVIEW đã được duyệt).
 *
 * <p>Quy ước:
 * <ul>
 *   <li>Giờ vào = lần chấm VÀO sớm nhất trong ngày; giờ ra = lần chấm RA muộn nhất.</li>
 *   <li>Giờ làm chỉ tính phần nằm trong ca, trừ nghỉ trưa. Làm thêm ngoài ca không tự tính – đi theo quy trình đăng ký OT.</li>
 *   <li>Đi muộn: vào sau (giờ bắt đầu + số phút cho phép) thì tính số phút muộn kể từ giờ bắt đầu.</li>
 *   <li>Công ngày = trọng số ngày × giờ làm ÷ giờ chuẩn, làm tròn xuống 0,01.</li>
 *   <li>Thiếu giờ ra hoặc giờ vào: công = 0 và đánh dấu để nhân viên giải trình.</li>
 * </ul>
 */
public final class Timesheet {

    /** Một lần chấm công hợp lệ. */
    public record Punch(Instant time, Type type) {
    }

    public enum DayStatus {
        PRESENT("Đi làm"),
        MISSING_OUT("Thiếu giờ ra"),
        MISSING_IN("Thiếu giờ vào"),
        ABSENT("Vắng"),
        OFF_DAY("Ngày nghỉ"),
        WORKED_ON_OFF_DAY("Làm ngày nghỉ");

        private final String label;

        DayStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Day(LocalDate date, DayStatus status, LocalTime firstIn, LocalTime lastOut,
                      int workedMinutes, int lateMinutes, int earlyLeaveMinutes, BigDecimal credit) {
    }

    public record Month(YearMonth month, List<Day> days, BigDecimal standardCredit, BigDecimal workedCredit,
                        int lateCount, int lateMinutes, int earlyLeaveCount, int absentDays, List<LocalDate> missingPunchDays) {
    }

    private Timesheet() {
    }

    public static Day day(LocalDate date, List<Punch> punches, WorkSchedule schedule, ZoneId zone) {
        Optional<LocalTime> in = punches.stream().filter(p -> p.type() == Type.IN)
                .map(p -> p.time().atZone(zone).toLocalTime()).min(Comparator.naturalOrder());
        Optional<LocalTime> out = punches.stream().filter(p -> p.type() == Type.OUT)
                .map(p -> p.time().atZone(zone).toLocalTime()).max(Comparator.naturalOrder());
        Optional<Shift> shift = schedule.shiftOn(date);

        if (shift.isEmpty()) {
            DayStatus st = punches.isEmpty() ? DayStatus.OFF_DAY : DayStatus.WORKED_ON_OFF_DAY;
            return new Day(date, st, in.orElse(null), out.orElse(null), 0, 0, 0, BigDecimal.ZERO);
        }
        Shift s = shift.get();
        if (in.isEmpty() && out.isEmpty()) {
            return new Day(date, DayStatus.ABSENT, null, null, 0, 0, 0, BigDecimal.ZERO);
        }
        if (in.isEmpty() || out.isEmpty() || !out.get().isAfter(in.get())) {
            DayStatus st = in.isEmpty() ? DayStatus.MISSING_IN : DayStatus.MISSING_OUT;
            return new Day(date, st, in.orElse(null), out.filter(o -> in.isEmpty() || o.isAfter(in.get())).orElse(null),
                    0, 0, 0, BigDecimal.ZERO);
        }
        int worked = s.workedMinutes(in.get(), out.get());
        int late = in.get().isAfter(s.start().plusMinutes(schedule.graceMinutes()))
                ? (in.get().toSecondOfDay() - s.start().toSecondOfDay()) / 60 : 0;
        int early = out.get().isBefore(s.end()) ? (s.end().toSecondOfDay() - out.get().toSecondOfDay()) / 60 : 0;
        BigDecimal credit = s.dayWeight().multiply(BigDecimal.valueOf(Math.min(worked, s.scheduledMinutes())))
                .divide(BigDecimal.valueOf(s.scheduledMinutes()), 2, RoundingMode.DOWN);
        return new Day(date, DayStatus.PRESENT, in.get(), out.get(), worked, late, early, credit);
    }

    public static Month month(YearMonth month, List<Punch> punches, WorkSchedule schedule, ZoneId zone) {
        Map<LocalDate, List<Punch>> byDay = new TreeMap<>();
        for (Punch p : punches) {
            LocalDate d = p.time().atZone(zone).toLocalDate();
            if (YearMonth.from(d).equals(month)) {
                byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(p);
            }
        }
        List<Day> days = new ArrayList<>();
        BigDecimal standard = BigDecimal.ZERO;
        BigDecimal worked = BigDecimal.ZERO;
        int lateCount = 0, lateMin = 0, earlyCount = 0, absent = 0;
        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            Day day = day(d, byDay.getOrDefault(d, List.of()), schedule, zone);
            days.add(day);
            Optional<Shift> s = schedule.shiftOn(d);
            if (s.isPresent()) {
                standard = standard.add(s.get().dayWeight());
            }
            worked = worked.add(day.credit());
            if (day.lateMinutes() > 0) {
                lateCount++;
                lateMin += day.lateMinutes();
            }
            if (day.earlyLeaveMinutes() > 0) {
                earlyCount++;
            }
            if (day.status() == DayStatus.ABSENT) {
                absent++;
            }
            if (day.status() == DayStatus.MISSING_IN || day.status() == DayStatus.MISSING_OUT) {
                missing.add(d);
            }
        }
        return new Month(month, days, standard, worked, lateCount, lateMin, earlyCount, absent, missing);
    }
}

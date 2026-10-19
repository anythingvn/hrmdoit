package vn.doit.hr.attendance;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lịch làm việc chuẩn. Mặc định theo HĐLĐ của DOIT: thứ Hai–thứ Sáu 08:00–17:00 (nghỉ trưa 12:00–13:00),
 * thứ Bảy 08:00–12:00 – tổng 44 giờ/tuần.
 *
 * @param graceMinutes số phút được phép đến muộn mà không tính là đi muộn
 */
public record WorkSchedule(Map<DayOfWeek, Shift> shifts, Set<LocalDate> holidays, int graceMinutes) {

    /**
     * Ca làm trong ngày.
     *
     * @param dayWeight ngày đó tính bao nhiêu công nếu làm đủ (thứ Bảy làm nửa buổi = 0,5 công)
     */
    public record Shift(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd, BigDecimal dayWeight) {

        public int scheduledMinutes() {
            return minutesBetween(start, end) - (breakStart == null ? 0 : minutesBetween(breakStart, breakEnd));
        }

        /** Số phút làm việc thực trong khoảng [from, to], chỉ tính phần nằm trong ca và ngoài giờ nghỉ trưa. */
        public int workedMinutes(LocalTime from, LocalTime to) {
            int total = overlap(from, to, start, end);
            if (breakStart != null) {
                total -= overlap(from, to, breakStart, breakEnd);
            }
            return Math.max(0, total);
        }

        private static int overlap(LocalTime a1, LocalTime a2, LocalTime b1, LocalTime b2) {
            LocalTime s = a1.isAfter(b1) ? a1 : b1;
            LocalTime e = a2.isBefore(b2) ? a2 : b2;
            return e.isAfter(s) ? minutesBetween(s, e) : 0;
        }

        private static int minutesBetween(LocalTime a, LocalTime b) {
            return (b.toSecondOfDay() - a.toSecondOfDay()) / 60;
        }
    }

    public WorkSchedule {
        shifts = Map.copyOf(shifts);
        holidays = Set.copyOf(holidays);
    }

    public static WorkSchedule doitDefault(Set<LocalDate> holidays) {
        Map<DayOfWeek, Shift> m = new EnumMap<>(DayOfWeek.class);
        Shift weekday = new Shift(LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), BigDecimal.ONE);
        for (DayOfWeek d : new DayOfWeek[] {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            m.put(d, weekday);
        }
        m.put(DayOfWeek.SATURDAY, new Shift(LocalTime.of(8, 0), LocalTime.of(12, 0), null, null, new BigDecimal("0.5")));
        return new WorkSchedule(m, holidays, 5);
    }

    /** Ca làm của ngày; rỗng nếu là ngày nghỉ (Chủ nhật, ngày lễ). */
    public Optional<Shift> shiftOn(LocalDate date) {
        if (holidays.contains(date)) {
            return Optional.empty();
        }
        return Optional.ofNullable(shifts.get(date.getDayOfWeek()));
    }
}

package vn.doit.hr.reminder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Lịch làm việc: nhắc rơi vào ngày nghỉ sẽ được dời lên ngày làm việc liền trước. */
@FunctionalInterface
public interface WorkCalendar {

    boolean isWorkingDay(LocalDate date);

    /** Thứ Hai–Thứ Bảy (theo HĐLĐ của DOIT: thứ Bảy làm buổi sáng), trừ các ngày lễ truyền vào. */
    static WorkCalendar mondayToSaturday(Set<LocalDate> holidays) {
        return d -> d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d);
    }

    default LocalDate onOrBefore(LocalDate date) {
        LocalDate d = date;
        for (int i = 0; i < 30 && !isWorkingDay(d); i++) {
            d = d.minusDays(1);
        }
        return d;
    }
}

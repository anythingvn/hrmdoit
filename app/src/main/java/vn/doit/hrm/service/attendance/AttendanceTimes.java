package vn.doit.hrm.service.attendance;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Định dạng giờ chấm công cho email và danh sách chờ duyệt. */
final class AttendanceTimes {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private AttendanceTimes() {}

    /** "08:00". */
    static String hm(LocalTime t) {
        return t.format(HM);
    }

    /** "Vào 08:00 · Ra 17:12" (giờ bỏ trống không ghi). */
    static String inOut(LocalTime in, LocalTime out) {
        List<String> parts = new ArrayList<>();
        if (in != null) parts.add("Vào " + hm(in));
        if (out != null) parts.add("Ra " + hm(out));
        return String.join(" · ", parts);
    }
}

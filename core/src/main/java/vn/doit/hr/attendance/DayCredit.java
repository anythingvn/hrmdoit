package vn.doit.hr.attendance;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import vn.doit.hr.leave.DayPart;

/** Tính công một ngày làm việc từ giờ vào/ra gõ tay theo ngưỡng muộn/về sớm. */
public final class DayCredit {

    public record Rules(int graceMinutes, int halfDayThresholdMinutes) {}

    public record Hours(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd) {}

    public enum Status { DI_LAM, THIEU_GIO, VANG, CHUA_NHAP }

    public record Result(Status status, LocalTime firstIn, LocalTime lastOut,
                         int lateMinutes, int earlyMinutes, BigDecimal workCredit) {}

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private DayCredit() {}

    /**
     * @param leavePart null nghĩa là không có đơn nghỉ; CA_NGAY không được gọi vào đây.
     * @return workCredit chỉ là phần đi làm (0, 0.5 hoặc 1), null khi chưa đủ dữ liệu.
     */
    public static Result compute(List<LocalTime> ins, List<LocalTime> outs, Hours hours, Rules rules,
                                 DayPart leavePart, boolean markedAbsent) {
        LocalTime firstIn = ins == null || ins.isEmpty() ? null : Collections.min(ins);
        LocalTime lastOut = outs == null || outs.isEmpty() ? null : Collections.max(outs);
        if (markedAbsent) {
            return new Result(Status.VANG, firstIn, lastOut, 0, 0, BigDecimal.ZERO);
        }
        if (firstIn == null && lastOut == null) {
            return new Result(Status.CHUA_NHAP, null, null, 0, 0, null);
        }
        if (firstIn == null || lastOut == null) {
            return new Result(Status.THIEU_GIO, firstIn, lastOut, 0, 0, null);
        }
        LocalTime segStart = leavePart == DayPart.SANG ? hours.breakEnd() : hours.start();
        LocalTime segEnd = leavePart == DayPart.CHIEU ? hours.breakStart() : hours.end();
        int late = minutes(segStart, firstIn);
        int early = minutes(lastOut, segEnd);
        if (late <= rules.graceMinutes()) late = 0;
        if (early <= rules.graceMinutes()) early = 0;

        if (leavePart != null) {
            return new Result(Status.DI_LAM, firstIn, lastOut, late, early, HALF);
        }
        int standard = minutes(hours.start(), hours.end()) - minutes(hours.breakStart(), hours.breakEnd());
        int worked = overlap(firstIn, lastOut, hours.start(), hours.end())
                - overlap(firstIn, lastOut, hours.breakStart(), hours.breakEnd());
        boolean lateOver = late > rules.halfDayThresholdMinutes();
        boolean earlyOver = early > rules.halfDayThresholdMinutes();
        if ((lateOver && earlyOver) || worked * 2 < standard) {
            return new Result(Status.VANG, firstIn, lastOut, late, early, BigDecimal.ZERO);
        }
        BigDecimal credit = lateOver || earlyOver ? HALF : BigDecimal.ONE;
        return new Result(Status.DI_LAM, firstIn, lastOut, late, early, credit);
    }

    /** Số phút từ a đến b, âm thì về 0. */
    private static int minutes(LocalTime a, LocalTime b) {
        return (int) Math.max(0, Duration.between(a, b).toMinutes());
    }

    private static int overlap(LocalTime a1, LocalTime a2, LocalTime b1, LocalTime b2) {
        LocalTime s = a1.isAfter(b1) ? a1 : b1;
        LocalTime e = a2.isBefore(b2) ? a2 : b2;
        return minutes(s, e);
    }
}

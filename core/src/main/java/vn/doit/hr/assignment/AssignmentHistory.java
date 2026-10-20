package vn.doit.hr.assignment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import vn.doit.hr.assignment.AssignmentChange.Kind;
import vn.doit.hr.assignment.WorkLocationType.Layer;
import vn.doit.hr.reminder.WorkCalendar;

/**
 * Lịch sử vị trí làm việc của <b>một</b> nhân viên. Bất biến: mỗi thao tác trả về lịch sử mới kèm danh sách
 * thay đổi để lưu DB và audit.
 *
 * <p>Quy tắc:
 * <ol>
 *   <li>Nơi làm việc chính (văn phòng / khách hàng / từ xa) không chồng lấn nhau.</li>
 *   <li>Cử sang nơi chính mới → tự chốt nơi đang làm vào ngày hôm trước.
 *       Nếu đợt cử có ngày kết thúc → tự tạo giai đoạn quay về nơi cũ từ ngày hôm sau.</li>
 *   <li>Đi công tác phải có ngày về, không chồng lấn đợt công tác khác, và đè lên nơi chính trong thời gian đi.</li>
 *   <li>Không được chèn giai đoạn chính bắt đầu trước giai đoạn chính gần nhất (tránh viết lại quá khứ ngầm);
 *       muốn sửa quá khứ thì dùng {@link #changeEnd}.</li>
 * </ol>
 */
public final class AssignmentHistory {

    /** Kết quả một thao tác ghi. */
    public record Result(AssignmentHistory history, List<AssignmentChange> changes) {
    }

    /** Một đoạn liên tục trên dòng thời gian; {@code assignment == null} là khoảng trống chưa có dữ liệu. */
    public record Segment(LocalDate from, LocalDate to, Assignment assignment) {
        public String label() {
            return assignment == null ? "(chưa có dữ liệu)" : assignment.placeLabel();
        }
    }

    private final String employeeCode;
    private final List<Assignment> entries;

    private AssignmentHistory(String employeeCode, List<Assignment> entries) {
        this.employeeCode = employeeCode;
        this.entries = entries.stream().sorted(Comparator.comparing(Assignment::from)).toList();
    }

    public static AssignmentHistory of(String employeeCode, List<Assignment> entries) {
        for (Assignment a : entries) {
            if (!employeeCode.equals(a.employeeCode())) {
                throw new IllegalArgumentException("Bản ghi của nhân viên khác: " + a);
            }
        }
        return new AssignmentHistory(employeeCode, entries);
    }

    public String employeeCode() {
        return employeeCode;
    }

    public List<Assignment> entries() {
        return entries;
    }

    // ------------------------------------------------------------------ ghi

    public Result add(Assignment a) {
        validate(a);
        List<Assignment> next = new ArrayList<>(entries);
        List<AssignmentChange> changes = new ArrayList<>();

        if (a.type().layer() == Layer.TEMPORARY) {
            List<Assignment> clash = sameLayer(Layer.TEMPORARY).stream().filter(t -> t.overlaps(a.from(), a.to())).toList();
            if (!clash.isEmpty()) {
                throw new AssignmentConflictException("Trùng thời gian với đợt công tác: " + join(clash) + ".");
            }
        } else {
            List<Assignment> clash = sameLayer(Layer.PRIMARY).stream().filter(p -> p.overlaps(a.from(), a.to())).toList();
            if (!clash.isEmpty()) {
                Assignment p = clash.get(0);
                boolean canSplit = clash.size() == 1 && p.from().isBefore(a.from());
                if (!canSplit) {
                    throw new AssignmentConflictException("Trùng thời gian với: " + join(clash)
                            + ". Hãy đổi ngày kết thúc giai đoạn đó trước, hoặc chọn ngày bắt đầu khác.");
                }
                Assignment closed = p.withTo(a.from().minusDays(1));
                replace(next, p, closed);
                changes.add(new AssignmentChange(Kind.CLOSED, p, closed));
                if (a.to() != null && (p.to() == null || p.to().isAfter(a.to()))) {
                    Assignment resumed = p.withFrom(a.to().plusDays(1));
                    next.add(resumed);
                    changes.add(new AssignmentChange(Kind.RESUMED, null, resumed));
                }
            }
        }
        next.add(a);
        changes.add(changes.isEmpty() || changes.get(0).kind() != Kind.CLOSED ? 0 : 1,
                new AssignmentChange(Kind.CREATED, null, a));
        return new Result(new AssignmentHistory(employeeCode, next), changes);
    }

    /**
     * Đổi ngày kết thúc (về sớm, gia hạn, hoặc chốt một giai đoạn đang mở).
     * Với nơi làm việc chính: giai đoạn chính liền sau (bắt đầu đúng ngày hôm sau ngày kết thúc cũ) được dời theo.
     */
    public Result changeEnd(Assignment target, LocalDate newEnd) {
        Assignment cur = entries.stream().filter(e -> e.equals(target)).findFirst()
                .orElseThrow(() -> new AssignmentConflictException("Không tìm thấy giai đoạn: " + target));
        if (newEnd != null && newEnd.isBefore(cur.from())) {
            throw new AssignmentConflictException("Ngày kết thúc " + newEnd + " trước ngày bắt đầu " + cur.from() + ".");
        }
        if (newEnd == null && cur.type() == WorkLocationType.CONG_TAC) {
            throw new AssignmentConflictException("Đi công tác phải có ngày về.");
        }
        Assignment updated = cur.withTo(newEnd);
        List<Assignment> next = new ArrayList<>(entries);
        replace(next, cur, updated);
        List<AssignmentChange> changes = new ArrayList<>(List.of(new AssignmentChange(Kind.END_CHANGED, cur, updated)));

        Layer layer = cur.type().layer();
        Optional<Assignment> follower = layer == Layer.PRIMARY && cur.to() != null
                ? sameLayer(layer).stream().filter(e -> e.from().equals(cur.to().plusDays(1))).findFirst()
                : Optional.empty();
        if (follower.isPresent() && newEnd != null) {
            Assignment f = follower.get();
            if (f.to() != null && !f.to().isAfter(newEnd)) {
                throw new AssignmentConflictException("Gia hạn đến " + newEnd + " sẽ đè hết giai đoạn sau: " + f + ".");
            }
            Assignment shifted = new Assignment(f.id(), f.employeeCode(), f.type(), f.unit(), f.address(),
                    newEnd.plusDays(1), f.to(), f.decisionNo(), f.note());
            replace(next, f, shifted);
            changes.add(new AssignmentChange(Kind.ADJUSTED, f, shifted));
        }
        for (Assignment other : next) {
            if (other != updated && other.type().layer() == layer && !Objects.equals(other, updated)
                    && other.overlaps(updated.from(), updated.to())) {
                throw new AssignmentConflictException("Ngày kết thúc mới làm chồng lấn với: " + other + ".");
            }
        }
        return new Result(new AssignmentHistory(employeeCode, next), changes);
    }

    // ------------------------------------------------------------------ tra cứu

    /** Nơi làm việc thực tế trong ngày: đang công tác thì là nơi công tác, ngược lại là nơi chính. */
    public Optional<Assignment> locationOn(LocalDate date) {
        Optional<Assignment> trip = sameLayer(Layer.TEMPORARY).stream().filter(t -> t.covers(date)).findFirst();
        return trip.isPresent() ? trip : sameLayer(Layer.PRIMARY).stream().filter(p -> p.covers(date)).findFirst();
    }

    /** Dòng thời gian thực tế trong [start, end], gộp các ngày liên tiếp cùng một nơi. */
    public List<Segment> timeline(LocalDate start, LocalDate end) {
        List<Segment> out = new ArrayList<>();
        LocalDate segStart = start;
        Assignment segA = locationOn(start).orElse(null);
        for (LocalDate d = start.plusDays(1); !d.isAfter(end); d = d.plusDays(1)) {
            Assignment a = locationOn(d).orElse(null);
            if (!Objects.equals(a, segA)) {
                out.add(new Segment(segStart, d.minusDays(1), segA));
                segStart = d;
                segA = a;
            }
        }
        out.add(new Segment(segStart, end, segA));
        return out;
    }

    /** Số ngày làm việc tại từng nơi trong kỳ (vd để đối soát với khách hàng, tính công tác phí). */
    public Map<String, Long> workingDaysByPlace(LocalDate start, LocalDate end, WorkCalendar calendar) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (calendar.isWorkingDay(d)) {
                String label = locationOn(d).map(Assignment::placeLabel).orElse("(chưa có dữ liệu)");
                out.merge(label, 1L, Long::sum);
            }
        }
        return out;
    }

    /** Các khoảng không có nơi làm việc chính nào – dữ liệu thiếu cần HR bổ sung. */
    public List<Segment> gaps(LocalDate start, LocalDate end) {
        return timeline(start, end).stream().filter(s -> s.assignment() == null).toList();
    }

    // ------------------------------------------------------------------ nội bộ

    private void validate(Assignment a) {
        if (!employeeCode.equals(a.employeeCode())) {
            throw new AssignmentConflictException("Sai nhân viên: " + a.employeeCode());
        }
        if (a.from() == null || a.type() == null) {
            throw new AssignmentConflictException("Thiếu loại hoặc ngày bắt đầu.");
        }
        if (a.to() != null && a.to().isBefore(a.from())) {
            throw new AssignmentConflictException("Ngày kết thúc trước ngày bắt đầu.");
        }
        if (a.type() == WorkLocationType.CONG_TAC && a.to() == null) {
            throw new AssignmentConflictException("Đi công tác phải có ngày về.");
        }
        if ((a.type() == WorkLocationType.CONG_TAC || a.type() == WorkLocationType.KHACH_HANG)
                && (a.unit() == null || a.unit().isBlank())) {
            throw new AssignmentConflictException("Cần ghi rõ đơn vị/nơi đến.");
        }
    }

    private List<Assignment> sameLayer(Layer layer) {
        return entries.stream().filter(e -> e.type().layer() == layer).toList();
    }

    private static void replace(List<Assignment> list, Assignment old, Assignment neu) {
        int i = list.indexOf(old);
        list.set(i, neu);
    }

    private static String join(List<Assignment> list) {
        return String.join("; ", list.stream().map(Assignment::toString).toList());
    }
}

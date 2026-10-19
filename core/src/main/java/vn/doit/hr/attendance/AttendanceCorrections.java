package vn.doit.hr.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import vn.doit.hr.attendance.PunchRequest.Type;
import vn.doit.hr.attendance.PunchVerdict.Status;
import vn.doit.hr.attendance.Timesheet.Punch;
import vn.doit.hr.attendance.Timesheet.Punch.Source;

/**
 * Giải trình và duyệt chấm công.
 *
 * <p>Ba đường để một lần chấm công được tính:
 * <ol>
 *   <li><b>Duyệt lần chấm chờ duyệt</b> ({@link #review}): lần chấm trên app bị NEEDS_REVIEW (GPS kém, sát ranh giới,
 *       thiết bị mới...) – quản lý bấm Duyệt / Từ chối.</li>
 *   <li><b>Giải trình</b> ({@link #submit} → {@link #decide}): nhân viên gửi giờ vào/ra kèm lý do (lần chấm bị từ chối,
 *       quên chấm, app lỗi...) – quản lý duyệt.</li>
 *   <li><b>Chấm tay</b> ({@link #manualPunch}): quản lý trực tiếp ghi giờ vào/ra cho nhân viên, bắt buộc ghi lý do.</li>
 * </ol>
 *
 * <p>Quy tắc chung: người duyệt phải là quản lý trực tiếp của nhân viên hoặc HR, không tự duyệt cho mình;
 * mọi quyết định lưu người duyệt, thời điểm, lý do; tháng đã <b>khóa công</b> thì không sửa được nữa.
 */
public final class AttendanceCorrections {

    /** Quan hệ quản lý để kiểm tra quyền duyệt. */
    public interface OrgChart {
        boolean isManagerOf(String approverCode, String employeeCode);

        boolean isHr(String code);
    }

    /** Khóa công: sau ngày {@code lockDayOfNextMonth} của tháng sau thì công tháng trước không sửa được. */
    public record LockPolicy(int lockDayOfNextMonth) {
        public boolean isLocked(LocalDate workDate, LocalDate today) {
            return today.isAfter(YearMonth.from(workDate).plusMonths(1).atDay(lockDayOfNextMonth));
        }
    }

    public enum RequestStatus { CHO_DUYET, DA_DUYET, TU_CHOI }

    public enum RequestKind {
        GIAI_TRINH("Giải trình lần chấm bị từ chối"),
        BO_SUNG("Bổ sung chấm công (quên chấm, app lỗi)");

        private final String label;

        RequestKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Đơn giải trình / bổ sung chấm công. */
    public record Request(long id, String employeeCode, LocalDate workDate, RequestKind kind, List<Punch> proposed,
                          String reason, RequestStatus status, String decidedBy, Instant decidedAt, String decisionNote) {
        public Request {
            proposed = List.copyOf(proposed);
        }
    }

    /** Lần chấm trên app đã lưu, kèm kết quả kiểm tra và (nếu có) quyết định duyệt. */
    public record StoredPunch(long id, String employeeCode, Punch punch, Status status,
                              String reviewedBy, Instant reviewedAt, String reviewNote) {
    }

    /** Lần chấm do quản lý ghi tay. */
    public record ManualPunch(String employeeCode, Punch punch, String createdBy, String reason, Instant createdAt) {
    }

    private AttendanceCorrections() {
    }

    // ------------------------------------------------------------------ 1. duyệt lần chấm chờ duyệt

    public static StoredPunch review(StoredPunch p, String approver, boolean approve, String note, Instant now,
                                     OrgChart org, LockPolicy lock, ZoneId zone) {
        if (p.status() != Status.NEEDS_REVIEW) {
            throw new AttendanceRuleException("Chỉ duyệt được lần chấm đang chờ duyệt (hiện: " + p.status() + ").");
        }
        LocalDate workDate = p.punch().time().atZone(zone).toLocalDate();
        checkApprover(approver, p.employeeCode(), org);
        checkNotLocked(workDate, now.atZone(zone).toLocalDate(), lock);
        if (!approve) {
            requireText(note, "Từ chối phải ghi lý do");
        }
        return new StoredPunch(p.id(), p.employeeCode(), p.punch(), approve ? Status.ACCEPTED : Status.REJECTED,
                approver, now, note);
    }

    // ------------------------------------------------------------------ 2. giải trình

    public static Request submit(String employeeCode, LocalDate workDate, RequestKind kind, List<Instant> times,
                                 List<Type> types, String reason, Instant now, LockPolicy lock, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        requireText(reason, "Cần ghi lý do giải trình");
        if (workDate.isAfter(today)) {
            throw new AttendanceRuleException("Không giải trình trước cho ngày chưa tới.");
        }
        checkNotLocked(workDate, today, lock);
        if (times.isEmpty() || times.size() != types.size()) {
            throw new AttendanceRuleException("Cần ít nhất một giờ vào hoặc giờ ra.");
        }
        List<Punch> proposed = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            Instant t = times.get(i);
            if (!t.atZone(zone).toLocalDate().equals(workDate)) {
                throw new AttendanceRuleException("Giờ đề xuất phải nằm trong ngày " + workDate + ".");
            }
            if (t.isAfter(now)) {
                throw new AttendanceRuleException("Giờ đề xuất không được sau thời điểm hiện tại.");
            }
            proposed.add(new Punch(t, types.get(i), Source.GIAI_TRINH));
        }
        return new Request(0, employeeCode, workDate, kind, proposed, reason.trim(), RequestStatus.CHO_DUYET, null, null, null);
    }

    public static Request decide(Request r, String approver, boolean approve, String note, Instant now,
                                 OrgChart org, LockPolicy lock, ZoneId zone) {
        if (r.status() != RequestStatus.CHO_DUYET) {
            throw new AttendanceRuleException("Đơn đã được xử lý (" + r.status() + ").");
        }
        checkApprover(approver, r.employeeCode(), org);
        checkNotLocked(r.workDate(), now.atZone(zone).toLocalDate(), lock);
        if (!approve) {
            requireText(note, "Từ chối phải ghi lý do");
        }
        return new Request(r.id(), r.employeeCode(), r.workDate(), r.kind(), r.proposed(), r.reason(),
                approve ? RequestStatus.DA_DUYET : RequestStatus.TU_CHOI, approver, now, note);
    }

    // ------------------------------------------------------------------ 3. chấm tay

    public static ManualPunch manualPunch(String employeeCode, Instant time, Type type, String manager, String reason,
                                          Instant now, OrgChart org, LockPolicy lock, ZoneId zone) {
        checkApprover(manager, employeeCode, org);
        requireText(reason, "Chấm tay phải ghi lý do");
        if (time.isAfter(now)) {
            throw new AttendanceRuleException("Không chấm tay trước cho thời điểm chưa tới.");
        }
        checkNotLocked(time.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate(), lock);
        return new ManualPunch(employeeCode, new Punch(time, type, Source.DUYET_TAY), manager, reason.trim(), now);
    }

    // ------------------------------------------------------------------ tổng hợp

    /** Các lần chấm được tính công: app đã chấp nhận + giải trình đã duyệt + chấm tay. */
    public static List<Punch> effectivePunches(List<StoredPunch> app, List<Request> requests, List<ManualPunch> manual) {
        List<Punch> out = new ArrayList<>();
        app.stream().filter(p -> p.status() == Status.ACCEPTED).map(StoredPunch::punch).forEach(out::add);
        requests.stream().filter(r -> r.status() == RequestStatus.DA_DUYET).flatMap(r -> r.proposed().stream()).forEach(out::add);
        manual.stream().map(ManualPunch::punch).forEach(out::add);
        return out;
    }

    // ------------------------------------------------------------------ nội bộ

    private static void checkApprover(String approver, String employee, OrgChart org) {
        if (approver == null || approver.equals(employee)) {
            throw new AttendanceRuleException("Không được tự duyệt chấm công cho chính mình.");
        }
        if (!org.isManagerOf(approver, employee) && !org.isHr(approver)) {
            throw new AttendanceRuleException("Chỉ quản lý trực tiếp hoặc HR mới được duyệt chấm công của " + employee + ".");
        }
    }

    private static void checkNotLocked(LocalDate workDate, LocalDate today, LockPolicy lock) {
        if (lock.isLocked(workDate, today)) {
            throw new AttendanceRuleException("Công tháng " + YearMonth.from(workDate) + " đã khóa, không sửa được nữa.");
        }
    }

    private static void requireText(String s, String message) {
        if (s == null || s.isBlank()) {
            throw new AttendanceRuleException(message + ".");
        }
    }
}

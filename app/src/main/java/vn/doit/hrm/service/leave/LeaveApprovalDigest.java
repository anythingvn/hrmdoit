package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.reminder.ReminderRecipients;

/**
 * Mục "Đơn chờ bạn duyệt" trong email nhắc việc hằng ngày (spec 5.3): đơn nghỉ và đơn làm thêm giờ đang chờ, gửi tới đúng người
 * duyệt của đơn — trưởng nhóm / trưởng phòng lưu trên đơn, mọi tài khoản BGĐ với đơn cấp BGĐ, mọi tài khoản Quản trị với đơn cấp
 * Quản trị. HR và Quản trị duyệt thay được mọi đơn nhưng không nhận danh sách các đơn không giao cho họ. Không ai nhận đơn của chính
 * mình; tài khoản bị khóa không nhận. Không có lý do nghỉ.
 */
@Service
@Transactional(readOnly = true)
public class LeaveApprovalDigest {

    public static final String TITLE = "Đơn chờ bạn duyệt";

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LeaveRequestRepository leaveRepository;
    private final OvertimeRequestRepository overtimeRepository;
    private final UserRepository users;
    private final ReminderRecipients recipients;

    public LeaveApprovalDigest(
        LeaveRequestRepository leaveRepository,
        OvertimeRequestRepository overtimeRepository,
        UserRepository users,
        ReminderRecipients recipients
    ) {
        this.leaveRepository = leaveRepository;
        this.overtimeRepository = overtimeRepository;
        this.users = users;
        this.recipients = recipients;
    }

    /** email (chữ thường) → các dòng đơn chờ người đó duyệt, theo thứ tự gửi. Người không có đơn chờ không có trong kết quả. */
    public Map<String, List<String>> byEmail() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Map<String, List<User>> roleUsers = new LinkedHashMap<>();
        Map<Long, Optional<String>> emailCache = new LinkedHashMap<>();
        for (LeaveRequest r : leaveRepository.findByStatusInOrderByCreatedAtAscIdAsc(LeaveRequestService.PENDING)) {
            String line = line(r);
            for (String email : approverEmails(r.getApproverKind(), r.getApproverEmployee(), r.getEmployee(), roleUsers, emailCache)) {
                out.computeIfAbsent(email, k -> new ArrayList<>()).add(line);
            }
        }
        for (OvertimeRequest o : overtimeRepository.findByStatusInOrderByCreatedAtAscIdAsc(OvertimeService.INBOX)) {
            String line = line(o);
            for (String email : approverEmails(o.getApproverKind(), o.getApproverEmployee(), o.getEmployee(), roleUsers, emailCache)) {
                out.computeIfAbsent(email, k -> new ArrayList<>()).add(line);
            }
        }
        return out;
    }

    /** Đoạn văn bản của mục trong email. */
    public static String render(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append(TITLE).append(" (").append(lines.size()).append(")\n");
        lines.forEach(l -> sb.append(l).append('\n'));
        sb.append("Vào mục \"Duyệt đơn\" trên hệ thống để duyệt hoặc từ chối.\n\n");
        return sb.toString();
    }

    private Set<String> approverEmails(
        ApproverKind kind,
        Employee approver,
        Employee requester,
        Map<String, List<User>> roleUsers,
        Map<Long, Optional<String>> emailCache
    ) {
        Set<String> to = new LinkedHashSet<>();
        if (kind == ApproverKind.BGD || kind == ApproverKind.QUAN_TRI) {
            String role = kind == ApproverKind.BGD ? AuthoritiesConstants.BGD : AuthoritiesConstants.ADMIN;
            for (User u : roleUsers.computeIfAbsent(role, k -> users.findActiveWithAnyAuthority(List.of(k)))) {
                if (!ApproverResolver.isSelf(u.getLogin(), requester)) {
                    emailCache.computeIfAbsent(u.getId(), id -> recipients.emailOf(u.getLogin())).ifPresent(to::add);
                }
            }
        } else if (approver != null) {
            User u = approver.getUser();
            if (u != null && u.isActivated() && !ApproverResolver.isSelf(u.getLogin(), requester)) {
                emailCache
                    .computeIfAbsent(u.getId(), id -> ReminderRecipients.usable(u.getEmail()).or(() -> ReminderRecipients.usable(approver.getEmail())))
                    .ifPresent(to::add);
            }
        }
        return to;
    }

    private static String line(LeaveRequest r) {
        String range = r.getFromDate().equals(r.getToDate())
            ? r.getFromDate().format(DMY)
            : r.getFromDate().format(DMY) + " – " + r.getToDate().format(DMY);
        String what = r.getStatus() == LeaveRequestStatus.CHO_HUY ? "Xin hủy " + lower(r.getType().label()) : r.getType().label();
        return " • [" + what + "] " + who(r.getEmployee()) + ": " + range + " (" + plain(r.getDays()) + " ngày làm việc)";
    }

    private static String line(OvertimeRequest o) {
        String what = o.getStatus() == OvertimeStatus.CHO_XAC_NHAN_THEM ? "Giờ làm thêm vượt đăng ký" : "Làm thêm giờ";
        BigDecimal hours = o.getStatus() == OvertimeStatus.CHO_XAC_NHAN_THEM && o.getActualHours() != null ? o.getActualHours() : o.getPlannedHours();
        return (
            " • [" +
            what +
            "] " +
            who(o.getEmployee()) +
            ": " +
            o.getWorkDate().format(DMY) +
            ", " +
            o.getStartTime() +
            " – " +
            o.getEndTime() +
            " (" +
            plain(hours) +
            " giờ)"
        );
    }

    private static String who(Employee e) {
        return e.getFullName() + (e.getCode() == null ? "" : " (" + e.getCode() + ")");
    }

    private static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static String lower(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}

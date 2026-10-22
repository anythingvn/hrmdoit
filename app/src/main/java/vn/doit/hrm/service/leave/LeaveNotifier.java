package vn.doit.hrm.service.leave;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.reminder.ReminderMailer;

/**
 * Email về đơn (spec 5.3): tới người duyệt khi có đơn mới, yêu cầu hủy hoặc được chuyển đơn; tới người gửi khi có quyết định. Người
 * duyệt cấp BGĐ / Quản trị: mọi tài khoản đang hoạt động có vai trò đó (trừ người xin). Thư gửi sau khi transaction commit (không
 * gửi cho thay đổi bị hủy, không giữ khóa sổ trong lúc gửi); lỗi gửi chỉ ghi log. Thư không chứa lý do nghỉ.
 */
@Component
public class LeaveNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(LeaveNotifier.class);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private record Mail(String to, String subject, String text) {}

    private final ReminderMailer mailer;
    private final UserRepository users;

    public LeaveNotifier(ReminderMailer mailer, UserRepository users) {
        this.mailer = mailer;
        this.users = users;
    }

    /** Đơn mới chờ duyệt. */
    public void submitted(LeaveRequest r) {
        toApprovers(r, "Đơn nghỉ mới chờ duyệt: " + who(r.getEmployee()), describe(r) + "\n\nVào mục \"Duyệt đơn\" để duyệt hoặc từ chối.");
    }

    /** Người gửi yêu cầu hủy đơn đã duyệt. */
    public void cancelRequested(LeaveRequest r) {
        toApprovers(r, "Yêu cầu hủy đơn nghỉ: " + who(r.getEmployee()), describe(r) + "\n\nVào mục \"Duyệt đơn\" để đồng ý hoặc từ chối hủy.");
    }

    /** Đơn được chuyển sang người duyệt mới. */
    public void reassigned(LeaveRequest r) {
        toApprovers(r, "Đơn nghỉ chuyển sang bạn duyệt: " + who(r.getEmployee()), describe(r) + "\n\nVào mục \"Duyệt đơn\" để xử lý.");
    }

    /** Có quyết định về đơn (duyệt, từ chối, hủy, từ chối hủy). */
    public void decided(LeaveRequest r) {
        String result = switch (r.getStatus()) {
            case DA_DUYET -> "đã được duyệt";
            case TU_CHOI -> "bị từ chối";
            case DA_HUY -> "đã được hủy";
            default -> "đã được cập nhật";
        };
        String text = describe(r) + "\nKết quả: " + result + (r.getDecisionNote() == null ? "" : "\nGhi chú: " + r.getDecisionNote());
        String to = address(r.getEmployee());
        if (to != null) {
            send(List.of(new Mail(to, "Đơn nghỉ " + result, text)));
        }
    }

    private void toApprovers(LeaveRequest r, String subject, String text) {
        Set<String> to = new LinkedHashSet<>();
        if (r.getApproverKind() == ApproverKind.BGD || r.getApproverKind() == ApproverKind.QUAN_TRI) {
            String role = r.getApproverKind() == ApproverKind.BGD ? AuthoritiesConstants.BGD : AuthoritiesConstants.ADMIN;
            User self = r.getEmployee().getUser();
            for (User u : users.findActiveWithAnyAuthority(List.of(role))) {
                if ((self == null || !u.getId().equals(self.getId())) && notBlank(u.getEmail())) {
                    to.add(u.getEmail().trim());
                }
            }
        } else if (r.getApproverEmployee() != null) {
            String a = address(r.getApproverEmployee());
            if (a != null) {
                to.add(a);
            }
        }
        List<Mail> mails = new ArrayList<>();
        for (String t : to) {
            mails.add(new Mail(t, subject, text));
        }
        send(mails);
    }

    private void send(List<Mail> mails) {
        if (mails.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deliver(mails);
                    }
                }
            );
        } else {
            deliver(mails);
        }
    }

    private void deliver(List<Mail> mails) {
        boolean configured;
        try {
            configured = mailer.configured();
        } catch (RuntimeException e) {
            LOG.warn("Không đọc được cấu hình email; bỏ qua thư về đơn nghỉ", e);
            return;
        }
        if (!configured) {
            LOG.debug("Chưa cấu hình email; bỏ qua {} thư về đơn nghỉ", mails.size());
            return;
        }
        for (Mail m : mails) {
            try {
                mailer.send(m.to(), m.subject(), m.text());
            } catch (RuntimeException e) {
                LOG.warn("Không gửi được email về đơn nghỉ tới {}: {}", m.to(), e.getMessage());
            }
        }
    }

    private static String describe(LeaveRequest r) {
        String range = r.getFromDate().equals(r.getToDate())
            ? r.getFromDate().format(DMY)
            : r.getFromDate().format(DMY) + " – " + r.getToDate().format(DMY);
        return (
            "Người xin: " +
            who(r.getEmployee()) +
            "\nLoại: " +
            r.getType().label() +
            "\nThời gian: " +
            range +
            " (" +
            r.getDays().stripTrailingZeros().toPlainString() +
            " ngày làm việc)"
        );
    }

    private static String who(Employee e) {
        return e.getFullName() + (e.getCode() == null ? "" : " (" + e.getCode() + ")");
    }

    /** Email tài khoản, nếu không có thì email trên hồ sơ. */
    private static String address(Employee e) {
        if (e == null) {
            return null;
        }
        User u = e.getUser();
        if (u != null && notBlank(u.getEmail())) {
            return u.getEmail().trim();
        }
        return notBlank(e.getEmail()) ? e.getEmail().trim() : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

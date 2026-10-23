package vn.doit.hrm.service.attendance;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.doit.hrm.domain.AttendanceExplanation;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.OnsiteLead;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.OnsiteLeadRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.reminder.ReminderMailer;

/**
 * Email về giải trình chấm công (spec 3.7), theo cách {@code LeaveNotifier} làm: thư gửi sau khi transaction commit (không gửi cho thay
 * đổi bị hủy, không giữ khóa trong lúc gửi); lỗi gửi chỉ ghi log. Người nhận giải trình mới: trưởng nhóm ({@code Employee.manager}); không
 * có (hoặc đã nghỉ, đã lưu trữ, không có email) thì trưởng nhóm onsite của khách hàng mà người đó onsite đúng ngày đó; không có thì HR.
 * Thư không chứa lý do giải trình, tọa độ hay IP.
 */
@Component
public class AttendanceNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceNotifier.class);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private record Mail(String to, String subject, String text) {}

    private final ReminderMailer mailer;
    private final UserRepository users;
    private final EmployeeRepository employees;
    private final OnsiteLeadRepository onsiteLeads;
    private final WorkAssignmentRepository assignments;

    public AttendanceNotifier(
        ReminderMailer mailer,
        UserRepository users,
        EmployeeRepository employees,
        OnsiteLeadRepository onsiteLeads,
        WorkAssignmentRepository assignments
    ) {
        this.mailer = mailer;
        this.users = users;
        this.employees = employees;
        this.onsiteLeads = onsiteLeads;
        this.assignments = assignments;
    }

    /** Giải trình mới chờ duyệt. */
    public void explanationSubmitted(AttendanceExplanation x, Employee requester) {
        String subject = "Giải trình chấm công chờ duyệt: " + who(requester);
        String text = describe(x, requester) + "\n\nVào mục \"Duyệt đơn\" để đồng ý hoặc từ chối.";
        List<Mail> mails = new ArrayList<>();
        for (String to : reviewers(requester, x)) {
            mails.add(new Mail(to, subject, text));
        }
        send(mails);
    }

    /** Có kết quả giải trình (duyệt hoặc từ chối): báo nhân viên. */
    public void explanationDecided(AttendanceExplanation x, Employee requester) {
        String result = switch (x.getStatus()) {
            case DA_DUYET -> "được duyệt";
            case TU_CHOI -> "bị từ chối";
            default -> "đã được cập nhật";
        };
        String to = address(requester);
        if (to == null) {
            return;
        }
        String text =
            describe(x, requester) + "\nKết quả: " + result + (x.getDecisionNote() == null ? "" : "\nGhi chú: " + x.getDecisionNote());
        send(List.of(new Mail(to, "Giải trình chấm công ngày " + x.getWorkDay().format(DMY) + " " + result, text)));
    }

    /** Trưởng nhóm, nếu không có thì trưởng nhóm onsite đúng ngày, nếu không có thì HR. Không gửi cho chính người giải trình. */
    private Set<String> reviewers(Employee requester, AttendanceExplanation x) {
        Set<String> to = new LinkedHashSet<>();
        Employee manager = requester.getManager();
        if (activeOther(manager, requester)) {
            add(to, address(manager));
        }
        if (to.isEmpty()) {
            for (WorkAssignment w : assignments.findOverlapping(List.of(requester.getId()), x.getWorkDay(), x.getWorkDay())) {
                if (w.getType() != WorkLocationType.KHACH_HANG || w.getSite() == null) {
                    continue;
                }
                for (OnsiteLead l : onsiteLeads.findByWorkSiteId(w.getSite().getId())) {
                    Employee lead = employees.findById(l.getEmployeeId()).orElse(null);
                    if (activeOther(lead, requester)) {
                        add(to, address(lead));
                    }
                }
            }
        }
        if (to.isEmpty()) {
            User self = requester.getUser();
            for (User u : users.findActiveWithAnyAuthority(List.of(AuthoritiesConstants.HR))) {
                if (self == null || !Objects.equals(u.getId(), self.getId())) {
                    add(to, u.getEmail());
                }
            }
        }
        return to;
    }

    /** Trưởng nhóm còn làm việc, chưa lưu trữ, không phải chính người giải trình. */
    private static boolean activeOther(Employee lead, Employee requester) {
        return (
            lead != null &&
            !lead.isArchived() &&
            lead.getStatus() != EmploymentStatus.DA_NGHI &&
            !Objects.equals(lead.getId(), requester.getId())
        );
    }

    private static void add(Set<String> to, String address) {
        if (address != null && !address.isBlank()) {
            to.add(address.trim());
        }
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
            LOG.warn("Không đọc được cấu hình email; bỏ qua thư về giải trình chấm công", e);
            return;
        }
        if (!configured) {
            LOG.debug("Chưa cấu hình email; bỏ qua {} thư về giải trình chấm công", mails.size());
            return;
        }
        for (Mail m : mails) {
            try {
                mailer.send(m.to(), m.subject(), m.text());
            } catch (RuntimeException e) {
                LOG.warn("Không gửi được email về giải trình chấm công tới {}: {}", m.to(), e.getMessage());
            }
        }
    }

    private static String describe(AttendanceExplanation x, Employee requester) {
        return (
            "Người giải trình: " +
            who(requester) +
            "\nNgày: " +
            x.getWorkDay().format(DMY) +
            "\nGiờ đề xuất: " +
            AttendanceTimes.inOut(x.getProposedIn(), x.getProposedOut())
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
        if (u != null && u.getEmail() != null && !u.getEmail().isBlank()) {
            return u.getEmail().trim();
        }
        return e.getEmail() != null && !e.getEmail().isBlank() ? e.getEmail().trim() : null;
    }
}

package vn.doit.hrm.service.reminder;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import vn.doit.hr.reminder.Reminder;
import vn.doit.hr.reminder.ReminderDigest;
import vn.doit.hr.reminder.ReminderEngine;
import vn.doit.hr.reminder.ReminderPolicy;
import vn.doit.hr.reminder.ReminderType;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.reminder.ReminderSnapshotService.Loaded;
import vn.doit.hrm.service.reminder.ReminderSnapshotService.Ref;
import vn.doit.hrm.service.reminder.dto.ReminderStatusDTO;
import vn.doit.hrm.service.reminder.dto.UpcomingDTO;

/** Việc sắp tới trong 30 ngày (trang Tổng quan), tình trạng email nhắc việc và gửi thử (Quản trị). */
@Service
public class ReminderService {

    public static final int HORIZON_DAYS = 30;

    private final Clock clock;
    private final WorkCalendarService calendarService;
    private final ReminderSnapshotService snapshots;
    private final ReminderRecipients recipients;
    private final ReminderMailer mailer;
    private final ReminderStore store;

    public ReminderService(
        Clock clock,
        WorkCalendarService calendarService,
        ReminderSnapshotService snapshots,
        ReminderRecipients recipients,
        ReminderMailer mailer,
        ReminderStore store
    ) {
        this.clock = clock;
        this.calendarService = calendarService;
        this.snapshots = snapshots;
        this.recipients = recipients;
        this.mailer = mailer;
        this.store = store;
    }

    /** {@code type} null = mọi loại. Quá hạn trước, rồi theo ngày. */
    public List<UpcomingDTO> upcoming(ReminderType type) {
        LocalDate today = LocalDate.now(clock);
        Loaded data = snapshots.load();
        return reminders(today, data)
            .stream()
            .filter(r -> type == null || r.type() == type)
            .map(r -> toDto(r, data.refs().get(r.employeeCode())))
            .toList();
    }

    public ReminderStatusDTO status() {
        return new ReminderStatusDTO(mailer.configured(), store.lastRun().orElse(null), recipients.resolve(snapshots.load()).missing());
    }

    /** Gửi bản tổng hợp 30 ngày tới email của người đang đăng nhập; không ghi nhật ký nhắc. Trả về địa chỉ đã gửi. */
    public String testSend(String login) {
        if (!mailer.configured()) {
            throw new EmployeeRuleException("Chưa cấu hình máy chủ gửi email (SMTP).", "mailnotconfigured");
        }
        String to = recipients
            .emailOf(login)
            .orElseThrow(() -> new EmployeeRuleException("Tài khoản của bạn chưa có email.", "noemail"));
        LocalDate today = LocalDate.now(clock);
        List<Reminder> items = reminders(today, snapshots.load());
        String text = items.isEmpty() ? "Không có nhắc việc trong " + HORIZON_DAYS + " ngày tới.\n" : ReminderDigest.renderText(today, items);
        try {
            mailer.send(to, "[Gửi thử] " + ReminderDigest.subject(today, items), text);
        } catch (MailException e) {
            throw new EmployeeRuleException("Không gửi được email tới " + to + ": " + e.getMessage(), "mailsendfailed");
        }
        return to;
    }

    private List<Reminder> reminders(LocalDate today, Loaded data) {
        return new ReminderEngine(ReminderPolicy.defaults(), calendarService.calendar(today)).upcoming(today, data.employees(), HORIZON_DAYS);
    }

    private static UpcomingDTO toDto(Reminder r, Ref ref) {
        Long contractId =
            switch (r.type()) {
                case PROBATION_END, PROBATION_RESULT_MISSING -> ref.probationContractId();
                case CONTRACT_EXPIRY, CONTRACT_OVERDUE -> ref.fixedContractId();
                default -> null;
            };
        Long salaryChangeId = r.type() == ReminderType.SALARY_REVIEW || r.type() == ReminderType.SALARY_REVIEW_OVERDUE ? ref.salaryChangeId() : null;
        return new UpcomingDTO(
            r.type(),
            r.type().label(),
            ref.employeeId(),
            r.employeeCode(),
            r.employeeName(),
            r.eventDate(),
            r.daysLeft(),
            r.urgent(),
            r.message(),
            contractId,
            salaryChangeId
        );
    }
}

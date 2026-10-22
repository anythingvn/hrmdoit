package vn.doit.hrm.service.reminder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.doit.hr.reminder.Reminder;
import vn.doit.hr.reminder.ReminderDigest;
import vn.doit.hr.reminder.ReminderEngine;
import vn.doit.hr.reminder.ReminderPolicy;
import vn.doit.hrm.service.leave.LeaveApprovalDigest;
import vn.doit.hrm.service.reminder.ReminderSnapshotService.Loaded;

/**
 * Email nhắc việc hằng ngày, 7:30 giờ Việt Nam, chỉ ngày làm việc; mỗi người nhận một email. Người duyệt có đơn nghỉ / làm thêm đang
 * chờ mình nhận thêm mục "Đơn chờ bạn duyệt (N)" ở đầu email (không có đơn thì không có mục).
 * Gửi lỗi một địa chỉ thì lần chạy sau chỉ gửi lại cho địa chỉ đó; mốc chỉ vào {@code reminder_log} khi mọi người nhận đã có.
 */
@Component
public class ReminderJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderJob.class);
    static final LocalTime SEND_AT = LocalTime.of(7, 30);
    private static final int MAX_ERROR = 1000;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FOOTER = "— Email tự động từ hệ thống HRM.\n";

    public record RunResult(int recipients, int items, String error) {}

    private final Clock clock;
    private final WorkCalendarService calendarService;
    private final ReminderSnapshotService snapshots;
    private final ReminderRecipients recipients;
    private final ReminderMailer mailer;
    private final ReminderStore store;
    private final LeaveApprovalDigest approvals;

    public ReminderJob(
        Clock clock,
        WorkCalendarService calendarService,
        ReminderSnapshotService snapshots,
        ReminderRecipients recipients,
        ReminderMailer mailer,
        ReminderStore store,
        LeaveApprovalDigest approvals
    ) {
        this.clock = clock;
        this.calendarService = calendarService;
        this.snapshots = snapshots;
        this.recipients = recipients;
        this.mailer = mailer;
        this.store = store;
        this.approvals = approvals;
    }

    @Scheduled(cron = "0 30 7 * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduled() {
        run(true);
    }

    /** Server bật sau 7:30 của ngày làm việc mà hôm nay chưa gửi được: gửi bù. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUp() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            if (now.toLocalTime().isBefore(SEND_AT) || !calendarService.isWorkingDay(now.toLocalDate()) || store.ranSuccessfully(now.toLocalDate())) {
                return;
            }
            run(true);
        } catch (RuntimeException e) {
            LOG.warn("Không chạy bù được nhắc việc: {}", e.getMessage());
        }
    }

    public synchronized RunResult run(boolean automatic) {
        Instant started = clock.instant();
        LocalDate today = LocalDate.ofInstant(started, clock.getZone());
        if (!calendarService.isWorkingDay(today)) {
            return new RunResult(0, 0, "Không phải ngày làm việc");
        }
        if (!mailer.configured()) {
            return finish(new ReminderStore.Run(today, started, automatic, 0, 0, "Chưa cấu hình SMTP"));
        }
        Loaded data = snapshots.load();
        List<Reminder> due = new ReminderEngine(ReminderPolicy.defaults(), calendarService.calendar(today)).run(today, data.employees(), store.log());
        Map<String, List<Reminder>> byEmail = ReminderDigest.byEmail(due, recipients.resolve(data).resolver());
        Set<String> delivered = store.deliveries(due);
        // Mục "Đơn chờ bạn duyệt": theo trạng thái đơn lúc chạy, mỗi người nhận một lần mỗi ngày.
        Map<String, List<String>> waiting = approvals.byEmail();
        Set<String> waitingSent = store.approvalDeliveries(today);
        Set<String> emails = new LinkedHashSet<>(byEmail.keySet());
        emails.addAll(waiting.keySet());
        List<String> errors = new ArrayList<>();
        int sentTo = 0;
        for (String email : emails) {
            List<Reminder> pending = byEmail
                .getOrDefault(email, List.of())
                .stream()
                .filter(r -> !delivered.contains(ReminderStore.deliveryKey(r, email)))
                .toList();
            List<String> requests = waitingSent.contains(email) ? List.of() : waiting.getOrDefault(email, List.of());
            if (pending.isEmpty() && requests.isEmpty()) {
                continue;
            }
            try {
                mailer.send(email, subject(today, pending, requests), text(today, pending, requests));
            } catch (RuntimeException e) {
                LOG.warn("Không gửi được email nhắc việc tới {}: {}", email, e.getMessage());
                errors.add(email + ": " + e.getMessage());
                continue;
            }
            store.markDelivered(pending, email, clock.instant());
            pending.forEach(r -> delivered.add(ReminderStore.deliveryKey(r, email)));
            if (!requests.isEmpty()) {
                store.markApprovalDelivered(today, email, clock.instant());
            }
            sentTo++;
        }
        Set<Reminder> incomplete = new HashSet<>();
        byEmail.forEach((email, items) ->
            items.stream().filter(r -> !delivered.contains(ReminderStore.deliveryKey(r, email))).forEach(incomplete::add)
        );
        Set<Reminder> addressed = new HashSet<>();
        byEmail.values().forEach(addressed::addAll);
        store.markLogged(due.stream().filter(r -> addressed.contains(r) && !incomplete.contains(r)).toList(), clock.instant());

        String error = errors.isEmpty() ? null : String.join("; ", errors);
        if (error != null && error.length() > MAX_ERROR) {
            error = error.substring(0, MAX_ERROR - 1) + "…";
        }
        return finish(new ReminderStore.Run(today, started, automatic, sentTo, due.size(), error));
    }

    private static String subject(LocalDate today, List<Reminder> pending, List<String> requests) {
        if (pending.isEmpty()) {
            return "[HRM] " + LeaveApprovalDigest.TITLE + " (" + requests.size() + ") – ngày " + today.format(DMY);
        }
        return ReminderDigest.subject(today, pending);
    }

    private static String text(LocalDate today, List<Reminder> pending, List<String> requests) {
        String head = requests.isEmpty() ? "" : LeaveApprovalDigest.render(requests);
        return head + (pending.isEmpty() ? FOOTER : ReminderDigest.renderText(today, pending));
    }

    private RunResult finish(ReminderStore.Run run) {
        store.recordRun(run, clock.instant());
        return new RunResult(run.recipients(), run.items(), run.error());
    }
}

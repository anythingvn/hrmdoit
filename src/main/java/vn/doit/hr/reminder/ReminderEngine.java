package vn.doit.hr.reminder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import vn.doit.hr.reminder.EmployeeSnapshot.Contract;
import vn.doit.hr.reminder.ReminderPolicy.Overdue;
import vn.doit.hr.reminder.ReminderPolicy.Recipient;

/**
 * Tính các lời nhắc cần gửi trong ngày {@code today}. Hàm thuần: không gửi mail, không ghi DB.
 * Job hằng ngày gọi {@link #run}, gửi kết quả, rồi ghi (eventKey, step) của từng lời nhắc vào {@link ReminderLog}.
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>Sự kiện sắp tới: mỗi mốc "nhắc trước N ngày" kích hoạt vào ngày làm việc gần nhất trước/đúng (ngày sự kiện − N).
 *       Nếu nhiều mốc cùng đến hạn (server nghỉ vài ngày, nhân viên mới nhập) chỉ gửi mốc gấp nhất.</li>
 *   <li>Đã quá hạn mà chưa xử lý: nhắc lặp theo {@link Overdue}, cũng có cơ chế bù khi lỡ ngày.</li>
 *   <li>Nhân viên đã nghỉ việc: bỏ qua.</li>
 * </ul>
 */
public class ReminderEngine {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int RENEWAL_WINDOW_DAYS = 30; // Điều 20.2 BLLĐ 2019

    private final ReminderPolicy policy;
    private final WorkCalendar calendar;

    public ReminderEngine(ReminderPolicy policy, WorkCalendar calendar) {
        this.policy = policy;
        this.calendar = calendar;
    }

    public List<Reminder> run(LocalDate today, List<EmployeeSnapshot> employees, ReminderLog log) {
        List<Reminder> out = new ArrayList<>();
        for (EmployeeSnapshot e : employees) {
            if (!e.active()) {
                continue;
            }
            contract(today, e, log).ifPresent(out::add);
            probation(today, e, log).ifPresent(out::add);
            salaryReview(today, e, log).ifPresent(out::add);
            birthday(today, e, log).ifPresent(out::add);
        }
        out.sort(Comparator.comparing(Reminder::urgent).reversed()
                .thenComparingLong(Reminder::daysLeft)
                .thenComparing(Reminder::employeeCode));
        return out;
    }

    // ---------------------------------------------------------------- hợp đồng

    private Optional<Reminder> contract(LocalDate today, EmployeeSnapshot e, ReminderLog log) {
        Optional<Contract> latest = e.latestContract().filter(Contract::fixedTerm);
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        Contract c = latest.get();
        LocalDate end = c.end();
        if (!today.isAfter(end)) {
            return upcoming(today, e, ReminderType.CONTRACT_EXPIRY, end, log, daysLeft -> {
                String when = daysLeft == 0 ? "hết hạn HÔM NAY (" + end.format(D) + ")"
                        : "hết hạn ngày " + end.format(D) + ", còn " + daysLeft + " ngày";
                String msg = "HĐLĐ số " + c.number() + " (" + c.start().format(D) + " – " + end.format(D) + ") " + when
                        + ". Cần quyết định tái ký hay chấm dứt; nếu chấm dứt phải thông báo bằng văn bản cho người lao động (Điều 45 BLLĐ 2019).";
                int k = e.consecutiveFixedTermContracts();
                if (k >= 2) {
                    msg += " LƯU Ý: đã ký " + k + " HĐ xác định thời hạn liên tiếp – nếu tiếp tục làm việc, HĐ tiếp theo phải là"
                            + " HĐ không xác định thời hạn (Điều 20 BLLĐ 2019, trừ các trường hợp ngoại lệ luật định).";
                }
                return msg;
            });
        }
        LocalDate deadline = end.plusDays(RENEWAL_WINDOW_DAYS);
        return overdue(today, e, ReminderType.CONTRACT_OVERDUE, end, log, d -> {
            long left = RENEWAL_WINDOW_DAYS - d;
            String tail = left == 0 ? "HÔM NAY là hạn cuối." : "còn " + left + " ngày.";
            return "HĐLĐ số " + c.number() + " đã hết hạn từ " + end.format(D) + " (" + d + " ngày) nhưng nhân viên vẫn đang làm việc."
                    + " Hạn ký HĐ mới: " + deadline.format(D) + ", " + tail
                    + " Quá hạn này HĐ sẽ trở thành HĐ không xác định thời hạn (Điều 20.2 BLLĐ 2019)."
                    + " Nếu nhân viên đã nghỉ, hãy cập nhật trạng thái nghỉ việc để dừng nhắc.";
        });
    }

    // ---------------------------------------------------------------- thử việc

    private Optional<Reminder> probation(LocalDate today, EmployeeSnapshot e, ReminderLog log) {
        LocalDate end = e.probationEnd();
        if (end == null || e.probationResultRecorded()) {
            return Optional.empty();
        }
        if (!today.isAfter(end)) {
            return upcoming(today, e, ReminderType.PROBATION_END, end, log, daysLeft ->
                    "Thử việc kết thúc " + (daysLeft == 0 ? "HÔM NAY" : "ngày " + end.format(D) + ", còn " + daysLeft + " ngày")
                            + ". Quản lý cần đánh giá; khi kết thúc thử việc công ty phải thông báo kết quả cho người lao động (Điều 27 BLLĐ 2019).");
        }
        return overdue(today, e, ReminderType.PROBATION_RESULT_MISSING, end, log, d ->
                "Thử việc đã kết thúc từ " + end.format(D) + " (" + d + " ngày) nhưng chưa ghi nhận kết quả thử việc.");
    }

    // ---------------------------------------------------------------- tăng lương

    private Optional<Reminder> salaryReview(LocalDate today, EmployeeSnapshot e, ReminderLog log) {
        Optional<LocalDate> due = e.nextSalaryReview();
        if (due.isEmpty()) {
            return Optional.empty();
        }
        LocalDate r = due.get();
        String last = e.lastSalaryChange() == null ? "" : " Lần điều chỉnh lương gần nhất: " + e.lastSalaryChange().format(D) + ".";
        if (!today.isAfter(r)) {
            return upcoming(today, e, ReminderType.SALARY_REVIEW, r, log, daysLeft ->
                    "Đến hạn xét tăng lương ngày " + r.format(D) + (daysLeft == 0 ? " (hôm nay)." : ", còn " + daysLeft + " ngày.") + last);
        }
        return overdue(today, e, ReminderType.SALARY_REVIEW_OVERDUE, r, log, d ->
                "Đã quá hạn xét tăng lương " + d + " ngày (hạn " + r.format(D) + ")." + last
                        + " Ghi nhận quyết định (tăng lương hoặc hẹn ngày xét lại) để dừng nhắc.");
    }

    // ---------------------------------------------------------------- sinh nhật

    private Optional<Reminder> birthday(LocalDate today, EmployeeSnapshot e, ReminderLog log) {
        if (e.birthDate() == null) {
            return Optional.empty();
        }
        LocalDate next = e.birthDate().withYear(today.getYear()); // 29/02 → 28/02 năm không nhuận
        if (next.isBefore(today)) {
            next = e.birthDate().withYear(today.getYear() + 1);
        }
        LocalDate b = next;
        return upcoming(today, e, ReminderType.BIRTHDAY, b, log, daysLeft ->
                "Sinh nhật " + (daysLeft == 0 ? "hôm nay" : "ngày " + b.format(DateTimeFormatter.ofPattern("dd/MM"))
                        + " (" + weekday(b) + ", còn " + daysLeft + " ngày)") + ".");
    }

    // ---------------------------------------------------------------- lõi

    private Optional<Reminder> upcoming(LocalDate today, EmployeeSnapshot e, ReminderType type, LocalDate event,
                                        ReminderLog log, java.util.function.LongFunction<String> message) {
        List<Integer> leads = policy.leadDays().getOrDefault(type, List.of());
        Integer due = null;
        for (int lead : leads) {
            LocalDate trigger = calendar.onOrBefore(event.minusDays(lead));
            if (!trigger.isAfter(today) && (due == null || lead < due)) {
                due = lead;
            }
        }
        if (due == null) {
            return Optional.empty();
        }
        String key = key(type, e, event);
        OptionalInt sent = log.smallestLeadSent(key);
        if (sent.isPresent() && sent.getAsInt() <= due) {
            return Optional.empty();
        }
        long daysLeft = ChronoUnit.DAYS.between(today, event);
        boolean urgent = type != ReminderType.BIRTHDAY && daysLeft <= 7;
        return Optional.of(new Reminder(type, e.code(), e.fullName(), event, daysLeft, urgent,
                recipients(type), message.apply(daysLeft), key, due));
    }

    private Optional<Reminder> overdue(LocalDate today, EmployeeSnapshot e, ReminderType type, LocalDate event,
                                       ReminderLog log, java.util.function.LongFunction<String> message) {
        Overdue od = policy.overdue().get(type);
        if (od == null) {
            return Optional.empty();
        }
        int d = (int) ChronoUnit.DAYS.between(event, today);
        if (d < 1 || d > od.maxDays()) {
            return Optional.empty();
        }
        // Mốc lịch gần nhất ≤ d trong dãy 1, 1+k, 1+2k, ..., maxDays
        int slot = d == od.maxDays() ? d : 1 + ((d - 1) / od.repeatEveryDays()) * od.repeatEveryDays();
        String key = key(type, e, event);
        if (log.overdueSent(key, slot)) {
            return Optional.empty();
        }
        return Optional.of(new Reminder(type, e.code(), e.fullName(), event, -d, true,
                recipients(type), message.apply(d), key, -slot));
    }

    private Set<Recipient> recipients(ReminderType type) {
        return policy.recipients().getOrDefault(type, Set.of(Recipient.HR));
    }

    private static String key(ReminderType type, EmployeeSnapshot e, LocalDate event) {
        return type + "|" + e.code() + "|" + event;
    }

    private static String weekday(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY -> "thứ Hai";
            case TUESDAY -> "thứ Ba";
            case WEDNESDAY -> "thứ Tư";
            case THURSDAY -> "thứ Năm";
            case FRIDAY -> "thứ Sáu";
            case SATURDAY -> "thứ Bảy";
            case SUNDAY -> "Chủ nhật";
        };
    }
}

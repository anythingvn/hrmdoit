package vn.doit.hr.reminder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.doit.hr.reminder.ReminderPolicy.Recipient;

/**
 * Gom lời nhắc thành <b>một email mỗi người nhận mỗi ngày</b> (thay vì một email cho từng sự kiện).
 */
public final class ReminderDigest {

    /** Tra email thực tế cho một vai trò, theo nhân viên (vd MANAGER của nhân viên X). */
    @FunctionalInterface
    public interface RecipientResolver {
        List<String> emails(Recipient role, String employeeCode);
    }

    private ReminderDigest() {
    }

    /** email → danh sách lời nhắc (giữ thứ tự ưu tiên của đầu vào, không trùng). */
    public static Map<String, List<Reminder>> byEmail(List<Reminder> reminders, RecipientResolver resolver) {
        Map<String, List<Reminder>> out = new LinkedHashMap<>();
        for (Reminder r : reminders) {
            for (Recipient role : Recipient.values()) {
                if (!r.recipients().contains(role)) {
                    continue;
                }
                for (String email : resolver.emails(role, r.employeeCode())) {
                    List<Reminder> list = out.computeIfAbsent(email.toLowerCase(), k -> new ArrayList<>());
                    if (!list.contains(r)) {
                        list.add(r);
                    }
                }
            }
        }
        return out;
    }

    public static String subject(LocalDate today, List<Reminder> items) {
        long urgent = items.stream().filter(Reminder::urgent).count();
        String d = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return urgent > 0
                ? "[HRM] " + urgent + " việc cần xử lý gấp – nhắc hạn ngày " + d
                : "[HRM] Nhắc hạn ngày " + d + " (" + items.size() + " mục)";
    }

    public static String renderText(LocalDate today, List<Reminder> items) {
        StringBuilder sb = new StringBuilder();
        section(sb, "CẦN XỬ LÝ GẤP", items.stream().filter(Reminder::urgent).toList());
        section(sb, "SẮP ĐẾN HẠN", items.stream()
                .filter(r -> !r.urgent() && r.type() != ReminderType.BIRTHDAY).toList());
        section(sb, "SINH NHẬT", items.stream().filter(r -> r.type() == ReminderType.BIRTHDAY).toList());
        sb.append("\n— Email tự động từ hệ thống HRM. Cập nhật dữ liệu trên hệ thống để dừng nhắc.\n");
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, List<Reminder> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append(title).append(" (").append(items.size()).append(")\n");
        for (Reminder r : items) {
            sb.append(" • [").append(r.type().label()).append("] ")
                    .append(r.employeeName()).append(" (").append(r.employeeCode()).append("): ")
                    .append(r.message()).append('\n');
        }
        sb.append('\n');
    }
}

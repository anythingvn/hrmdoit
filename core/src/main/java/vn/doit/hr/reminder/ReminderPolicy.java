package vn.doit.hr.reminder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cấu hình nhắc: nhắc trước bao nhiêu ngày, ai nhận, và nhắc lặp lại thế nào khi đã quá hạn.
 * Nên lưu thành bảng cấu hình để HR tự chỉnh; {@link #defaults()} là giá trị khởi đầu.
 */
public record ReminderPolicy(
        Map<ReminderType, List<Integer>> leadDays,
        Map<ReminderType, Set<Recipient>> recipients,
        Map<ReminderType, Overdue> overdue) {

    public enum Recipient { HR, MANAGER, EMPLOYEE, DIRECTOR }

    /**
     * Nhắc lặp khi quá hạn: ngày quá hạn thứ 1, rồi cứ mỗi {@code repeatEveryDays} ngày,
     * và luôn nhắc vào ngày thứ {@code maxDays}; sau đó dừng.
     */
    public record Overdue(int repeatEveryDays, int maxDays) {
    }

    public static ReminderPolicy defaults() {
        Map<ReminderType, List<Integer>> lead = new EnumMap<>(ReminderType.class);
        lead.put(ReminderType.CONTRACT_EXPIRY, List.of(45, 30, 15, 7, 0));
        lead.put(ReminderType.PROBATION_END, List.of(7, 3, 0));
        lead.put(ReminderType.SALARY_REVIEW, List.of(30, 7));
        lead.put(ReminderType.BIRTHDAY, List.of(3, 0));

        Map<ReminderType, Set<Recipient>> to = new EnumMap<>(ReminderType.class);
        to.put(ReminderType.CONTRACT_EXPIRY, Set.of(Recipient.HR, Recipient.MANAGER));
        to.put(ReminderType.CONTRACT_OVERDUE, Set.of(Recipient.HR, Recipient.MANAGER, Recipient.DIRECTOR));
        to.put(ReminderType.PROBATION_END, Set.of(Recipient.HR, Recipient.MANAGER));
        to.put(ReminderType.PROBATION_RESULT_MISSING, Set.of(Recipient.HR, Recipient.MANAGER, Recipient.DIRECTOR));
        to.put(ReminderType.SALARY_REVIEW, Set.of(Recipient.HR, Recipient.MANAGER));
        to.put(ReminderType.SALARY_REVIEW_OVERDUE, Set.of(Recipient.HR, Recipient.DIRECTOR));
        to.put(ReminderType.BIRTHDAY, Set.of(Recipient.HR, Recipient.MANAGER));

        Map<ReminderType, Overdue> od = new EnumMap<>(ReminderType.class);
        // Điều 20.2 BLLĐ 2019: 30 ngày kể từ khi hết hạn phải ký HĐ mới
        od.put(ReminderType.CONTRACT_OVERDUE, new Overdue(3, 30));
        // Điều 27: kết thúc thử việc phải thông báo kết quả → nhắc hằng ngày
        od.put(ReminderType.PROBATION_RESULT_MISSING, new Overdue(1, 14));
        od.put(ReminderType.SALARY_REVIEW_OVERDUE, new Overdue(7, 28));
        return new ReminderPolicy(lead, to, od);
    }
}

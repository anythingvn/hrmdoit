package vn.doit.hr.reminder;

public enum ReminderType {
    CONTRACT_EXPIRY("Hết hạn hợp đồng"),
    CONTRACT_OVERDUE("Hợp đồng đã hết hạn, chưa ký mới"),
    PROBATION_END("Hết thời gian thử việc"),
    PROBATION_RESULT_MISSING("Chưa có kết quả thử việc"),
    SALARY_REVIEW("Đến hạn xét tăng lương"),
    SALARY_REVIEW_OVERDUE("Quá hạn xét tăng lương"),
    BIRTHDAY("Sinh nhật");

    private final String label;

    ReminderType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

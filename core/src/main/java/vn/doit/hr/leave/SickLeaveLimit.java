package vn.doit.hr.leave;

public final class SickLeaveLimit {

    private SickLeaveLimit() {}

    /** Hạn mức nghỉ ốm BHXH mỗi năm theo số năm đã đóng: &lt;15 → 30; 15–&lt;30 → 40; ≥30 → 60. */
    public static int daysPerYear(int yearsContributed) {
        if (yearsContributed >= 30) {
            return 60;
        }
        return yearsContributed >= 15 ? 40 : 30;
    }
}

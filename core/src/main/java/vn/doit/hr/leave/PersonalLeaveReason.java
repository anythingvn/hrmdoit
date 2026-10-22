package vn.doit.hr.leave;

/** Nghỉ việc riêng có lương. */
public enum PersonalLeaveReason {
    KET_HON(3, "Bản thân kết hôn"),
    CON_KET_HON(1, "Con kết hôn"),
    TANG(3, "Cha, mẹ, vợ/chồng, con mất");

    private final int days;
    private final String label;

    PersonalLeaveReason(int days, String label) {
        this.days = days;
        this.label = label;
    }

    public int days() {
        return days;
    }

    public String label() {
        return label;
    }
}

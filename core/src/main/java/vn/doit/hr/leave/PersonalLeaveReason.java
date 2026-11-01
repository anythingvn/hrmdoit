package vn.doit.hr.leave;

/** Nghỉ việc riêng có lương: các lý do theo Điều 115 không trừ phép; "việc riêng khác" trừ phép năm, không có mức theo lý do. */
public enum PersonalLeaveReason {
    KET_HON(3, "Bản thân kết hôn"),
    CON_KET_HON(1, "Con kết hôn"),
    TANG(3, "Cha, mẹ, vợ/chồng, con mất"),
    KHAC(0, "Việc riêng khác");

    private final int days;
    private final String label;

    PersonalLeaveReason(int days, String label) {
        this.days = days;
        this.label = label;
    }

    public int days() {
        return days;
    }

    /** Trừ phép năm khi duyệt (không thuộc Điều 115). */
    public boolean deductsAnnualLeave() {
        return this == KHAC;
    }

    public String label() {
        return label;
    }
}

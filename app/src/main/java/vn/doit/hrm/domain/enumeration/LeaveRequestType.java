package vn.doit.hrm.domain.enumeration;

import vn.doit.hr.leave.Pool;

/** Loại đơn nghỉ và quỹ bị trừ khi duyệt (null = không trừ quỹ). */
public enum LeaveRequestType {
    PHEP_NAM("Nghỉ phép năm", Pool.PHEP_NAM),
    VIEC_RIENG("Nghỉ việc riêng có lương", null),
    OM("Nghỉ ốm", null),
    KHONG_LUONG("Nghỉ không lương", null),
    NGHI_BU("Nghỉ bù", Pool.NGHI_BU);

    private final String label;
    private final Pool pool;

    LeaveRequestType(String label, Pool pool) {
        this.label = label;
        this.pool = pool;
    }

    public String label() {
        return label;
    }

    /** Quỹ bị trừ khi duyệt, hoặc null. */
    public Pool pool() {
        return pool;
    }
}

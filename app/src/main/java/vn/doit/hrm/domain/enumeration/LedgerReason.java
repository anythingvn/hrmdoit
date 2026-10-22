package vn.doit.hrm.domain.enumeration;

/** Lý do của một dòng sổ phép. */
public enum LedgerReason {
    /** Cộng phép tháng. */
    CONG_THANG,
    /** Số dư đầu kỳ. */
    DAU_KY,
    /** Trừ khi duyệt đơn. */
    DUYET_DON,
    /** Hoàn lại khi hủy đơn. */
    HUY_DON,
    /** Hết hạn phép chuyển sang. */
    HET_HAN,
    /** Chuyển phép sang năm sau. */
    CHUYEN_NAM,
    /** HR điều chỉnh. */
    DIEU_CHINH,
    /** Chốt khi nghỉ việc. */
    NGHI_VIEC,
    /** Hoàn tác nghỉ việc. */
    HOAN_TAC,
    /** Cộng giờ nghỉ bù. */
    LAM_THEM,
}

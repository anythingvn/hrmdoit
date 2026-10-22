package vn.doit.hrm.domain.enumeration;

/** Cấp người duyệt đơn: trưởng nhóm, trưởng phòng (khi không có trưởng nhóm), giám đốc (bất kỳ người BGĐ), Quản trị. */
public enum ApproverKind {
    TRUONG_NHOM,
    TRUONG_PHONG,
    BGD,
    QUAN_TRI,
}

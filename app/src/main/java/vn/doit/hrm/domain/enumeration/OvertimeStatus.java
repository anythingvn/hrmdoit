package vn.doit.hrm.domain.enumeration;

/**
 * Trạng thái đơn làm thêm giờ: chờ duyệt → đã duyệt (chờ xác nhận giờ thực tế) → hoàn tất; giờ thực tế vượt giờ đăng ký thì chờ
 * người duyệt xác nhận phần chênh. Từ chối, người gửi rút khi còn chờ, đã hủy.
 */
public enum OvertimeStatus {
    CHO_DUYET,
    DA_DUYET,
    TU_CHOI,
    DA_RUT,
    CHO_XAC_NHAN_THEM,
    HOAN_TAT,
    DA_HUY,
}

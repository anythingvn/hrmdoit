package vn.doit.hrm.domain.enumeration;

/**
 * Trạng thái lần chấm: gõ tay luôn {@code CHAP_NHAN}; tự chấm theo bảng xác minh ({@code CHAP_NHAN}, {@code CHO_DUYET},
 * {@code TU_CHOI}); người duyệt đổi {@code CHO_DUYET} thành {@code DA_DUYET} hoặc {@code TU_CHOI}.
 */
public enum AttendancePunchStatus {
    CHAP_NHAN,
    CHO_DUYET,
    TU_CHOI,
    DA_DUYET,
}

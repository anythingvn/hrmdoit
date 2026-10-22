package vn.doit.hrm.service.leave;

/** Tài khoản đang đăng nhập không gắn hồ sơ nhân viên nên không có sổ phép → 404 error.leavenoprofile. */
public class LeaveNoProfileException extends RuntimeException {

    public LeaveNoProfileException() {
        super("Tài khoản chưa gắn hồ sơ nhân viên nên không có sổ phép");
    }
}

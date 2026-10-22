package vn.doit.hrm.service.leave;

import org.springframework.security.access.AccessDeniedException;

/** Người không thuộc loại NHAN_VIEN (thực tập, dịch vụ): không có sổ phép → 403 error.leavenotapplicable. */
public class LeaveNotApplicableException extends AccessDeniedException {

    public LeaveNotApplicableException() {
        super("Phần nghỉ phép chỉ dành cho nhân viên chính thức và thử việc");
    }
}

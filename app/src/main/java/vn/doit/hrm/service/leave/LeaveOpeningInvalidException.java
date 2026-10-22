package vn.doit.hrm.service.leave;

import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.dto.LeaveOpeningCheckDTO;

/** File số dư đầu kỳ còn lỗi: không lưu gì; kèm kết quả kiểm tra để hiện lại. */
public class LeaveOpeningInvalidException extends EmployeeRuleException {

    private final transient LeaveOpeningCheckDTO check;

    public LeaveOpeningInvalidException(LeaveOpeningCheckDTO check) {
        super("File số dư đầu kỳ còn lỗi; xem lại kết quả kiểm tra", "leaveopeninginvalid");
        this.check = check;
    }

    public LeaveOpeningCheckDTO getCheck() {
        return check;
    }
}

package vn.doit.hrm.service.payroll.calc;

import vn.doit.hrm.service.EmployeeRuleException;

/**
 * Xác nhận bị từ chối vì tính lại với thông số, mức luật, bảng công hiện hành ra số khác số đang lưu. Số mới đã được lưu trong cùng
 * giao dịch, nên các phương thức ném lỗi này khai báo {@code noRollbackFor} để không mất số mới.
 */
public class CalcStaleException extends EmployeeRuleException {

    public static final String MESSAGE = "Thông số, mức luật hoặc bảng công đã thay đổi, hãy xem lại bản tính.";

    public CalcStaleException() {
        super(MESSAGE, "calcstale");
    }
}

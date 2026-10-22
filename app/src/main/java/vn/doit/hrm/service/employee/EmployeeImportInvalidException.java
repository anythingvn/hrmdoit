package vn.doit.hrm.service.employee;

import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.employee.dto.EmployeeImportCheckDTO;

/** File nhập còn lỗi hoặc có cảnh báo chưa xác nhận; kèm kết quả kiểm tra để hiện lại. */
public class EmployeeImportInvalidException extends EmployeeRuleException {

    private final transient EmployeeImportCheckDTO check;

    public EmployeeImportInvalidException(EmployeeImportCheckDTO check) {
        super("File còn lỗi hoặc cảnh báo chưa xác nhận; xem lại kết quả kiểm tra", "importinvalid");
        this.check = check;
    }

    public EmployeeImportCheckDTO getCheck() {
        return check;
    }
}

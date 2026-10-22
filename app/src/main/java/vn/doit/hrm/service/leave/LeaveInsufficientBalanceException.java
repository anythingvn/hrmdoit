package vn.doit.hrm.service.leave;

import java.math.BigDecimal;
import vn.doit.hrm.service.EmployeeRuleException;

/** Số dư không đủ cho đơn → 400 {@code error.insufficientbalance}; {@code remaining} là số còn lại (ngày hoặc giờ). */
public class LeaveInsufficientBalanceException extends EmployeeRuleException {

    private final BigDecimal remaining;

    public LeaveInsufficientBalanceException(String message, BigDecimal remaining) {
        super(message, "insufficientbalance");
        this.remaining = remaining;
    }

    public BigDecimal getRemaining() {
        return remaining;
    }
}

package vn.doit.hrm.service.leave.dto;

import java.math.BigDecimal;

/** Người gửi xác nhận số giờ thực tế sau khi làm. */
public record OvertimeConfirmDTO(BigDecimal actualHours) {}

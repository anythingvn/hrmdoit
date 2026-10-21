package vn.doit.hrm.service.payslip.dto;

import java.time.Instant;

/** Một kỳ lương đã gửi phiếu cho chính mình (phiên bản đã gửi gần nhất). */
public record OwnPayslipDTO(String period, int version, Instant sentAt) {}

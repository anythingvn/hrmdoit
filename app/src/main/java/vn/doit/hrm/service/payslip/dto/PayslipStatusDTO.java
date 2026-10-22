package vn.doit.hrm.service.payslip.dto;

import java.time.Instant;
import vn.doit.hrm.domain.enumeration.PayslipDeliveryStatus;

/**
 * Tình trạng gửi phiếu lương của một người trong kỳ (lượt gửi gần nhất).
 *
 * @param status {@code null} = chưa gửi lần nào
 * @param version phiên bản bảng lương của lượt gửi gần nhất
 * @param staleVersion đã gửi phiếu theo phiên bản cũ (kỳ đã duyệt lại phiên bản mới)
 */
public record PayslipStatusDTO(
    Long employeeId,
    String employeeCode,
    String fullName,
    String email,
    PayslipDeliveryStatus status,
    Integer version,
    Instant sentAt,
    String error,
    boolean staleVersion,
    /** Hồ sơ đã lưu trữ: màn phiếu lương hiện nhãn "Lưu trữ". */
    boolean archived
) {}

package vn.doit.hrm.service.dto;

import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/**
 * Yêu cầu cử nhân sự. Chọn {@code siteId} thì đơn vị/địa chỉ lấy từ nơi làm việc đó; {@code signDate} trống = hôm nay;
 * người ký trống = theo cấu hình công ty.
 */
public record AssignmentDecisionRequestDTO(
    WorkLocationType type,
    Long siteId,
    String unit,
    String address,
    LocalDate fromDate,
    LocalDate toDate,
    String task,
    String benefits,
    LocalDate signDate,
    String signerName,
    String signerTitle
) {}

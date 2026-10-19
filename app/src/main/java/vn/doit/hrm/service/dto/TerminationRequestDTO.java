package vn.doit.hrm.service.dto;

import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.TerminationType;

/**
 * Yêu cầu cho nghỉ việc.
 *
 * @param terminationDate ngày nghỉ việc (tùy chọn); bỏ trống = ngày sau ngày làm việc cuối; không được trước ngày làm việc cuối
 */
public record TerminationRequestDTO(
    LocalDate lastWorkingDay,
    TerminationType terminationType,
    String terminationReason,
    LocalDate terminationDate
) {}

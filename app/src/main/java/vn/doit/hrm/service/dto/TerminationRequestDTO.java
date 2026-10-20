package vn.doit.hrm.service.dto;

import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.TerminationType;

/**
 * Yêu cầu cho nghỉ việc.
 *
 * @param terminationDate ngày nghỉ việc (tùy chọn); bỏ trống = ngày sau ngày làm việc cuối; không được trước ngày làm việc cuối
 * @param deletedReturnPeriods mô tả giai đoạn "quay về" vừa xóa để cho nghỉ (tùy chọn, do màn hình gửi); hoàn tác sẽ báo lại là
 *     không khôi phục giai đoạn này
 */
public record TerminationRequestDTO(
    LocalDate lastWorkingDay,
    TerminationType terminationType,
    String terminationReason,
    LocalDate terminationDate,
    @jakarta.validation.constraints.Size(max = 500) String deletedReturnPeriods
) {}

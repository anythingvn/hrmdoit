package vn.doit.hrm.service.dto;

import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/**
 * Giai đoạn làm việc bắt đầu sau ngày làm việc cuối, chặn việc cho nghỉ.
 *
 * @param started    giai đoạn đã bắt đầu (ngày bắt đầu ≤ hôm nay)
 * @param autoReturn giai đoạn "quay về nơi cũ" hệ thống tự tạo, chưa bắt đầu
 */
public record TerminationBlockerDTO(
    Long id,
    WorkLocationType type,
    String unit,
    LocalDate fromDate,
    LocalDate toDate,
    boolean started,
    boolean autoReturn
) {}

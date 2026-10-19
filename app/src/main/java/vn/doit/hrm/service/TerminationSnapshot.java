package vn.doit.hrm.service;

import java.time.LocalDate;
import java.util.List;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.TerminationType;

/**
 * Những gì một lần cho nghỉ việc đã đổi (giá trị trước và sau), lưu dạng JSON trong {@code termination_record.snapshot_json}.
 * Hoàn tác chỉ trả lại các giá trị này, và chỉ khi giá trị hiện tại vẫn đúng bằng giá trị "sau" (chưa ai sửa tiếp).
 * {@code contractIdsAtTermination}: mọi HĐ có lúc cho nghỉ – HĐ thêm sau đó mà trùng thời gian với HĐ được mở lại thì không hoàn tác.
 */
public record TerminationSnapshot(
    EmployeeState before,
    EmployeeState after,
    List<AssignmentEnd> assignments,
    List<ContractEnd> contracts,
    Long userId,
    Boolean userActivatedBefore,
    List<Long> contractIdsAtTermination
) {
    /** Trạng thái và các trường nghỉ việc của hồ sơ. */
    public record EmployeeState(
        EmploymentStatus status,
        LocalDate lastWorkingDay,
        LocalDate terminationDate,
        TerminationType terminationType,
        String terminationReason
    ) {}

    /** Một giai đoạn làm việc bị chốt (hoặc do lõi tạo thêm khi chốt: {@code created}). */
    public record AssignmentEnd(Long id, LocalDate oldFrom, LocalDate oldTo, LocalDate newFrom, LocalDate newTo, boolean created) {}

    /** Một hợp đồng bị kết thúc. */
    public record ContractEnd(
        Long id,
        String contractNo,
        ContractStatus oldStatus,
        LocalDate oldEndDate,
        ContractStatus newStatus,
        LocalDate newEndDate
    ) {}
}

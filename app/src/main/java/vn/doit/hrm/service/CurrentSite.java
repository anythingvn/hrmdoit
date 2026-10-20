package vn.doit.hrm.service;

import java.time.LocalDate;
import java.util.List;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.AssignmentHistory;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/** Tên nơi làm việc hiện tại của nhân viên, dùng cho danh sách và chi tiết. */
final class CurrentSite {

    private CurrentSite() {}

    /**
     * Nơi làm việc thực tế trong ngày (theo lõi {@code locationOn}): tên site nếu có, nếu không thì đơn vị,
     * nếu không thì nhãn loại. Null nếu không có hoặc nhân viên đã nghỉ.
     */
    static String nameOn(Employee e, List<WorkAssignment> rows, LocalDate today) {
        if (e.getStatus() == EmploymentStatus.DA_NGHI || rows.isEmpty()) return null;
        return AssignmentHistory
            .of(e.getCode(), rows.stream().map(a -> AssignmentMapping.toCore(a, e.getCode())).toList())
            .locationOn(today)
            .map(found -> rows.stream().filter(r -> r.getId() != null && r.getId() == found.id()).findFirst().orElse(null))
            .map(CurrentSite::label)
            .orElse(null);
    }

    private static String label(WorkAssignment a) {
        if (a.getSite() != null && a.getSite().getName() != null && !a.getSite().getName().isBlank()) return a.getSite().getName();
        if (a.getUnit() != null && !a.getUnit().isBlank()) return a.getUnit();
        return typeLabel(a.getType());
    }

    private static String typeLabel(WorkLocationType t) {
        if (t == null) return null;
        return switch (t) {
            case VAN_PHONG -> "Văn phòng";
            case KHACH_HANG -> "Khách hàng";
            case TU_XA -> "Từ xa";
            case CONG_TAC -> "Công tác";
        };
    }
}

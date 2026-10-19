package vn.doit.hrm.service;

import vn.doit.hr.assignment.Assignment;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/** Chuyển đổi giữa thực thể {@link WorkAssignment} và bản ghi lõi {@link Assignment} (id 0 = bản ghi mới chưa lưu). */
public final class AssignmentMapping {

    private AssignmentMapping() {}

    public static Assignment toCore(WorkAssignment e, String employeeCode) {
        return new Assignment(
            e.getId() == null ? 0L : e.getId(),
            employeeCode,
            toCore(e.getType()),
            e.getUnit(),
            e.getAddress(),
            e.getFromDate(),
            e.getToDate(),
            e.getDecisionNo(),
            e.getNote()
        );
    }

    /** Chép các trường nghiệp vụ của bản ghi lõi sang thực thể (không đụng id, nhân viên, nơi làm việc). */
    public static void copyInto(Assignment a, WorkAssignment target) {
        target.setType(toApp(a.type()));
        target.setUnit(a.unit());
        target.setAddress(a.address());
        target.setFromDate(a.from());
        target.setToDate(a.to());
        target.setDecisionNo(a.decisionNo());
        target.setNote(a.note());
    }

    public static vn.doit.hr.assignment.WorkLocationType toCore(WorkLocationType type) {
        return type == null ? null : vn.doit.hr.assignment.WorkLocationType.valueOf(type.name());
    }

    public static WorkLocationType toApp(vn.doit.hr.assignment.WorkLocationType type) {
        return type == null ? null : WorkLocationType.valueOf(type.name());
    }
}

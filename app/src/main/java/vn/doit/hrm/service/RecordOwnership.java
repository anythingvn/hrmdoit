package vn.doit.hrm.service;

import java.util.Objects;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.service.dto.EmployeeDTO;

/** Bản ghi lịch sử (HĐ, lương, phụ cấp) luôn thuộc đúng nhân viên đã tạo nó: sửa không được chuyển sang người khác. */
final class RecordOwnership {

    static final String CANNOT_MOVE = "cannotmoverecord";

    private RecordOwnership() {}

    /**
     * @param current nhân viên đang sở hữu bản ghi.
     * @param incoming nhân viên trong dữ liệu gửi lên.
     * @param partial {@code true} cho PATCH: không gửi {@code employee} nghĩa là giữ nguyên.
     */
    static void requireSameEmployee(Employee current, EmployeeDTO incoming, boolean partial) {
        if (partial && incoming == null) {
            return;
        }
        Long currentId = current == null ? null : current.getId();
        Long incomingId = incoming == null ? null : incoming.getId();
        if (!Objects.equals(currentId, incomingId)) {
            throw new EmployeeRuleException("Không được chuyển bản ghi sang nhân viên khác", CANNOT_MOVE);
        }
    }
}

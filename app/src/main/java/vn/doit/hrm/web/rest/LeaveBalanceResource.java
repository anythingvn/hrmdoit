package vn.doit.hrm.web.rest;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.leave.EmployeeLeaveService;
import vn.doit.hrm.service.leave.LeaveBalanceService;
import vn.doit.hrm.service.leave.dto.EmployeeLeaveDTO;
import vn.doit.hrm.service.leave.dto.LeaveAdjustmentDTO;
import vn.doit.hrm.service.leave.dto.LeaveBalanceDTO;
import vn.doit.hrm.service.leave.dto.LeaveLedgerEntryDTO;

/**
 * Số dư và sổ phép. Mọi tài khoản đăng nhập gọi được (kể cả ROLE_NHAN_VIEN); quyền xem sổ của người khác kiểm tra theo dữ liệu ở
 * service. Người không có sổ (thực tập, dịch vụ, tài khoản không gắn hồ sơ) → 403 error.leavenotapplicable.
 */
@RestController
@RequestMapping("/api/leave")
public class LeaveBalanceResource {

    private final LeaveBalanceService service;
    private final EmployeeLeaveService employeeLeave;

    public LeaveBalanceResource(LeaveBalanceService service, EmployeeLeaveService employeeLeave) {
        this.service = service;
        this.employeeLeave = employeeLeave;
    }

    /** Tab "Nghỉ phép" trên hồ sơ: HR, Quản trị, trưởng nhóm hoặc trưởng phòng của người đó (kiểm tra theo dữ liệu ở service). */
    @GetMapping("/employees/{employeeId}")
    public EmployeeLeaveDTO employee(@PathVariable("employeeId") Long employeeId) {
        return employeeLeave.overview(employeeId);
    }

    /** Phép năm (hai ngăn, số được xin), nghỉ bù (giờ), ngày ốm đã nghỉ / mức, giờ làm thêm tháng này / giới hạn. */
    @GetMapping("/balances/me")
    public LeaveBalanceDTO mine() {
        return service.mine();
    }

    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @GetMapping("/balances")
    public List<LeaveBalanceDTO> company(@RequestParam(name = "departmentId", required = false) Long departmentId) {
        return service.company(departmentId);
    }

    /** Chính mình, HR, Quản trị, trưởng nhóm hoặc trưởng phòng của người đó. */
    @GetMapping("/ledger/{employeeId}")
    public List<LeaveLedgerEntryDTO> ledger(@PathVariable("employeeId") Long employeeId) {
        return service.ledgerOf(employeeId);
    }

    /** HR / Quản trị cộng hoặc trừ tay; ghi chú bắt buộc. */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("/adjustments")
    public ResponseEntity<List<LeaveLedgerEntryDTO>> adjust(@Valid @RequestBody LeaveAdjustmentDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adjust(dto));
    }
}

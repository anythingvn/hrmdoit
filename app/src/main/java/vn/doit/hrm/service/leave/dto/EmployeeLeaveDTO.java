package vn.doit.hrm.service.leave.dto;

import java.util.List;

/** Tab "Nghỉ phép" trên hồ sơ nhân viên: số dư, đơn nghỉ, sổ phép của người đó. */
public record EmployeeLeaveDTO(LeaveBalanceDTO balance, List<LeaveRequestDTO> requests, List<LeaveLedgerEntryDTO> ledger) {}

package vn.doit.hrm.service;

import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.dto.TerminationRequestDTO;

/**
 * Cho nghỉ việc – cách duy nhất để chuyển trạng thái sang {@code DA_NGHI}. Trong một transaction:
 * đặt trạng thái và các trường nghỉ việc, chốt vị trí làm việc đang mở, kết thúc hợp đồng đang hiệu lực.
 */
@Service
@Transactional
public class TerminationService {

    private static final Logger LOG = LoggerFactory.getLogger(TerminationService.class);

    private final EmployeeRepository employeeRepository;

    private final EmploymentContractRepository contractRepository;

    private final WorkAssignmentService workAssignmentService;

    public TerminationService(
        EmployeeRepository employeeRepository,
        EmploymentContractRepository contractRepository,
        WorkAssignmentService workAssignmentService
    ) {
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.workAssignmentService = workAssignmentService;
    }

    public void terminate(Long employeeId, TerminationRequestDTO request) {
        LOG.debug("Request to terminate Employee {} : {}", employeeId, request);
        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound"));
        if (employee.getStatus() == EmploymentStatus.DA_NGHI) {
            throw new EmployeeRuleException("Nhân viên đã nghỉ việc", "alreadyterminated");
        }
        if (request == null || request.lastWorkingDay() == null) {
            throw new EmployeeRuleException("Cần ngày làm việc cuối cùng", "lastworkingdayrequired");
        }
        if (request.terminationType() == null) {
            throw new EmployeeRuleException("Cần chọn hình thức nghỉ việc", "terminationtyperequired");
        }
        LocalDate lastDay = request.lastWorkingDay();

        // Kiểm tra hết trước khi ghi bất cứ gì.
        List<EmploymentContract> contracts = contractRepository.findByEmployeeId(employeeId);
        for (EmploymentContract c : contracts) {
            if (c.getStatus() == ContractStatus.DA_KY && c.getStartDate() != null && c.getStartDate().isAfter(lastDay)) {
                throw new EmployeeRuleException(
                    "Ngày làm việc cuối trước ngày bắt đầu hợp đồng " + (c.getContractNo() == null ? "" : c.getContractNo() + " ") + "(" + c.getStartDate() + ")",
                    "terminatebeforecontract"
                );
            }
        }
        workAssignmentService.checkCanCloseAt(employeeId, lastDay);

        employee.setStatus(EmploymentStatus.DA_NGHI);
        employee.setLastWorkingDay(lastDay);
        employee.setTerminationType(request.terminationType());
        employee.setTerminationReason(request.terminationReason());
        employeeRepository.save(employee);

        workAssignmentService.closeAt(employeeId, lastDay);

        for (EmploymentContract c : contracts) {
            if (c.getStatus() == ContractStatus.DA_KY && (c.getEndDate() == null || c.getEndDate().isAfter(lastDay))) {
                c.setStatus(ContractStatus.DA_KET_THUC);
                c.setEndDate(lastDay);
                contractRepository.save(c);
            }
        }
        employeeRepository.flush();
    }
}

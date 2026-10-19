package vn.doit.hrm.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.SensitiveAccessLog;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.repository.SensitiveAccessLogRepository;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.EmployeeSensitiveDTO;
import vn.doit.hrm.service.mapper.EmployeeMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.Employee}.
 */
@Service
@Transactional
public class EmployeeService {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;

    private final EmployeeMapper employeeMapper;

    private final SensitiveAccessLogRepository sensitiveAccessLogRepository;

    private final Clock clock;

    private final EmploymentContractRepository contractRepository;

    private final SalaryChangeRepository salaryChangeRepository;

    private final vn.doit.hrm.repository.WorkAssignmentRepository workAssignmentRepository;

    public EmployeeService(
        EmployeeRepository employeeRepository,
        EmployeeMapper employeeMapper,
        SensitiveAccessLogRepository sensitiveAccessLogRepository,
        Clock clock,
        EmploymentContractRepository contractRepository,
        SalaryChangeRepository salaryChangeRepository,
        vn.doit.hrm.repository.WorkAssignmentRepository workAssignmentRepository
    ) {
        this.workAssignmentRepository = workAssignmentRepository;
        this.contractRepository = contractRepository;
        this.salaryChangeRepository = salaryChangeRepository;
        this.employeeRepository = employeeRepository;
        this.employeeMapper = employeeMapper;
        this.sensitiveAccessLogRepository = sensitiveAccessLogRepository;
        this.clock = clock;
    }

    /**
     * Save a employee.
     *
     * @param employeeDTO the entity to save.
     * @return the persisted entity.
     */
    public EmployeeDTO save(EmployeeDTO employeeDTO) {
        LOG.debug("Request to save Employee : {}", employeeDTO);
        Employee employee = employeeMapper.toEntity(employeeDTO);
        applyRules(employee, null);
        employee = employeeRepository.save(employee);
        return employeeMapper.toDto(employee);
    }

    /**
     * Update a employee.
     *
     * @param employeeDTO the entity to save.
     * @return the persisted entity.
     */
    public EmployeeDTO update(EmployeeDTO employeeDTO) {
        LOG.debug("Request to update Employee : {}", employeeDTO);
        Employee existing = employeeRepository.findById(employeeDTO.getId()).orElseThrow();
        PersonType existingType = existing.getPersonType();
        String existingCode = existing.getCode();
        checkStatusNotChanged(employeeDTO.getStatus(), existing.getStatus());
        Employee employee = employeeMapper.toEntity(employeeDTO);
        employee.setStatus(existing.getStatus());
        keepNonEditableFields(employee, existing);
        keepSensitiveWhenOmitted(employee, existing);
        applyRules(employee, new Previous(existingType, existingCode));
        employee = employeeRepository.save(employee);
        return employeeMapper.toDto(employee);
    }

    /**
     * Partially update a employee.
     *
     * @param employeeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<EmployeeDTO> partialUpdate(EmployeeDTO employeeDTO) {
        LOG.debug("Request to partially update Employee : {}", employeeDTO);

        return employeeRepository
            .findById(employeeDTO.getId())
            .map(existingEmployee -> {
                checkStatusNotChanged(employeeDTO.getStatus(), existingEmployee.getStatus());
                Previous previous = new Previous(existingEmployee.getPersonType(), existingEmployee.getCode());
                if (previous.personType() != PersonType.DICH_VU && employeeDTO.getCode() != null) {
                    // Kiểm tra trùng trước khi sửa thực thể, tránh Hibernate flush gây lỗi ràng buộc duy nhất.
                    String code = EmployeeCodes.normalize(employeeDTO.getCode());
                    if (code == null) {
                        throw new EmployeeRuleException("Nhân viên phải có mã", "codenull");
                    }
                    if (employeeRepository.existsByCodeAndIdNot(code, existingEmployee.getId())) {
                        throw new EmployeeRuleException("Mã nhân viên đã tồn tại", "codeexists");
                    }
                    employeeDTO.setCode(code);
                }
                var lastWorkingDay = existingEmployee.getLastWorkingDay();
                var terminationType = existingEmployee.getTerminationType();
                var terminationReason = existingEmployee.getTerminationReason();
                var terminationDate = existingEmployee.getTerminationDate();
                var user = existingEmployee.getUser();
                employeeMapper.partialUpdate(existingEmployee, employeeDTO);
                existingEmployee.setLastWorkingDay(lastWorkingDay);
                existingEmployee.setTerminationType(terminationType);
                existingEmployee.setTerminationReason(terminationReason);
                existingEmployee.setTerminationDate(terminationDate);
                existingEmployee.setUser(user);
                applyRules(existingEmployee, previous);
                return existingEmployee;
            })
            .map(employeeRepository::save)
            .map(employeeMapper::toDto);
    }

    /**
     * Sửa hồ sơ không được đổi trạng thái làm việc (kể cả cho làm lại người đã nghỉ); bỏ trống = giữ nguyên.
     * Chỉ chức năng cho nghỉ việc ({@link TerminationService}) đổi trạng thái.
     */
    private static void checkStatusNotChanged(EmploymentStatus requested, EmploymentStatus stored) {
        if (requested != null && requested != stored) {
            throw new EmployeeRuleException("Không đổi trạng thái khi sửa hồ sơ; hãy dùng chức năng Cho nghỉ việc", "useterminate");
        }
    }

    /** Thông tin nghỉ việc và liên kết tài khoản không sửa được qua hồ sơ: chỉ đổi qua /terminate. */
    private static void keepNonEditableFields(Employee employee, Employee existing) {
        employee.setLastWorkingDay(existing.getLastWorkingDay());
        employee.setTerminationType(existing.getTerminationType());
        employee.setTerminationReason(existing.getTerminationReason());
        employee.setTerminationDate(existing.getTerminationDate());
        employee.setUser(existing.getUser());
    }

    /**
     * Các trường nhạy cảm không có trong dữ liệu trả về nên client PUT thường không gửi lại;
     * trường nào bị bỏ trống thì giữ giá trị đã lưu thay vì xóa.
     */
    private static void keepSensitiveWhenOmitted(Employee employee, Employee existing) {
        if (employee.getIdNumber() == null) employee.setIdNumber(existing.getIdNumber());
        if (employee.getIdIssueDate() == null) employee.setIdIssueDate(existing.getIdIssueDate());
        if (employee.getIdIssuePlace() == null) employee.setIdIssuePlace(existing.getIdIssuePlace());
        if (employee.getTaxCode() == null) employee.setTaxCode(existing.getTaxCode());
        if (employee.getBankAccount() == null) employee.setBankAccount(existing.getBankAccount());
        if (employee.getBankName() == null) employee.setBankName(existing.getBankName());
        if (employee.getSocialInsuranceNo() == null) employee.setSocialInsuranceNo(existing.getSocialInsuranceNo());
    }

    /**
     * Đọc dữ liệu nhạy cảm và ghi nhật ký người xem.
     *
     * @param fullAccess true: trả đủ trường; false: chỉ ngân hàng và BHXH (các trường còn lại null).
     * @param viewerLogin tài khoản đang xem.
     */
    public Optional<EmployeeSensitiveDTO> findSensitive(Long id, boolean fullAccess, String viewerLogin) {
        return employeeRepository
            .findById(id)
            .map(employee -> {
                sensitiveAccessLogRepository.save(new SensitiveAccessLog(id, viewerLogin, Instant.now(clock)));
                EmployeeSensitiveDTO dto = new EmployeeSensitiveDTO();
                if (fullAccess) {
                    dto.setIdNumber(employee.getIdNumber());
                    dto.setIdIssueDate(employee.getIdIssueDate());
                    dto.setIdIssuePlace(employee.getIdIssuePlace());
                    dto.setTaxCode(employee.getTaxCode());
                }
                dto.setBankAccount(employee.getBankAccount());
                dto.setBankName(employee.getBankName());
                dto.setSocialInsuranceNo(employee.getSocialInsuranceNo());
                return dto;
            });
    }

    private record Previous(PersonType personType, String code) {}

    /**
     * Quy tắc chung khi lưu: mặc định trạng thái (chỉ khi tạo mới; khi sửa trạng thái đã được giữ nguyên)/phụ thuộc, chuẩn hóa mã, mã HDDV tự sinh, tính lại searchText.
     *
     * @param previous trạng thái đã lưu (null khi tạo mới)
     */
    private void applyRules(Employee employee, Previous previous) {
        if (employee.getStatus() == null) {
            employee.setStatus(EmploymentStatus.DANG_LAM_VIEC);
        }
        if (employee.getDependants() == null) {
            employee.setDependants(0);
        }
        if (previous != null && previous.personType() == PersonType.DICH_VU) {
            // Người dịch vụ giữ nguyên loại và mã đã cấp.
            employee.setPersonType(PersonType.DICH_VU);
            employee.setCode(previous.code());
        } else if (employee.getPersonType() == PersonType.DICH_VU) {
            employee.setCode(EmployeeCodes.SERVICE_PREFIX + employeeRepository.nextServiceCodeNumber());
        } else {
            String code = EmployeeCodes.normalize(employee.getCode());
            if (code == null) {
                throw new EmployeeRuleException("Nhân viên phải có mã", "codenull");
            }
            boolean taken = employee.getId() == null
                ? employeeRepository.existsByCode(code)
                : employeeRepository.existsByCodeAndIdNot(code, employee.getId());
            if (taken) {
                throw new EmployeeRuleException("Mã nhân viên đã tồn tại", "codeexists");
            }
            employee.setCode(code);
        }
        employee.setSearchText(
            VietnameseText.fold(
                employee.getCode() + " " + employee.getFullName() + " " + (employee.getEmail() == null ? "" : employee.getEmail())
            )
        );
    }

    /**
     * Get all the employees with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<EmployeeDTO> findAllWithEagerRelationships(Pageable pageable) {
        return employeeRepository.findAllWithEagerRelationships(pageable).map(employeeMapper::toDto);
    }

    /**
     * Get one employee by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<EmployeeDTO> findOne(Long id) {
        LOG.debug("Request to get Employee : {}", id);
        return employeeRepository
            .findOneWithEagerRelationships(id)
            .map(e -> {
                EmployeeDTO dto = employeeMapper.toDto(e);
                LocalDate today = LocalDate.now(clock);
                List<EmploymentContract> contracts = contractRepository.findByEmployeeId(id);
                CurrentSalaryCalculator.of(e.getStatus(), contracts, salaryChangeRepository.findByEmployeeId(id), today).ifPresent(
                    dto::setCurrentSalary
                );
                dto.setCurrentSiteName(CurrentSite.nameOn(e, workAssignmentRepository.findByEmployeeId(id), today));
                ContractRules.currentContract(contracts, e.getStatus(), today).ifPresent(c -> dto.setCurrentContractType(c.getType()));
                return dto;
            });
    }

    /** Gợi ý khi tạo hợp đồng mới: true nếu 2 HĐLĐ xác định thời hạn gần nhất đã ký (lần sau phải không xác định thời hạn). */
    @Transactional(readOnly = true)
    public boolean nextContractMustBeIndefinite(Long employeeId) {
        return ContractRules.nextMustBeIndefinite(contractRepository.findByEmployeeId(employeeId));
    }
}

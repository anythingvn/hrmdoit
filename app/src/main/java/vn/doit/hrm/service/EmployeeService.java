package vn.doit.hrm.service;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.service.dto.EmployeeDTO;
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

    public EmployeeService(EmployeeRepository employeeRepository, EmployeeMapper employeeMapper) {
        this.employeeRepository = employeeRepository;
        this.employeeMapper = employeeMapper;
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
        Employee employee = employeeMapper.toEntity(employeeDTO);
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
                employeeMapper.partialUpdate(existingEmployee, employeeDTO);
                applyRules(existingEmployee, previous);
                return existingEmployee;
            })
            .map(employeeRepository::save)
            .map(employeeMapper::toDto);
    }

    private record Previous(PersonType personType, String code) {}

    /**
     * Quy tắc chung khi lưu: mặc định trạng thái/phụ thuộc, chuẩn hóa mã, mã HDDV tự sinh, tính lại searchText.
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
            VietnameseText.fold(employee.getCode() + " " + employee.getFullName() + " " + (employee.getEmail() == null ? "" : employee.getEmail()))
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
        return employeeRepository.findOneWithEagerRelationships(id).map(employeeMapper::toDto);
    }
}

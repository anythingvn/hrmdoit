package vn.doit.hrm.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.service.dto.DepartmentDTO;
import vn.doit.hrm.service.mapper.DepartmentMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.Department}.
 */
@Service
@Transactional
public class DepartmentService {

    private static final Logger LOG = LoggerFactory.getLogger(DepartmentService.class);

    private final DepartmentRepository departmentRepository;

    private final DepartmentMapper departmentMapper;

    private final EmployeeRepository employeeRepository;

    public DepartmentService(
        DepartmentRepository departmentRepository,
        DepartmentMapper departmentMapper,
        EmployeeRepository employeeRepository
    ) {
        this.departmentRepository = departmentRepository;
        this.departmentMapper = departmentMapper;
        this.employeeRepository = employeeRepository;
    }

    /** Trưởng phòng phải là nhân viên (loại NHAN_VIEN) đang làm việc; null = bỏ trưởng phòng. */
    private Employee head(Long headId) {
        if (headId == null) {
            return null;
        }
        Employee head = employeeRepository.findById(headId).orElse(null);
        if (head == null || head.getStatus() != EmploymentStatus.DANG_LAM_VIEC || head.getPersonType() != PersonType.NHAN_VIEN) {
            throw new EmployeeRuleException("Trưởng phòng phải là nhân viên đang làm việc", "departmentheadinactive");
        }
        return head;
    }

    /**
     * Save a department.
     *
     * @param departmentDTO the entity to save.
     * @return the persisted entity.
     */
    public DepartmentDTO save(DepartmentDTO departmentDTO) {
        LOG.debug("Request to save Department : {}", departmentDTO);
        Department department = departmentMapper.toEntity(departmentDTO);
        department.setHead(head(departmentDTO.getHeadId()));
        department = departmentRepository.save(department);
        return departmentMapper.toDto(department);
    }

    /**
     * Update a department.
     *
     * @param departmentDTO the entity to save.
     * @return the persisted entity.
     */
    public DepartmentDTO update(DepartmentDTO departmentDTO) {
        LOG.debug("Request to update Department : {}", departmentDTO);
        Employee currentHead = departmentRepository.findById(departmentDTO.getId()).map(Department::getHead).orElse(null);
        Department department = departmentMapper.toEntity(departmentDTO);
        // Giữ nguyên trưởng phòng hiện tại (kể cả khi người đó vừa nghỉ) thì vẫn sửa được tên / trạng thái phòng.
        boolean unchanged = currentHead != null && currentHead.getId().equals(departmentDTO.getHeadId());
        department.setHead(unchanged ? currentHead : head(departmentDTO.getHeadId()));
        department = departmentRepository.save(department);
        return departmentMapper.toDto(department);
    }

    /**
     * Partially update a department.
     *
     * @param departmentDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DepartmentDTO> partialUpdate(DepartmentDTO departmentDTO) {
        LOG.debug("Request to partially update Department : {}", departmentDTO);

        return departmentRepository
            .findById(departmentDTO.getId())
            .map(existingDepartment -> {
                departmentMapper.partialUpdate(existingDepartment, departmentDTO);
                if (
                    departmentDTO.getHeadId() != null &&
                    (existingDepartment.getHead() == null || !existingDepartment.getHead().getId().equals(departmentDTO.getHeadId()))
                ) {
                    existingDepartment.setHead(head(departmentDTO.getHeadId()));
                }

                return existingDepartment;
            })
            .map(departmentRepository::save)
            .map(departmentMapper::toDto);
    }

    /**
     * Get all the departments.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<DepartmentDTO> findAll() {
        LOG.debug("Request to get all Departments");
        return departmentRepository.findAll().stream().map(departmentMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one department by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DepartmentDTO> findOne(Long id) {
        LOG.debug("Request to get Department : {}", id);
        return departmentRepository.findById(id).map(departmentMapper::toDto);
    }

    /**
     * Delete the department by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Department : {}", id);
        departmentRepository.deleteById(id);
    }
}

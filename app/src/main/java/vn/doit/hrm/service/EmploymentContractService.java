package vn.doit.hrm.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.payroll.NetGrossConverter;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.dto.EmploymentContractDTO;
import vn.doit.hrm.service.mapper.EmploymentContractMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.EmploymentContract}.
 */
@Service
@Transactional
public class EmploymentContractService {

    private static final Logger LOG = LoggerFactory.getLogger(EmploymentContractService.class);

    private final EmploymentContractRepository employmentContractRepository;

    private final EmploymentContractMapper employmentContractMapper;

    private final EmployeeRepository employeeRepository;

    private final ApplicationProperties applicationProperties;

    public EmploymentContractService(
        EmploymentContractRepository employmentContractRepository,
        EmploymentContractMapper employmentContractMapper,
        EmployeeRepository employeeRepository,
        ApplicationProperties applicationProperties
    ) {
        this.employmentContractRepository = employmentContractRepository;
        this.employmentContractMapper = employmentContractMapper;
        this.employeeRepository = employeeRepository;
        this.applicationProperties = applicationProperties;
    }

    /** Kiểm tra thời hạn, điền ngày kết thúc mặc định và gợi ý Gross khi lương là Net. */
    private void applyRules(EmploymentContract c) {
        ContractRules.validate(c.getType(), c.getTermMonths(), c.getStartDate(), c.getEndDate(), applicationProperties.getContract().getTermOptionsMonths());
        if (c.getEndDate() == null) {
            c.setEndDate(ContractRules.defaultEndDate(c.getType(), c.getTermMonths(), c.getStartDate()));
        }
        if (c.getSalaryBasis() == SalaryBasis.NET && c.getGrossAmount() == null && c.getSalary() != null && c.getStartDate() != null) {
            int dependants = 0;
            if (c.getEmployee() != null && c.getEmployee().getId() != null) {
                dependants = employeeRepository
                    .findById(c.getEmployee().getId())
                    .map(e -> e.getDependants() == null ? 0 : e.getDependants())
                    .orElse(0);
            }
            NetGrossConverter.suggestGross(BigDecimal.valueOf(c.getSalary()), dependants, YearMonth.from(c.getStartDate()))
                .ifPresent(g -> c.setGrossAmount(g.longValueExact()));
        }
    }

    /**
     * Save a employmentContract.
     *
     * @param employmentContractDTO the entity to save.
     * @return the persisted entity.
     */
    public EmploymentContractDTO save(EmploymentContractDTO employmentContractDTO) {
        LOG.debug("Request to save EmploymentContract : {}", employmentContractDTO);
        EmploymentContract employmentContract = employmentContractMapper.toEntity(employmentContractDTO);
        applyRules(employmentContract);
        employmentContract = employmentContractRepository.save(employmentContract);
        return employmentContractMapper.toDto(employmentContract);
    }

    /**
     * Update a employmentContract.
     *
     * @param employmentContractDTO the entity to save.
     * @return the persisted entity.
     */
    public EmploymentContractDTO update(EmploymentContractDTO employmentContractDTO) {
        LOG.debug("Request to update EmploymentContract : {}", employmentContractDTO);
        EmploymentContract employmentContract = employmentContractMapper.toEntity(employmentContractDTO);
        applyRules(employmentContract);
        employmentContract = employmentContractRepository.save(employmentContract);
        return employmentContractMapper.toDto(employmentContract);
    }

    /**
     * Partially update a employmentContract.
     *
     * @param employmentContractDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<EmploymentContractDTO> partialUpdate(EmploymentContractDTO employmentContractDTO) {
        LOG.debug("Request to partially update EmploymentContract : {}", employmentContractDTO);

        return employmentContractRepository
            .findById(employmentContractDTO.getId())
            .map(existingEmploymentContract -> {
                employmentContractMapper.partialUpdate(existingEmploymentContract, employmentContractDTO);
                applyRules(existingEmploymentContract);

                return existingEmploymentContract;
            })
            .map(employmentContractRepository::save)
            .map(employmentContractMapper::toDto);
    }

    /**
     * Get all the employmentContracts.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<EmploymentContractDTO> findAll() {
        LOG.debug("Request to get all EmploymentContracts");
        return employmentContractRepository
            .findAll()
            .stream()
            .map(employmentContractMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /** Hợp đồng của một nhân viên, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<EmploymentContractDTO> findByEmployee(Long employeeId) {
        return employmentContractRepository
            .findByEmployeeId(employeeId)
            .stream()
            .sorted(
                java.util.Comparator.comparing(EmploymentContract::getStartDate, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                    .thenComparing(EmploymentContract::getId, java.util.Comparator.reverseOrder())
            )
            .map(employmentContractMapper::toDto)
            .toList();
    }

    /**
     * Get all the employmentContracts with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<EmploymentContractDTO> findAllWithEagerRelationships(Pageable pageable) {
        return employmentContractRepository.findAllWithEagerRelationships(pageable).map(employmentContractMapper::toDto);
    }

    /**
     * Get one employmentContract by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<EmploymentContractDTO> findOne(Long id) {
        LOG.debug("Request to get EmploymentContract : {}", id);
        return employmentContractRepository.findOneWithEagerRelationships(id).map(employmentContractMapper::toDto);
    }

    /**
     * Delete the employmentContract by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete EmploymentContract : {}", id);
        employmentContractRepository.deleteById(id);
    }
}

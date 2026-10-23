package vn.doit.hrm.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.payroll.NetGrossConverter;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.service.dto.SalaryChangeDTO;
import vn.doit.hrm.service.mapper.SalaryChangeMapper;
import vn.doit.hrm.service.payroll.RuleBookService;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.SalaryChange}.
 */
@Service
@Transactional
public class SalaryChangeService {

    private static final Logger LOG = LoggerFactory.getLogger(SalaryChangeService.class);

    private final SalaryChangeRepository salaryChangeRepository;

    private final SalaryChangeMapper salaryChangeMapper;

    private final EmployeeRepository employeeRepository;

    private final RuleBookService ruleBookService;

    private final ArchivedEmployeeGuard archivedGuard;

    public SalaryChangeService(
        SalaryChangeRepository salaryChangeRepository,
        SalaryChangeMapper salaryChangeMapper,
        EmployeeRepository employeeRepository,
        RuleBookService ruleBookService,
        ArchivedEmployeeGuard archivedGuard
    ) {
        this.archivedGuard = archivedGuard;
        this.ruleBookService = ruleBookService;
        this.salaryChangeRepository = salaryChangeRepository;
        this.salaryChangeMapper = salaryChangeMapper;
        this.employeeRepository = employeeRepository;
    }

    /** Lương Net chưa có Gross thì điền gợi ý theo số người phụ thuộc và tháng hiệu lực. */
    private void applyRules(SalaryChange c) {
        if (c.getBasis() == SalaryBasis.NET && c.getGrossAmount() == null && c.getAmount() != null && c.getEffectiveDate() != null) {
            int dependants = 0;
            boolean insured = true;
            if (c.getEmployee() != null && c.getEmployee().getId() != null) {
                var employee = employeeRepository.findById(c.getEmployee().getId());
                dependants = employee.map(e -> e.getDependants() == null ? 0 : e.getDependants()).orElse(0);
                // Không thuộc diện đóng BHXH bắt buộc: Gross không cộng BH người lao động.
                insured = employee.map(e -> e.getInsuranceExemption() == null).orElse(true);
            }
            NetGrossConverter.suggestGross(BigDecimal.valueOf(c.getAmount()), dependants, YearMonth.from(c.getEffectiveDate()), ruleBookService.current(), insured).ifPresent(
                g -> c.setGrossAmount(g.longValueExact())
            );
        }
    }

    /**
     * Save a salaryChange.
     *
     * @param salaryChangeDTO the entity to save.
     * @return the persisted entity.
     */
    public SalaryChangeDTO save(SalaryChangeDTO salaryChangeDTO) {
        LOG.debug("Request to save SalaryChange : {}", salaryChangeDTO);
        archivedGuard.requireActive(salaryChangeDTO.getEmployee() == null ? null : salaryChangeDTO.getEmployee().getId());
        SalaryChange salaryChange = salaryChangeMapper.toEntity(salaryChangeDTO);
        applyRules(salaryChange);
        salaryChange = salaryChangeRepository.save(salaryChange);
        return salaryChangeMapper.toDto(salaryChange);
    }

    /**
     * Update a salaryChange.
     *
     * @param salaryChangeDTO the entity to save.
     * @return the persisted entity.
     */
    public SalaryChangeDTO update(SalaryChangeDTO salaryChangeDTO) {
        LOG.debug("Request to update SalaryChange : {}", salaryChangeDTO);
        archivedGuard.requireActive(salaryChangeDTO.getEmployee() == null ? null : salaryChangeDTO.getEmployee().getId());
        salaryChangeRepository
            .findById(salaryChangeDTO.getId())
            .ifPresent(existing -> {
                archivedGuard.requireActive(existing.getEmployee());
                RecordOwnership.requireSameEmployee(existing.getEmployee(), salaryChangeDTO.getEmployee(), false);
            });
        SalaryChange salaryChange = salaryChangeMapper.toEntity(salaryChangeDTO);
        applyRules(salaryChange);
        salaryChange = salaryChangeRepository.save(salaryChange);
        return salaryChangeMapper.toDto(salaryChange);
    }

    /**
     * Partially update a salaryChange.
     *
     * @param salaryChangeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<SalaryChangeDTO> partialUpdate(SalaryChangeDTO salaryChangeDTO) {
        LOG.debug("Request to partially update SalaryChange : {}", salaryChangeDTO);
        MoneyRules.requireNonNegative(salaryChangeDTO.getAmount(), salaryChangeDTO.getGrossAmount());

        return salaryChangeRepository
            .findById(salaryChangeDTO.getId())
            .map(existingSalaryChange -> {
                archivedGuard.requireActive(existingSalaryChange.getEmployee());
                RecordOwnership.requireSameEmployee(existingSalaryChange.getEmployee(), salaryChangeDTO.getEmployee(), true);
                salaryChangeMapper.partialUpdate(existingSalaryChange, salaryChangeDTO);
                applyRules(existingSalaryChange);

                return existingSalaryChange;
            })
            .map(salaryChangeRepository::save)
            .map(salaryChangeMapper::toDto);
    }

    /**
     * Get all the salaryChanges.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<SalaryChangeDTO> findAll() {
        LOG.debug("Request to get all SalaryChanges");
        return salaryChangeRepository.findAll().stream().map(salaryChangeMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /** Điều chỉnh lương của một nhân viên, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<SalaryChangeDTO> findByEmployee(Long employeeId) {
        return salaryChangeRepository
            .findByEmployeeId(employeeId)
            .stream()
            .sorted(
                java.util.Comparator.comparing(SalaryChange::getEffectiveDate, java.util.Comparator.reverseOrder()).thenComparing(
                    SalaryChange::getId,
                    java.util.Comparator.reverseOrder()
                )
            )
            .map(salaryChangeMapper::toDto)
            .toList();
    }

    /**
     * Get all the salaryChanges with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public List<SalaryChangeDTO> findAllWithEagerRelationships() {
        return salaryChangeRepository
            .findAllWithEagerRelationships()
            .stream()
            .map(salaryChangeMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one salaryChange by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<SalaryChangeDTO> findOne(Long id) {
        LOG.debug("Request to get SalaryChange : {}", id);
        return salaryChangeRepository.findOneWithEagerRelationships(id).map(salaryChangeMapper::toDto);
    }

    /**
     * Delete the salaryChange by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete SalaryChange : {}", id);
        salaryChangeRepository.findById(id).ifPresent(c -> archivedGuard.requireActive(c.getEmployee()));
        salaryChangeRepository.deleteById(id);
    }
}

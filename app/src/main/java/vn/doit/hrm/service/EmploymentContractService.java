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

    private final PendingTerminationGuard pendingTerminationGuard;

    private final DocumentNumberService documentNumberService;

    private final vn.doit.hrm.service.payroll.RuleBookService ruleBookService;

    private final java.time.Clock clock;

    public EmploymentContractService(
        EmploymentContractRepository employmentContractRepository,
        EmploymentContractMapper employmentContractMapper,
        EmployeeRepository employeeRepository,
        ApplicationProperties applicationProperties,
        PendingTerminationGuard pendingTerminationGuard,
        DocumentNumberService documentNumberService,
        vn.doit.hrm.service.payroll.RuleBookService ruleBookService,
        java.time.Clock clock
    ) {
        this.ruleBookService = ruleBookService;
        this.clock = clock;
        this.pendingTerminationGuard = pendingTerminationGuard;
        this.documentNumberService = documentNumberService;
        this.employmentContractRepository = employmentContractRepository;
        this.employmentContractMapper = employmentContractMapper;
        this.employeeRepository = employeeRepository;
        this.applicationProperties = applicationProperties;
    }

    /**
     * Số HĐ gõ tay: bỏ khoảng trắng, trống thành null; khi số thay đổi thì không được trùng số đã có trên văn bản khác.
     * Số không đổi thì không kiểm (HĐ nhập trước khi có đánh số có thể trùng nhau, vẫn phải sửa được các trường khác).
     */
    private void checkContractNo(EmploymentContract c, String previousNo) {
        String no = c.getContractNo() == null ? null : c.getContractNo().trim();
        c.setContractNo(no == null || no.isEmpty() ? null : no);
        boolean changed = c.getContractNo() != null && !c.getContractNo().equals(previousNo == null ? null : previousNo.trim());
        if (changed && documentNumberService.isTakenByOther(c.getContractNo(), c.getId())) {
            throw new EmployeeRuleException("Số " + c.getContractNo() + " đã được dùng cho văn bản khác", "numbertaken");
        }
    }

    /** Kiểm tra số HĐ, thời hạn, điền ngày kết thúc mặc định và gợi ý Gross khi lương là Net. */
    private void applyRules(EmploymentContract c, String previousNo) {
        checkContractNo(c, previousNo);
        // Kết quả thử việc chỉ ghi trên HĐ thử việc; thiếu ngày thì lấy hôm nay.
        if (c.getProbationResult() != null) {
            if (c.getType() != vn.doit.hrm.domain.enumeration.ContractType.HDTV) {
                throw new EmployeeRuleException("Kết quả thử việc chỉ ghi trên hợp đồng thử việc", "probationresultnothdtv");
            }
            if (c.getProbationResultDate() == null) {
                c.setProbationResultDate(java.time.LocalDate.now(clock));
            }
        }
        if (c.getStatus() == vn.doit.hrm.domain.enumeration.ContractStatus.DA_KY && c.getEmployee() != null) {
            pendingTerminationGuard.checkStart(c.getEmployee().getId(), c.getStartDate());
        }
        ContractRules.validate(
            c.getType(),
            c.getTermMonths(),
            c.getStartDate(),
            c.getEndDate(),
            applicationProperties.getContract().getTermOptionsMonths()
        );
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
            NetGrossConverter.suggestGross(BigDecimal.valueOf(c.getSalary()), dependants, YearMonth.from(c.getStartDate()), ruleBookService.current()).ifPresent(g ->
                c.setGrossAmount(g.longValueExact())
            );
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
        // Bản đã ký chỉ gắn qua "Tải bản đã ký lên".
        employmentContract.setSignedFileId(null);
        applyRules(employmentContract, null);
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
        Optional<EmploymentContract> existing = employmentContractRepository.findById(employmentContractDTO.getId());
        existing.ifPresent(e -> RecordOwnership.requireSameEmployee(e.getEmployee(), employmentContractDTO.getEmployee(), false));
        Long signedFileId = existing.map(EmploymentContract::getSignedFileId).orElse(null);
        String previousNo = existing.map(EmploymentContract::getContractNo).orElse(null);
        EmploymentContract employmentContract = employmentContractMapper.toEntity(employmentContractDTO);
        // Bản đã ký chỉ gắn qua "Tải bản đã ký lên": giữ giá trị đang có, không lấy từ dữ liệu gửi lên.
        employmentContract.setSignedFileId(signedFileId);
        applyRules(employmentContract, previousNo);
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
        MoneyRules.requireNonNegative(employmentContractDTO.getSalary(), employmentContractDTO.getGrossAmount());
        employmentContractDTO.setSignedFileId(null);

        return employmentContractRepository
            .findById(employmentContractDTO.getId())
            .map(existingEmploymentContract -> {
                RecordOwnership.requireSameEmployee(existingEmploymentContract.getEmployee(), employmentContractDTO.getEmployee(), true);
                // Kiểm tra lịch nghỉ việc trước khi sửa thực thể đang được quản lý (tránh để lại thay đổi dở dang).
                var status =
                    employmentContractDTO.getStatus() != null ? employmentContractDTO.getStatus() : existingEmploymentContract.getStatus();
                var start =
                    employmentContractDTO.getStartDate() != null
                        ? employmentContractDTO.getStartDate()
                        : existingEmploymentContract.getStartDate();
                if (status == vn.doit.hrm.domain.enumeration.ContractStatus.DA_KY && existingEmploymentContract.getEmployee() != null) {
                    pendingTerminationGuard.checkStart(existingEmploymentContract.getEmployee().getId(), start);
                }
                String previousNo = existingEmploymentContract.getContractNo();
                employmentContractMapper.partialUpdate(existingEmploymentContract, employmentContractDTO);
                applyRules(existingEmploymentContract, previousNo);

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
                java.util.Comparator.comparing(
                    EmploymentContract::getStartDate,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())
                ).thenComparing(EmploymentContract::getId, java.util.Comparator.reverseOrder())
            )
            .map(employmentContractMapper::toDto)
            .toList();
    }

    /**
     * Get all the employmentContracts with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public List<EmploymentContractDTO> findAllWithEagerRelationships() {
        return employmentContractRepository
            .findAllWithEagerRelationships()
            .stream()
            .map(employmentContractMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
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
        // Chỉ xóa được bản nháp; HĐ đã ký/đã kết thúc/đã thanh lý là dữ liệu lịch sử, không xóa cứng.
        employmentContractRepository.findById(id).ifPresent(c -> {
            if (c.getStatus() != vn.doit.hrm.domain.enumeration.ContractStatus.NHAP) {
                throw new EmployeeRuleException("Chỉ xóa được hợp đồng nháp; hợp đồng đã ký là dữ liệu lịch sử", "cannotdeletesigned");
            }
            employmentContractRepository.delete(c);
        });
    }
}

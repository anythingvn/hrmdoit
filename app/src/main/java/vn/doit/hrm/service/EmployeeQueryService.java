package vn.doit.hrm.service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;
import tech.jhipster.service.filter.StringFilter;
import vn.doit.hrm.domain.*; // for static metamodels
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.service.criteria.EmployeeCriteria;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.mapper.EmployeeMapper;

/**
 * Service for executing complex queries for {@link Employee} entities in the database.
 * The main input is a {@link EmployeeCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link EmployeeDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeQueryService extends QueryService<Employee> {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeQueryService.class);

    private final EmployeeRepository employeeRepository;

    private final EmployeeMapper employeeMapper;

    private final Clock clock;

    private final vn.doit.hrm.repository.WorkAssignmentRepository workAssignmentRepository;

    private final vn.doit.hrm.repository.TerminationRecordRepository terminationRecordRepository;

    public EmployeeQueryService(
        EmployeeRepository employeeRepository,
        EmployeeMapper employeeMapper,
        Clock clock,
        vn.doit.hrm.repository.WorkAssignmentRepository workAssignmentRepository,
        vn.doit.hrm.repository.TerminationRecordRepository terminationRecordRepository
    ) {
        this.terminationRecordRepository = terminationRecordRepository;
        this.workAssignmentRepository = workAssignmentRepository;
        this.employeeRepository = employeeRepository;
        this.employeeMapper = employeeMapper;
        this.clock = clock;
    }

    /**
     * Return a {@link Page} of {@link EmployeeDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<EmployeeDTO> findByCriteria(EmployeeCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<Employee> specification = createSpecification(criteria);
        Page<Employee> rows = employeeRepository.findAll(specification, page);
        // Nạp lịch sử vị trí của cả trang bằng một truy vấn (không N+1).
        java.util.List<Long> ids = rows.getContent().stream().map(Employee::getId).toList();
        java.util.Map<Long, java.util.List<vn.doit.hrm.domain.WorkAssignment>> byEmployee = ids.isEmpty()
            ? java.util.Map.of()
            : workAssignmentRepository
                .findByEmployeeIdIn(ids)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(a -> a.getEmployee().getId()));
        java.util.Map<Long, vn.doit.hrm.domain.TerminationRecord> scheduled = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            terminationRecordRepository
                .findByEmployeeIdInAndStatus(ids, vn.doit.hrm.domain.enumeration.TerminationRecordStatus.SCHEDULED)
                .forEach(r -> scheduled.put(r.getEmployeeId(), r));
        }
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        return rows.map(e -> {
            EmployeeDTO dto = employeeMapper.toDto(e);
            vn.doit.hrm.domain.TerminationRecord r = scheduled.get(e.getId());
            if (r != null) {
                dto.setScheduledTermination(r.getLastWorkingDay());
                if (r.isOverdue(today)) {
                    dto.setScheduledTerminationOverdue(true);
                    dto.setScheduledTerminationError(r.getLastError());
                }
            }
            dto.setCurrentSiteName(CurrentSite.nameOn(e, byEmployee.getOrDefault(e.getId(), java.util.List.of()), today));
            return dto;
        });
    }

    /** Toàn bộ nhân viên khớp bộ lọc (không phân trang), dùng cho xuất Excel. */
    @Transactional(readOnly = true)
    public java.util.List<Employee> findEntitiesByCriteria(EmployeeCriteria criteria, org.springframework.data.domain.Sort sort) {
        return employeeRepository.findAll(createSpecification(criteria), sort);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(EmployeeCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Employee> specification = createSpecification(criteria);
        return employeeRepository.count(specification);
    }

    /**
     * Function to convert {@link EmployeeCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Employee> createSpecification(EmployeeCriteria criteria) {
        // Hồ sơ đã lưu trữ không bao giờ hiện ở danh sách, đếm, tìm kiếm, ô chọn hay xuất Excel (chỉ ở vùng lưu trữ).
        Specification<Employee> specification = (root, query, builder) -> builder.isNull(root.get(Employee_.archivedAt));
        specification = specification.and((root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch(Employee_.department, JoinType.LEFT);
                root.fetch(Employee_.manager, JoinType.LEFT);
                root.fetch(Employee_.user, JoinType.LEFT);
            }
            return null;
        });
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = specification.and(
                Specification.allOf(
                    Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                    buildRangeSpecification(criteria.getId(), Employee_.id),
                    buildStringSpecification(criteria.getCode(), Employee_.code),
                    buildSpecification(criteria.getPersonType(), Employee_.personType),
                    buildStringSpecification(criteria.getFullName(), Employee_.fullName),
                    buildStringSpecification(criteria.getSearchText(), Employee_.searchText),
                    buildSpecification(criteria.getGender(), Employee_.gender),
                    buildRangeSpecification(criteria.getBirthDate(), Employee_.birthDate),
                    buildStringSpecification(criteria.getEmail(), Employee_.email),
                    buildStringSpecification(criteria.getPhone(), Employee_.phone),
                    buildStringSpecification(criteria.getResidence(), Employee_.residence),
                    buildStringSpecification(criteria.getEducation(), Employee_.education),
                    buildStringSpecification(criteria.getIdNumber(), Employee_.idNumber),
                    buildRangeSpecification(criteria.getIdIssueDate(), Employee_.idIssueDate),
                    buildStringSpecification(criteria.getIdIssuePlace(), Employee_.idIssuePlace),
                    buildStringSpecification(criteria.getTaxCode(), Employee_.taxCode),
                    buildStringSpecification(criteria.getBankAccount(), Employee_.bankAccount),
                    buildStringSpecification(criteria.getBankName(), Employee_.bankName),
                    buildStringSpecification(criteria.getSocialInsuranceNo(), Employee_.socialInsuranceNo),
                    buildRangeSpecification(criteria.getSocialInsuranceStart(), Employee_.socialInsuranceStart),
                    buildRangeSpecification(criteria.getSocialInsuranceSalary(), Employee_.socialInsuranceSalary),
                    buildRangeSpecification(criteria.getDependants(), Employee_.dependants),
                    buildStringSpecification(criteria.getPosition(), Employee_.position),
                    buildSpecification(criteria.getStatus(), Employee_.status),
                    buildRangeSpecification(criteria.getJoinDate(), Employee_.joinDate),
                    buildRangeSpecification(criteria.getLastWorkingDay(), Employee_.lastWorkingDay),
                    buildSpecification(criteria.getTerminationType(), Employee_.terminationType),
                    buildStringSpecification(criteria.getTerminationReason(), Employee_.terminationReason),
                    buildSpecification(criteria.getDepartmentId(), root ->
                        root.join(Employee_.department, JoinType.LEFT).get(Department_.id)
                    ),
                    buildSpecification(criteria.getManagerId(), root -> root.join(Employee_.manager, JoinType.LEFT).get(Employee_.id)),
                    buildSpecification(criteria.getUserId(), root -> root.join(Employee_.user, JoinType.LEFT).get(User_.id))
                )
            );
            if (criteria.getQ() != null && criteria.getQ().getContains() != null) {
                StringFilter folded = new StringFilter();
                folded.setContains(VietnameseText.fold(criteria.getQ().getContains()));
                specification = specification.and(buildStringSpecification(folded, Employee_.searchText));
            }
            if (criteria.getCurrentSiteId() != null && criteria.getCurrentSiteId().getEquals() != null) {
                specification = specification.and(currentSite(criteria.getCurrentSiteId().getEquals()));
            }
            if (criteria.getCurrentContractType() != null && criteria.getCurrentContractType().getEquals() != null) {
                specification = specification.and(currentContract(criteria.getCurrentContractType().getEquals()));
            }
        }
        return specification;
    }

    private Specification<Employee> currentSite(Long siteId) {
        final LocalDate today = LocalDate.now(clock);
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            var wa = sub.from(WorkAssignment.class);
            sub
                .select(wa.get(WorkAssignment_.id))
                .where(
                    cb.equal(wa.get(WorkAssignment_.employee), root),
                    cb.equal(wa.get(WorkAssignment_.site).get(WorkSite_.id), siteId),
                    cb.lessThanOrEqualTo(wa.get(WorkAssignment_.fromDate), today),
                    cb.or(cb.isNull(wa.get(WorkAssignment_.toDate)), cb.greaterThanOrEqualTo(wa.get(WorkAssignment_.toDate), today))
                );
            return cb.exists(sub);
        };
    }

    private Specification<Employee> currentContract(String type) {
        final LocalDate today = LocalDate.now(clock);
        final ContractType contractType;
        try {
            contractType = ContractType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return (root, query, cb) -> cb.disjunction();
        }
        // Cùng điều kiện với ContractRules.currentContract: tồn tại HĐ loại này đang hiệu lực và không có HĐ đang hiệu lực
        // nào đứng sau nó theo ContractRules.CURRENT_ORDER (bắt đầu muộn hơn, hoặc cùng ngày mà id lớn hơn); nhân viên chưa nghỉ.
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            var c = sub.from(EmploymentContract.class);
            Subquery<Long> later = query.subquery(Long.class);
            var c2 = later.from(EmploymentContract.class);
            later
                .select(c2.get(EmploymentContract_.id))
                .where(
                    cb.equal(c2.get(EmploymentContract_.employee), root),
                    cb.equal(c2.get(EmploymentContract_.status), ContractStatus.DA_KY),
                    cb.lessThanOrEqualTo(c2.get(EmploymentContract_.startDate), today),
                    cb.or(
                        cb.isNull(c2.get(EmploymentContract_.endDate)),
                        cb.greaterThanOrEqualTo(c2.get(EmploymentContract_.endDate), today)
                    ),
                    cb.or(
                        cb.greaterThan(c2.get(EmploymentContract_.startDate), c.get(EmploymentContract_.startDate)),
                        cb.and(
                            cb.equal(c2.get(EmploymentContract_.startDate), c.get(EmploymentContract_.startDate)),
                            cb.greaterThan(c2.get(EmploymentContract_.id), c.get(EmploymentContract_.id))
                        )
                    )
                );
            sub
                .select(c.get(EmploymentContract_.id))
                .where(
                    cb.equal(c.get(EmploymentContract_.employee), root),
                    cb.equal(c.get(EmploymentContract_.type), contractType),
                    cb.equal(c.get(EmploymentContract_.status), ContractStatus.DA_KY),
                    cb.lessThanOrEqualTo(c.get(EmploymentContract_.startDate), today),
                    cb.or(
                        cb.isNull(c.get(EmploymentContract_.endDate)),
                        cb.greaterThanOrEqualTo(c.get(EmploymentContract_.endDate), today)
                    ),
                    cb.not(cb.exists(later))
                );
            return cb.and(cb.notEqual(root.get(Employee_.status), EmploymentStatus.DA_NGHI), cb.exists(sub));
        };
    }
}

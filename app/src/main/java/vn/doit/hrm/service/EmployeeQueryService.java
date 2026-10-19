package vn.doit.hrm.service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDate;
import tech.jhipster.service.filter.StringFilter;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;
import vn.doit.hrm.domain.*; // for static metamodels
import vn.doit.hrm.domain.Employee;
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

    public EmployeeQueryService(EmployeeRepository employeeRepository, EmployeeMapper employeeMapper, Clock clock) {
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
        return employeeRepository.findAll(specification, page).map(employeeMapper::toDto);
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
        Specification<Employee> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            if (criteria.getDistinct() != null) {
                specification = specification.and(distinct(criteria.getDistinct()));
            }
            if (criteria.getId() != null) {
                specification = specification.and(buildRangeSpecification(criteria.getId(), Employee_.id));
            }
            if (criteria.getCode() != null) {
                specification = specification.and(buildStringSpecification(criteria.getCode(), Employee_.code));
            }
            if (criteria.getPersonType() != null) {
                specification = specification.and(buildSpecification(criteria.getPersonType(), Employee_.personType));
            }
            if (criteria.getFullName() != null) {
                specification = specification.and(buildStringSpecification(criteria.getFullName(), Employee_.fullName));
            }
            if (criteria.getSearchText() != null) {
                specification = specification.and(buildStringSpecification(criteria.getSearchText(), Employee_.searchText));
            }
            if (criteria.getGender() != null) {
                specification = specification.and(buildSpecification(criteria.getGender(), Employee_.gender));
            }
            if (criteria.getBirthDate() != null) {
                specification = specification.and(buildRangeSpecification(criteria.getBirthDate(), Employee_.birthDate));
            }
            if (criteria.getEmail() != null) {
                specification = specification.and(buildStringSpecification(criteria.getEmail(), Employee_.email));
            }
            if (criteria.getPhone() != null) {
                specification = specification.and(buildStringSpecification(criteria.getPhone(), Employee_.phone));
            }
            if (criteria.getResidence() != null) {
                specification = specification.and(buildStringSpecification(criteria.getResidence(), Employee_.residence));
            }
            if (criteria.getEducation() != null) {
                specification = specification.and(buildStringSpecification(criteria.getEducation(), Employee_.education));
            }
            if (criteria.getIdNumber() != null) {
                specification = specification.and(buildStringSpecification(criteria.getIdNumber(), Employee_.idNumber));
            }
            if (criteria.getIdIssueDate() != null) {
                specification = specification.and(buildRangeSpecification(criteria.getIdIssueDate(), Employee_.idIssueDate));
            }
            if (criteria.getIdIssuePlace() != null) {
                specification = specification.and(buildStringSpecification(criteria.getIdIssuePlace(), Employee_.idIssuePlace));
            }
            if (criteria.getTaxCode() != null) {
                specification = specification.and(buildStringSpecification(criteria.getTaxCode(), Employee_.taxCode));
            }
            if (criteria.getBankAccount() != null) {
                specification = specification.and(buildStringSpecification(criteria.getBankAccount(), Employee_.bankAccount));
            }
            if (criteria.getBankName() != null) {
                specification = specification.and(buildStringSpecification(criteria.getBankName(), Employee_.bankName));
            }
            if (criteria.getSocialInsuranceNo() != null) {
                specification = specification.and(buildStringSpecification(criteria.getSocialInsuranceNo(), Employee_.socialInsuranceNo));
            }
            if (criteria.getSocialInsuranceStart() != null) {
                specification = specification.and(
                    buildRangeSpecification(criteria.getSocialInsuranceStart(), Employee_.socialInsuranceStart)
                );
            }
            if (criteria.getSocialInsuranceSalary() != null) {
                specification = specification.and(
                    buildRangeSpecification(criteria.getSocialInsuranceSalary(), Employee_.socialInsuranceSalary)
                );
            }
            if (criteria.getDependants() != null) {
                specification = specification.and(buildRangeSpecification(criteria.getDependants(), Employee_.dependants));
            }
            if (criteria.getPosition() != null) {
                specification = specification.and(buildStringSpecification(criteria.getPosition(), Employee_.position));
            }
            if (criteria.getStatus() != null) {
                specification = specification.and(buildSpecification(criteria.getStatus(), Employee_.status));
            }
            if (criteria.getJoinDate() != null) {
                specification = specification.and(buildRangeSpecification(criteria.getJoinDate(), Employee_.joinDate));
            }
            if (criteria.getLastWorkingDay() != null) {
                specification = specification.and(buildRangeSpecification(criteria.getLastWorkingDay(), Employee_.lastWorkingDay));
            }
            if (criteria.getTerminationType() != null) {
                specification = specification.and(buildSpecification(criteria.getTerminationType(), Employee_.terminationType));
            }
            if (criteria.getTerminationReason() != null) {
                specification = specification.and(buildStringSpecification(criteria.getTerminationReason(), Employee_.terminationReason));
            }
            if (criteria.getDepartmentId() != null) {
                specification = specification.and(
                    buildSpecification(criteria.getDepartmentId(), root ->
                        root.join(Employee_.department, JoinType.LEFT).get(Department_.id)
                    )
                );
            }
            if (criteria.getManagerId() != null) {
                specification = specification.and(
                    buildSpecification(criteria.getManagerId(), root -> root.join(Employee_.manager, JoinType.LEFT).get(Employee_.id))
                );
            }
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
            if (criteria.getUserId() != null) {
                specification = specification.and(
                    buildSpecification(criteria.getUserId(), root -> root.join(Employee_.user, JoinType.LEFT).get(User_.id))
                );
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
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            var c = sub.from(EmploymentContract.class);
            sub
                .select(c.get(EmploymentContract_.id))
                .where(
                    cb.equal(c.get(EmploymentContract_.employee), root),
                    cb.equal(c.get(EmploymentContract_.type), contractType),
                    cb.equal(c.get(EmploymentContract_.status), ContractStatus.DA_KY),
                    cb.lessThanOrEqualTo(c.get(EmploymentContract_.startDate), today),
                    cb.or(cb.isNull(c.get(EmploymentContract_.endDate)), cb.greaterThanOrEqualTo(c.get(EmploymentContract_.endDate), today))
                );
            return cb.exists(sub);
        };
    }
}

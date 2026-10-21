package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.EmploymentContract;

/**
 * Spring Data JPA repository for the EmploymentContract entity.
 */
@Repository
public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {
    default Optional<EmploymentContract> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<EmploymentContract> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<EmploymentContract> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select employmentContract from EmploymentContract employmentContract left join fetch employmentContract.employee left join fetch employmentContract.department",
        countQuery = "select count(employmentContract) from EmploymentContract employmentContract"
    )
    Page<EmploymentContract> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select employmentContract from EmploymentContract employmentContract left join fetch employmentContract.employee left join fetch employmentContract.department"
    )
    List<EmploymentContract> findAllWithToOneRelationships();

    @Query(
        "select employmentContract from EmploymentContract employmentContract left join fetch employmentContract.employee left join fetch employmentContract.department where employmentContract.id =:id"
    )
    Optional<EmploymentContract> findOneWithToOneRelationships(@Param("id") Long id);

    List<EmploymentContract> findByEmployeeId(Long employeeId);

    boolean existsByContractNo(String contractNo);

    boolean existsByContractNoAndIdNot(String contractNo, Long id);

    /** Khóa dòng hợp đồng đến hết giao dịch (hai người sinh văn bản cùng lúc không cấp hai số). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmploymentContract c where c.id = :id")
    Optional<EmploymentContract> findForUpdate(@Param("id") Long id);

    List<EmploymentContract> findByEmployeeIdIn(java.util.Collection<Long> employeeIds);

    /** Số nhân viên đang làm có HĐTV đã ký còn hiệu lực ngày {@code day}. */
    @Query(
        "select count(distinct c.employee.id) from EmploymentContract c where c.type = vn.doit.hrm.domain.enumeration.ContractType.HDTV" +
        " and c.status = vn.doit.hrm.domain.enumeration.ContractStatus.DA_KY and c.startDate <= :day and (c.endDate is null or c.endDate >= :day)" +
        " and c.employee.personType = vn.doit.hrm.domain.enumeration.PersonType.NHAN_VIEN" +
        " and c.employee.status = vn.doit.hrm.domain.enumeration.EmploymentStatus.DANG_LAM_VIEC"
    )
    long countOnProbation(@Param("day") java.time.LocalDate day);

    /** Số HĐLĐ có thời hạn đã ký, hết hạn trong [from, to], là HĐLĐ mới nhất của nhân viên đang làm. */
    @Query(
        "select count(c) from EmploymentContract c where c.type = vn.doit.hrm.domain.enumeration.ContractType.HDLD_XDTH" +
        " and c.status = vn.doit.hrm.domain.enumeration.ContractStatus.DA_KY and c.endDate between :from and :to" +
        " and c.employee.personType = vn.doit.hrm.domain.enumeration.PersonType.NHAN_VIEN" +
        " and c.employee.status = vn.doit.hrm.domain.enumeration.EmploymentStatus.DANG_LAM_VIEC" +
        " and not exists (select n from EmploymentContract n where n.employee = c.employee and n.startDate > c.startDate" +
        " and n.status = vn.doit.hrm.domain.enumeration.ContractStatus.DA_KY" +
        " and n.type in (vn.doit.hrm.domain.enumeration.ContractType.HDLD_XDTH, vn.doit.hrm.domain.enumeration.ContractType.HDLD_KXDTH))"
    )
    long countExpiringBetween(@Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}

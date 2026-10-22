package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.Employee;

/**
 * Spring Data JPA repository for the Employee entity.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {
    @Query("select employee from Employee employee where employee.user.login = ?#{authentication.name}")
    List<Employee> findByUserIsCurrentUser();

    default Optional<Employee> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<Employee> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<Employee> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select employee from Employee employee left join fetch employee.department left join fetch employee.manager left join fetch employee.user",
        countQuery = "select count(employee) from Employee employee"
    )
    Page<Employee> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select employee from Employee employee left join fetch employee.department left join fetch employee.manager left join fetch employee.user"
    )
    List<Employee> findAllWithToOneRelationships();

    @Query(
        "select employee from Employee employee left join fetch employee.department left join fetch employee.manager left join fetch employee.user where employee.id =:id"
    )
    Optional<Employee> findOneWithToOneRelationships(@Param("id") Long id);

    /** Hồ sơ gắn với tài khoản {@code login} (mỗi tài khoản gắn tối đa một hồ sơ). */
    @Query(
        "select employee from Employee employee left join fetch employee.department left join fetch employee.manager join fetch employee.user u where u.login = :login"
    )
    Optional<Employee> findOneByUserLogin(@Param("login") String login);

    Optional<Employee> findOneByUserId(Long userId);

    long countByPersonTypeAndStatus(vn.doit.hrm.domain.enumeration.PersonType personType, vn.doit.hrm.domain.enumeration.EmploymentStatus status);

    @EntityGraph(attributePaths = "department")
    List<Employee> findByPersonType(vn.doit.hrm.domain.enumeration.PersonType personType);

    @EntityGraph(attributePaths = "manager")
    List<Employee> findByPersonTypeAndStatus(vn.doit.hrm.domain.enumeration.PersonType personType, vn.doit.hrm.domain.enumeration.EmploymentStatus status);

    /** Hồ sơ có số CCCD {@code idNumber} (so sánh sau khi bỏ khoảng trắng hai đầu), để gắn tài khoản Quản trị tạo bằng CCCD. */
    @Query("select employee from Employee employee left join fetch employee.user where trim(employee.idNumber) = :idNumber")
    List<Employee> findAllByTrimmedIdNumber(@Param("idNumber") String idNumber);

    /** Khóa dòng hồ sơ khi tạo/gắn tài khoản, để hai yêu cầu đồng thời không cùng gắn. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select employee from Employee employee where employee.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    boolean existsByIdAndUserLogin(Long id, String login);

    boolean existsByCode(String code);

    /** Id mọi hồ sơ loại {@code personType} (kể cả đã nghỉ), cho việc tự động của sổ phép. */
    @Query("select e.id from Employee e where e.personType = :personType order by e.id")
    List<Long> findIdsByPersonType(@Param("personType") vn.doit.hrm.domain.enumeration.PersonType personType);

    Optional<Employee> findOneByCode(String code);

    /** Tìm theo chuỗi không dấu (mã + họ tên + email), tối đa 200 hồ sơ. */
    List<Employee> findTop200BySearchTextContainingOrderByFullNameAsc(String folded);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query(value = "select nextval('hddv_code_seq')", nativeQuery = true)
    long nextServiceCodeNumber();

    /** Mọi mã bắt đầu bằng tiền tố (kể cả người đã nghỉ), để tính số thứ tự cộng dồn. */
    @Query("select e.code from Employee e where upper(e.code) like concat(:prefix, '%')")
    List<String> findCodesStartingWith(@Param("prefix") String prefix);

    /** Khóa theo giao dịch khi cấp mã tự động: hai hồ sơ tạo cùng lúc không nhận trùng mã. */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(4711002)) l", nativeQuery = true)
    long lockCodeAssignment();

    /** Id người NHAN_VIEN có quản lý trực tiếp là {@code managerId} (cho lịch nghỉ). */
    @Query("select e.id from Employee e where e.manager.id = :managerId and e.personType = vn.doit.hrm.domain.enumeration.PersonType.NHAN_VIEN")
    List<Long> findNhanVienIdsByManagerId(@Param("managerId") Long managerId);

    /** Id người NHAN_VIEN thuộc phòng đang hoạt động có trưởng phòng là {@code headId}. */
    @Query(
        "select e.id from Employee e where e.department.head.id = :headId and e.department.active = true and e.personType = vn.doit.hrm.domain.enumeration.PersonType.NHAN_VIEN"
    )
    List<Long> findNhanVienIdsByDepartmentHeadId(@Param("headId") Long headId);

    /** Hồ sơ NHAN_VIEN theo id kèm phòng ban (một truy vấn, không N+1). */
    @Query(
        "select e from Employee e left join fetch e.department where e.id in :ids and e.personType = vn.doit.hrm.domain.enumeration.PersonType.NHAN_VIEN"
    )
    List<Employee> findNhanVienWithDepartmentByIdIn(@Param("ids") java.util.Collection<Long> ids);
}

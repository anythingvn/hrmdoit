package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Hồ sơ có số CCCD {@code idNumber} (so sánh sau khi bỏ khoảng trắng hai đầu), để gắn tài khoản Quản trị tạo bằng CCCD. */
    @Query("select employee from Employee employee left join fetch employee.user where trim(employee.idNumber) = :idNumber")
    List<Employee> findAllByTrimmedIdNumber(@Param("idNumber") String idNumber);

    /** Khóa dòng hồ sơ khi tạo/gắn tài khoản, để hai yêu cầu đồng thời không cùng gắn. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select employee from Employee employee where employee.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    boolean existsByIdAndUserLogin(Long id, String login);

    boolean existsByCode(String code);

    Optional<Employee> findOneByCode(String code);

    /** Tìm theo chuỗi không dấu (mã + họ tên + email), tối đa 200 hồ sơ. */
    List<Employee> findTop200BySearchTextContainingOrderByFullNameAsc(String folded);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query(value = "select nextval('hddv_code_seq')", nativeQuery = true)
    long nextServiceCodeNumber();
}

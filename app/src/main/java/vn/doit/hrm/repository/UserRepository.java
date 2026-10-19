package vn.doit.hrm.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.User;

/**
 * Spring Data JPA repository for the {@link User} entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findOneByActivationKey(String activationKey);

    List<User> findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(Instant dateTime);

    Optional<User> findOneByResetKey(String resetKey);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesByEmailIgnoreCase(String email);

    Optional<User> findOneByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    Optional<User> findOneByLogin(String login);

    Page<User> findAllByIdNotNullAndActivatedIsTrue(Pageable pageable);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesById(Long id);

    /** Cờ kích hoạt theo tên đăng nhập (kiểm tra JWT ở mỗi request). */
    @org.springframework.data.jpa.repository.Query("select u.activated from User u where u.login = :login")
    Optional<Boolean> findActivatedByLogin(@org.springframework.data.repository.query.Param("login") String login);

    /** Tài khoản chưa gắn hồ sơ nhân viên nào (để chọn khi gắn tài khoản có sẵn). */
    @EntityGraph(attributePaths = "authorities")
    @org.springframework.data.jpa.repository.Query(
        "select u from User u where not exists (select 1 from Employee e where e.user = u) order by u.login"
    )
    List<User> findAllNotLinkedToEmployee();
}

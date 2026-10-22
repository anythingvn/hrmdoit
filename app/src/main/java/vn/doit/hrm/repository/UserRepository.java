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

    /** Số tài khoản đang hoạt động có vai trò {@code authority} (chặn khóa ADMIN cuối cùng). */
    @org.springframework.data.jpa.repository.Query(
        "select count(u) from User u join u.authorities a where a.name = :authority and u.activated = true"
    )
    long countActivatedWithAuthority(@org.springframework.data.repository.query.Param("authority") String authority);

    /** Cờ kích hoạt và lần đổi thông tin đăng nhập gần nhất theo tên đăng nhập (kiểm tra JWT ở mỗi request). */
    @org.springframework.data.jpa.repository.Query(
        "select new vn.doit.hrm.repository.UserLoginState(u.activated, u.credentialsChangedAt, u.mustChangePassword) from User u where u.login = :login"
    )
    Optional<UserLoginState> findLoginStateByLogin(@org.springframework.data.repository.query.Param("login") String login);

    /** Tài khoản chưa gắn hồ sơ nhân viên nào (để chọn khi gắn tài khoản có sẵn). */
    @EntityGraph(attributePaths = "authorities")
    @org.springframework.data.jpa.repository.Query(
        "select u from User u where not exists (select 1 from Employee e where e.user = u) order by u.login"
    )
    List<User> findAllNotLinkedToEmployee();

    /**
     * Tài khoản đang hoạt động có một trong các vai trò, bỏ tài khoản gắn với hồ sơ đã lưu trữ (không làm người duyệt BGĐ, không nhận
     * email nhắc / thông báo theo vai trò).
     */
    @org.springframework.data.jpa.repository.Query(
        "select distinct u from User u join u.authorities a where u.activated = true and a.name in :roles" +
        " and not exists (select 1 from Employee e where e.user = u and e.archivedAt is not null) order by u.login"
    )
    List<User> findActiveWithAnyAuthority(@org.springframework.data.repository.query.Param("roles") java.util.Collection<String> roles);
}

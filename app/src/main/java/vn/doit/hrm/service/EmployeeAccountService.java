package vn.doit.hrm.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.config.Constants;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.dto.AccountCandidateDTO;
import vn.doit.hrm.service.dto.EmployeeAccountCreateDTO;
import vn.doit.hrm.service.dto.EmployeeAccountDTO;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.mapper.EmployeeMapper;

/**
 * Tài khoản đăng nhập gắn hồ sơ nhân viên: mỗi tài khoản gắn tối đa một hồ sơ, mỗi hồ sơ tối đa một tài khoản
 * (CSDL có ràng buộc duy nhất {@code ux_employee__user_id} làm chốt chặn cuối).
 * <p>
 * ADMIN và HR được tạo/gắn/bỏ gắn; chỉ ADMIN được cấp {@code ROLE_ADMIN} hoặc gắn tài khoản có {@code ROLE_ADMIN}.
 * Bỏ gắn không xóa tài khoản.
 */
@Service
@Transactional
public class EmployeeAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeAccountService.class);

    /** Vai trò được cấp khi tạo tài khoản trên hồ sơ. */
    static final Set<String> GRANTABLE = Set.of(
        AuthoritiesConstants.NHAN_VIEN,
        AuthoritiesConstants.HR,
        AuthoritiesConstants.KE_TOAN,
        AuthoritiesConstants.BGD,
        AuthoritiesConstants.ADMIN
    );

    private static final int NAME_MAX = 50;

    private final EmployeeRepository employeeRepository;

    private final UserRepository userRepository;

    private final AuthorityRepository authorityRepository;

    private final PasswordEncoder passwordEncoder;

    private final EmployeeMapper employeeMapper;

    public EmployeeAccountService(
        EmployeeRepository employeeRepository,
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        PasswordEncoder passwordEncoder,
        EmployeeMapper employeeMapper
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeMapper = employeeMapper;
    }

    /** Tài khoản của hồ sơ; {@link EmployeeAccountDTO#NONE} khi chưa gắn; rỗng khi không có hồ sơ. */
    @Transactional(readOnly = true)
    public Optional<EmployeeAccountDTO> getAccount(Long employeeId) {
        return employeeRepository.findById(employeeId).map(e -> toDto(e.getUser()));
    }

    /**
     * Tạo tài khoản với mật khẩu ban đầu (đăng nhập được ngay, không cần email) và gắn vào hồ sơ.
     *
     * @return rỗng khi không có hồ sơ
     */
    public Optional<EmployeeAccountDTO> createAndLink(Long employeeId, EmployeeAccountCreateDTO request) {
        Optional<Employee> found = employeeRepository.findByIdForUpdate(employeeId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Employee employee = found.orElseThrow();
        if (employee.getUser() != null) {
            throw new EmployeeRuleException("Hồ sơ đã có tài khoản; bỏ gắn trước khi tạo tài khoản khác", "employeehasaccount");
        }
        Set<Authority> authorities = resolveAuthorities(request.authorities());
        String login = request.login().trim().toLowerCase();
        if (userRepository.findOneByLogin(login).isPresent()) {
            throw new EmployeeRuleException("Tên đăng nhập đã được dùng", "userexists");
        }
        User user = new User();
        user.setLogin(login);
        user.setFirstName(truncate(employee.getFullName()));
        user.setLangKey(Constants.DEFAULT_LANGUAGE);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setActivated(true);
        user.setAuthorities(authorities);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmployeeRuleException("Tên đăng nhập đã được dùng", "userexists");
        }
        employee.setUser(user);
        saveLink(employee);
        LOG.debug("Created account {} for Employee {}", login, employeeId);
        return Optional.of(toDto(user));
    }

    /**
     * Gắn tài khoản có sẵn vào hồ sơ.
     *
     * @return rỗng khi không có hồ sơ
     */
    public Optional<EmployeeAccountDTO> link(Long employeeId, Long userId) {
        Optional<Employee> found = employeeRepository.findByIdForUpdate(employeeId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Employee employee = found.orElseThrow();
        User user = userRepository
            .findOneWithAuthoritiesById(userId)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy tài khoản", "usernotfound"));
        if (hasAdmin(user) && !currentUserIsAdmin()) {
            throw new AccessDeniedException("Chỉ Quản trị được gắn tài khoản Quản trị");
        }
        if (employee.getUser() != null && employee.getUser().getId().equals(user.getId())) {
            return Optional.of(toDto(user));
        }
        Optional<Employee> holder = employeeRepository.findOneByUserId(user.getId());
        if (holder.isPresent()) {
            throw new EmployeeRuleException("Tài khoản đã gắn với hồ sơ nhân viên khác", "useralreadylinked");
        }
        if (employee.getUser() != null) {
            throw new EmployeeRuleException("Hồ sơ đã có tài khoản; bỏ gắn trước khi gắn tài khoản khác", "employeehasaccount");
        }
        employee.setUser(user);
        saveLink(employee);
        LOG.debug("Linked account {} to Employee {}", user.getLogin(), employeeId);
        return Optional.of(toDto(user));
    }

    /**
     * Bỏ gắn tài khoản khỏi hồ sơ; tài khoản vẫn còn.
     *
     * @return false khi không có hồ sơ
     */
    public boolean unlink(Long employeeId) {
        return employeeRepository
            .findById(employeeId)
            .map(employee -> {
                User user = employee.getUser();
                if (user != null) {
                    if (hasAdmin(user) && !currentUserIsAdmin()) {
                        throw new AccessDeniedException("Chỉ Quản trị được bỏ gắn tài khoản Quản trị");
                    }
                    employee.setUser(null);
                    employeeRepository.save(employee);
                    LOG.debug("Unlinked account {} from Employee {}", user.getLogin(), employeeId);
                }
                return true;
            })
            .orElse(false);
    }

    /** Tài khoản chưa gắn hồ sơ; HR không thấy tài khoản Quản trị (không được gắn). */
    @Transactional(readOnly = true)
    public List<AccountCandidateDTO> candidates() {
        boolean admin = currentUserIsAdmin();
        return userRepository
            .findAllNotLinkedToEmployee()
            .stream()
            .filter(u -> admin || !hasAdmin(u))
            .map(u ->
                new AccountCandidateDTO(
                    u.getId(),
                    u.getLogin(),
                    String.join(" ", nonNull(u.getFirstName()), nonNull(u.getLastName())).trim(),
                    u.isActivated()
                )
            )
            .toList();
    }

    /** Hồ sơ của người đang đăng nhập (không có trường nhạy cảm). */
    @Transactional(readOnly = true)
    public Optional<EmployeeDTO> findCurrentUserEmployee() {
        return SecurityUtils.getCurrentUserLogin().flatMap(employeeRepository::findOneByUserLogin).map(employeeMapper::toDto);
    }

    /** {@code id} là hồ sơ gắn với tài khoản đang đăng nhập (dùng trong {@code @PreAuthorize}). */
    @Transactional(readOnly = true)
    public boolean isOwnProfile(Long id) {
        if (id == null) {
            return false;
        }
        return SecurityUtils.getCurrentUserLogin()
            .map(login -> employeeRepository.existsByIdAndUserLogin(id, login))
            .orElse(false);
    }

    /** Ràng buộc duy nhất employee.user_id là chốt cuối khi hai yêu cầu đồng thời gắn cùng một tài khoản. */
    private void saveLink(Employee employee) {
        try {
            employeeRepository.saveAndFlush(employee);
        } catch (DataIntegrityViolationException e) {
            throw new EmployeeRuleException("Tài khoản đã gắn với hồ sơ nhân viên khác", "useralreadylinked");
        }
    }

    private Set<Authority> resolveAuthorities(Set<String> requested) {
        Set<String> names = requested == null || requested.isEmpty() ? Set.of(AuthoritiesConstants.NHAN_VIEN) : requested;
        Set<Authority> result = new HashSet<>();
        for (String name : names) {
            if (name == null || !GRANTABLE.contains(name)) {
                throw new EmployeeRuleException("Vai trò không hợp lệ", "badauthority");
            }
            result.add(
                authorityRepository.findById(name).orElseThrow(() -> new EmployeeRuleException("Vai trò không hợp lệ", "badauthority"))
            );
        }
        if (names.contains(AuthoritiesConstants.ADMIN) && !currentUserIsAdmin()) {
            throw new AccessDeniedException("Chỉ Quản trị được cấp vai trò Quản trị");
        }
        return result;
    }

    private static boolean currentUserIsAdmin() {
        return SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
    }

    private static boolean hasAdmin(User user) {
        return user
            .getAuthorities()
            .stream()
            .anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName()));
    }

    private static EmployeeAccountDTO toDto(User user) {
        if (user == null) {
            return EmployeeAccountDTO.NONE;
        }
        return new EmployeeAccountDTO(
            user.getId(),
            user.getLogin(),
            user.isActivated(),
            user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toCollection(java.util.TreeSet::new))
        );
    }

    private static String truncate(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.length() <= NAME_MAX ? trimmed : trimmed.substring(0, NAME_MAX);
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}

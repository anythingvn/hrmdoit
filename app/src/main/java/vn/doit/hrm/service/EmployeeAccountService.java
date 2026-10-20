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
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.config.Constants;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
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
 * Tài khoản đăng nhập gắn hồ sơ nhân viên: tên đăng nhập là số CCCD của hồ sơ (đổi theo khi sửa CCCD); mỗi tài khoản gắn tối
 * đa một hồ sơ, mỗi hồ sơ tối đa một tài khoản
 * (CSDL có ràng buộc duy nhất {@code ux_employee__user_id} làm chốt chặn cuối).
 * <p>
 * ADMIN và HR được tạo/gắn/bỏ gắn; chỉ ADMIN được cấp {@code ROLE_ADMIN} hoặc gắn tài khoản có {@code ROLE_ADMIN}.
 * Bỏ gắn không xóa tài khoản. Hồ sơ đã nghỉ: không tạo tài khoản mới, chỉ gắn được tài khoản đang khóa ({@code employeeterminated}).
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

    private final ApplicationProperties applicationProperties;

    public EmployeeAccountService(
        EmployeeRepository employeeRepository,
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        PasswordEncoder passwordEncoder,
        EmployeeMapper employeeMapper,
        ApplicationProperties applicationProperties
    ) {
        this.applicationProperties = applicationProperties;
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
     * Tạo tài khoản và gắn vào hồ sơ: tên đăng nhập = số CCCD của hồ sơ, mật khẩu ban đầu do người tạo nhập (để trống = mật khẩu
     * mặc định), phải đổi mật khẩu ở lần đăng nhập đầu ({@code mustChangePassword}).
     *
     * @return rỗng khi không có hồ sơ
     * @throws EmployeeRuleException {@code idnumberrequired}/{@code idnumberinvalid} khi hồ sơ chưa có CCCD hợp lệ,
     *     {@code loginexists} khi số đó đã là tên đăng nhập của tài khoản khác
     */
    public Optional<EmployeeAccountDTO> createAndLink(Long employeeId, EmployeeAccountCreateDTO request) {
        Optional<Employee> found = employeeRepository.findByIdForUpdate(employeeId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Employee employee = found.orElseThrow();
        if (employee.getStatus() == EmploymentStatus.DA_NGHI) {
            throw new EmployeeRuleException("Nhân viên đã nghỉ việc — không tạo tài khoản đăng nhập mới", "employeeterminated");
        }
        if (employee.getUser() != null) {
            throw new EmployeeRuleException("Hồ sơ đã có tài khoản; bỏ gắn trước khi tạo tài khoản khác", "employeehasaccount");
        }
        String login = IdNumbers.toLogin(employee.getIdNumber());
        Set<Authority> authorities = resolveAuthorities(request == null ? null : request.authorities());
        checkLoginFree(login, null);
        User user = new User();
        user.setLogin(login);
        user.setFirstName(truncate(employee.getFullName()));
        user.setLangKey(Constants.DEFAULT_LANGUAGE);
        String initial = request == null ? null : request.initialPassword();
        // Mật khẩu ban đầu do người tạo nhập, hoặc mật khẩu mặc định; người dùng vẫn phải đổi ở lần đăng nhập đầu.
        user.setPassword(passwordEncoder.encode(initial != null ? initial : applicationProperties.getAccount().getDefaultPassword()));
        user.setMustChangePassword(true);
        user.setActivated(true);
        user.setAuthorities(authorities);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw loginExists();
        }
        employee.setUser(user);
        saveLink(employee);
        LOG.debug("Created account #{} for Employee {}", user.getId(), employeeId);
        return Optional.of(toDto(user));
    }

    /**
     * Gắn tài khoản có sẵn vào hồ sơ; tên đăng nhập được đổi sang số CCCD của hồ sơ (phiên đăng nhập cũ của tài khoản hết hiệu
     * lực). Không gắn tài khoản quản trị hệ thống {@code admin} ({@code systemaccount}).
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
        if (IdNumbers.SYSTEM_ADMIN_LOGIN.equals(user.getLogin())) {
            throw new EmployeeRuleException(
                "Tài khoản quản trị hệ thống \"admin\" giữ nguyên tên đăng nhập, không gắn vào hồ sơ nhân viên",
                "systemaccount"
            );
        }
        if (employee.getStatus() == EmploymentStatus.DA_NGHI && user.isActivated()) {
            // Hồ sơ đã nghỉ chỉ được gắn tài khoản đang khóa (và tài khoản đó không mở lại được khi hồ sơ còn ở trạng thái nghỉ).
            throw new EmployeeRuleException(
                "Nhân viên đã nghỉ việc — chỉ gắn được tài khoản đang khóa; khóa tài khoản trước hoặc dùng Hoàn tác nghỉ việc",
                "employeeterminated"
            );
        }
        Optional<Employee> holder = employeeRepository.findOneByUserId(user.getId());
        if (holder.isPresent()) {
            throw new EmployeeRuleException("Tài khoản đã gắn với hồ sơ nhân viên khác", "useralreadylinked");
        }
        if (employee.getUser() != null) {
            throw new EmployeeRuleException("Hồ sơ đã có tài khoản; bỏ gắn trước khi gắn tài khoản khác", "employeehasaccount");
        }
        String login = IdNumbers.toLogin(employee.getIdNumber());
        rename(user, login, employeeId);
        employee.setUser(user);
        saveLink(employee);
        LOG.debug("Linked account #{} to Employee {}", user.getId(), employeeId);
        return Optional.of(toDto(user));
    }

    /**
     * Đồng bộ tên đăng nhập khi sửa số CCCD trên hồ sơ có tài khoản gắn (gọi trước khi sửa hồ sơ, trong cùng transaction).
     * Không làm gì khi hồ sơ chưa có tài khoản, số CCCD không đổi, hoặc tài khoản là quản trị hệ thống {@code admin}.
     *
     * @param employeeId hồ sơ đang sửa
     * @param newIdNumber số CCCD mới (đã bỏ khoảng trắng hai đầu; null = xóa)
     * @throws EmployeeRuleException {@code idnumberrequired}/{@code idnumberinvalid} khi số mới không dùng làm tên đăng nhập
     *     được, {@code loginexists} khi số mới là tên đăng nhập của tài khoản khác
     */
    public void syncLoginWithIdNumber(Long employeeId, String newIdNumber) {
        if (employeeId == null) {
            return;
        }
        User user = employeeRepository.findById(employeeId).map(Employee::getUser).orElse(null);
        if (user == null || IdNumbers.SYSTEM_ADMIN_LOGIN.equals(user.getLogin())) {
            return;
        }
        if (newIdNumber == null) {
            throw new EmployeeRuleException(
                "Hồ sơ có tài khoản đăng nhập (tên đăng nhập là số CCCD) — không để trống số CCCD",
                "idnumberrequired"
            );
        }
        rename(user, IdNumbers.toLogin(newIdNumber), employeeId);
    }

    /** Đổi tên đăng nhập (nếu khác); phiên cũ hết hiệu lực. */
    private void rename(User user, String login, Long employeeId) {
        if (login.equals(user.getLogin())) {
            return;
        }
        checkLoginFree(login, user.getId());
        user.setLogin(login);
        user.markCredentialsChanged();
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw loginExists();
        }
        // Không ghi số CCCD vào log.
        LOG.info("Đổi tên đăng nhập của tài khoản #{} theo số CCCD của hồ sơ #{}", user.getId(), employeeId);
    }

    private void checkLoginFree(String login, Long ownerId) {
        userRepository
            .findOneByLogin(login)
            .filter(u -> !u.getId().equals(ownerId))
            .ifPresent(u -> {
                throw loginExists();
            });
    }

    private static EmployeeRuleException loginExists() {
        return new EmployeeRuleException("Số CCCD này đã là tên đăng nhập của một tài khoản khác", "loginexists");
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
                    LOG.debug("Unlinked account #{} from Employee {}", user.getId(), employeeId);
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

    /** Tài khoản đang đăng nhập đã gắn hồ sơ nhân viên (mọi vai trò; hiện mục "Hồ sơ của tôi"). */
    @Transactional(readOnly = true)
    public boolean currentUserHasEmployeeProfile() {
        return SecurityUtils.getCurrentUserLogin().flatMap(employeeRepository::findOneByUserLogin).isPresent();
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

package vn.doit.hrm.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.security.RandomUtil;
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
import vn.doit.hrm.service.dto.AdminPasswordDTO;
import vn.doit.hrm.service.dto.AdminUserDTO;
import vn.doit.hrm.service.dto.UserDTO;

/**
 * Service class for managing users.
 */
@Service
@Transactional
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthorityRepository authorityRepository;

    private final EmployeeRepository employeeRepository;

    private final ApplicationProperties applicationProperties;

    public static final String USER_LINKED_MESSAGE = "Tài khoản đang gắn với hồ sơ nhân viên — bỏ gắn trước khi xóa.";

    public UserService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthorityRepository authorityRepository,
        EmployeeRepository employeeRepository,
        ApplicationProperties applicationProperties
    ) {
        this.applicationProperties = applicationProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorityRepository = authorityRepository;
        this.employeeRepository = employeeRepository;
    }

    public Optional<User> completePasswordReset(String newPassword, String key) {
        LOG.debug("Reset user password for reset key {}", key);
        return userRepository
            .findOneByResetKey(key)
            .filter(user -> user.getResetDate().isAfter(Instant.now().minus(1, ChronoUnit.DAYS)))
            .map(user -> {
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setResetKey(null);
                user.setResetDate(null);
                user.setMustChangePassword(false);
                user.markCredentialsChanged();
                return user;
            });
    }

    public Optional<User> requestPasswordReset(String mail) {
        return userRepository
            .findOneByEmailIgnoreCase(mail)
            .filter(User::isActivated)
            .map(user -> {
                user.setResetKey(RandomUtil.generateResetKey());
                user.setResetDate(Instant.now());
                return user;
            });
    }

    /**
     * Quản trị tạo tài khoản: tên đăng nhập = số CCCD ({@code userDTO.login} đã được đặt bằng số CCCD chuẩn hóa), mật khẩu ban
     * đầu do Quản trị nhập (để trống = mật khẩu mặc định), phải đổi mật khẩu ở lần đăng nhập đầu. Số CCCD trùng hồ sơ nhân viên chưa có tài khoản thì gắn luôn vào hồ sơ đó;
     * không có hồ sơ thì tài khoản không gắn hồ sơ (vd quản trị viên ngoài). Email không bắt buộc.
     *
     * @throws EmployeeRuleException {@code employeehasaccount} (hồ sơ có CCCD đó đã có tài khoản khác),
     *     {@code employeeterminated} (hồ sơ đã nghỉ việc), {@code idnumberambiguous} (nhiều hồ sơ đang làm cùng CCCD)
     */
    public User createUser(AdminUserDTO userDTO) {
        String login = userDTO.getLogin().toLowerCase();
        Optional<Employee> profile = profileToLink(login);
        User user = new User();
        user.setLogin(login);
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        if ((user.getFirstName() == null || user.getFirstName().isBlank()) && profile.isPresent()) {
            String name = profile.orElseThrow().getFullName();
            user.setFirstName(name == null ? null : name.trim().length() <= 50 ? name.trim() : name.trim().substring(0, 50));
        }
        String email = normalizeEmail(userDTO.getEmail());
        if (email != null) {
            user.setEmail(email);
        }
        user.setImageUrl(userDTO.getImageUrl());
        if (userDTO.getLangKey() == null) {
            user.setLangKey(Constants.DEFAULT_LANGUAGE); // default language
        } else {
            user.setLangKey(userDTO.getLangKey());
        }
        boolean custom = userDTO.getPassword() != null && !userDTO.getPassword().isEmpty();
        // Mật khẩu ban đầu do Quản trị nhập, hoặc mật khẩu mặc định; người dùng vẫn phải đổi ở lần đăng nhập đầu.
        user.setPassword(passwordEncoder.encode(custom ? userDTO.getPassword() : applicationProperties.getAccount().getDefaultPassword()));
        user.setMustChangePassword(true);
        user.setActivated(true);
        if (userDTO.getAuthorities() != null) {
            Set<Authority> authorities = userDTO
                .getAuthorities()
                .stream()
                .map(authorityRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
            user.setAuthorities(authorities);
        }
        try {
            userRepository.saveAndFlush(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Hai yêu cầu tạo cùng số CCCD đồng thời: ràng buộc duy nhất của login là chốt cuối.
            throw new EmployeeRuleException("Tên đăng nhập (số CCCD) đã được dùng", "userexists");
        }
        profile.ifPresent(found -> {
            Employee employee = employeeRepository.findByIdForUpdate(found.getId()).orElseThrow();
            // Kiểm tra lại sau khi khóa dòng: hồ sơ có thể vừa bị sửa (cho nghỉ, đổi CCCD, gắn tài khoản khác).
            if (employee.getUser() != null) {
                throw new EmployeeRuleException("Hồ sơ nhân viên có số CCCD này đã có tài khoản", "employeehasaccount");
            }
            if (employee.getStatus() == EmploymentStatus.DA_NGHI) {
                throw new EmployeeRuleException(
                    "Nhân viên có số CCCD này đã nghỉ việc — không tạo tài khoản đăng nhập mới",
                    "employeeterminated"
                );
            }
            if (!login.equals(vn.doit.hrm.service.IdNumbers.trim(employee.getIdNumber()))) {
                throw new EmployeeRuleException("Số CCCD của hồ sơ vừa thay đổi; thử tạo lại tài khoản", "idnumberchanged");
            }
            employee.setUser(user);
            try {
                employeeRepository.saveAndFlush(employee);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                throw new EmployeeRuleException("Hồ sơ nhân viên có số CCCD này đã có tài khoản", "employeehasaccount");
            }
            LOG.debug("Linked new User #{} to Employee {}", user.getId(), employee.getId());
        });
        LOG.debug("Created Information for User #{}", user.getId());
        return user;
    }

    /** Hồ sơ nhân viên để gắn tài khoản mới có tên đăng nhập (số CCCD) {@code idNumber}; rỗng nếu không có hồ sơ nào. */
    private Optional<Employee> profileToLink(String idNumber) {
        // Hồ sơ đã lưu trữ không được tự gắn tài khoản mới.
        List<Employee> matches = employeeRepository.findAllByTrimmedIdNumber(idNumber).stream().filter(e -> !e.isArchived()).toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.stream().anyMatch(e -> e.getUser() != null)) {
            throw new EmployeeRuleException(
                "Hồ sơ nhân viên có số CCCD này đã có tài khoản; dùng tài khoản đó hoặc bỏ gắn trên hồ sơ trước",
                "employeehasaccount"
            );
        }
        List<Employee> working = matches.stream().filter(e -> e.getStatus() != EmploymentStatus.DA_NGHI).toList();
        if (working.isEmpty()) {
            throw new EmployeeRuleException("Nhân viên có số CCCD này đã nghỉ việc — không tạo tài khoản đăng nhập mới", "employeeterminated");
        }
        if (working.size() > 1) {
            throw new EmployeeRuleException(
                "Có nhiều hồ sơ nhân viên đang làm việc cùng số CCCD này; tạo tài khoản từ tab Tài khoản của đúng hồ sơ",
                "idnumberambiguous"
            );
        }
        return Optional.of(working.get(0));
    }

    /** Email để trống coi như không có (cột email cho phép null, chỉ duy nhất khi có giá trị). */
    public static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    /** Email đã thuộc tài khoản khác; email trống thì không bao giờ trùng. */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        String normalized = normalizeEmail(email);
        return normalized == null ? Optional.empty() : userRepository.findOneByEmailIgnoreCase(normalized);
    }

    /**
     * Quản trị sửa tài khoản.
     * <ul>
     *   <li>Không được tự hạ quyền ADMIN hay tự khóa mình ({@code selfadmin}); không được khóa/hạ quyền ADMIN đang hoạt động cuối
     *   cùng ({@code lastadmin}).</li>
     *   <li>Không mở lại tài khoản gắn hồ sơ nhân viên đã nghỉ ({@code employeeterminated}) – dùng Hoàn tác nghỉ việc.</li>
     *   <li>Đổi vai trò hoặc khóa/mở thì JWT đã phát hành trước đó hết hiệu lực.</li>
     * </ul>
     *
     * @param userDTO user to update.
     * @return updated user.
     * @throws EmployeeRuleException khi vi phạm các quy tắc trên
     */
    public Optional<AdminUserDTO> updateUser(AdminUserDTO userDTO) {
        return Optional.of(userRepository.findById(userDTO.getId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(user -> {
                Set<Authority> requested = (userDTO.getAuthorities() == null ? Set.<String>of() : userDTO.getAuthorities())
                    .stream()
                    .map(authorityRepository::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
                checkAdminUpdate(user, userDTO.isActivated(), hasAdmin(requested));
                String newLogin = userDTO.getLogin().toLowerCase();
                boolean loginChanged = !newLogin.equals(user.getLogin());
                if (loginChanged && employeeRepository.findOneByUserId(user.getId()).isPresent()) {
                    throw new EmployeeRuleException(
                        "Tên đăng nhập của tài khoản gắn hồ sơ là số CCCD — sửa số CCCD trên hồ sơ nhân viên để đổi",
                        "loginfromidnumber"
                    );
                }
                Set<String> oldNames = authorityNames(user.getAuthorities());
                boolean credentialsChanged =
                    loginChanged || user.isActivated() != userDTO.isActivated() || !oldNames.equals(authorityNames(requested));
                user.setLogin(newLogin);
                user.setFirstName(userDTO.getFirstName());
                user.setLastName(userDTO.getLastName());
                user.setEmail(normalizeEmail(userDTO.getEmail()));
                user.setImageUrl(userDTO.getImageUrl());
                user.setActivated(userDTO.isActivated());
                user.setLangKey(userDTO.getLangKey());
                Set<Authority> managedAuthorities = user.getAuthorities();
                managedAuthorities.clear();
                managedAuthorities.addAll(requested);
                if (credentialsChanged) {
                    user.markCredentialsChanged();
                }
                userRepository.save(user);
                LOG.debug("Changed Information for User #{}", user.getId());
                return user;
            })
            .map(AdminUserDTO::new);
    }

    private void checkAdminUpdate(User user, boolean activate, boolean keepsAdmin) {
        boolean deactivating = user.isActivated() && !activate;
        boolean wasAdmin = hasAdmin(user.getAuthorities());
        if (isCurrentUser(user) && (deactivating || (wasAdmin && !keepsAdmin))) {
            throw new EmployeeRuleException("Không thể tự khóa hoặc tự bỏ quyền Quản trị của chính mình", "selfadmin");
        }
        if (wasAdmin && user.isActivated() && (deactivating || !keepsAdmin)) {
            checkNotLastAdmin(user);
        }
        if (!user.isActivated() && activate) {
            employeeRepository
                .findOneByUserId(user.getId())
                .ifPresent(e -> {
                    if (e.isArchived()) {
                        throw new EmployeeRuleException(
                            "Tài khoản gắn với hồ sơ đã lưu trữ — khôi phục hồ sơ trước khi mở lại tài khoản",
                            "accountemployeearchived"
                        );
                    }
                    if (e.getStatus() == EmploymentStatus.DA_NGHI) {
                        throw new EmployeeRuleException(
                            "Tài khoản gắn với nhân viên đã nghỉ việc — dùng Hoàn tác nghỉ việc trên hồ sơ để mở lại",
                            "employeeterminated"
                        );
                    }
                });
        }
    }

    private void checkNotLastAdmin(User user) {
        if (userRepository.countActivatedWithAuthority(AuthoritiesConstants.ADMIN) <= 1) {
            throw new EmployeeRuleException(
                "Tài khoản " + user.getLogin() + " là tài khoản quản trị cuối cùng đang hoạt động; tạo hoặc mở một tài khoản quản trị khác trước",
                "lastadmin"
            );
        }
    }

    private static boolean isCurrentUser(User user) {
        return SecurityUtils.getCurrentUserLogin().map(login -> login.equalsIgnoreCase(user.getLogin())).orElse(false);
    }

    private static boolean hasAdmin(Set<Authority> authorities) {
        return authorities.stream().anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName()));
    }

    private static Set<String> authorityNames(Set<Authority> authorities) {
        return authorities.stream().map(Authority::getName).collect(Collectors.toSet());
    }

    /**
     * Xóa tài khoản. Tài khoản đang gắn hồ sơ nhân viên thì không xóa (không tự bỏ gắn); không tự xóa mình; không xóa ADMIN đang
     * hoạt động cuối cùng.
     *
     * @throws EmployeeRuleException {@code userlinked}, {@code selfadmin} hoặc {@code lastadmin}
     */
    public void deleteUser(String login) {
        userRepository.findOneByLogin(login).ifPresent(user -> {
            if (employeeRepository.findOneByUserId(user.getId()).isPresent()) {
                throw new EmployeeRuleException(USER_LINKED_MESSAGE, "userlinked");
            }
            if (isCurrentUser(user)) {
                throw new EmployeeRuleException("Không thể tự xóa tài khoản của chính mình", "selfadmin");
            }
            if (hasAdmin(user.getAuthorities()) && user.isActivated()) {
                checkNotLastAdmin(user);
            }
            userRepository.delete(user);
            LOG.debug("Deleted User #{}", user.getId());
        });
    }

    /**
     * Update basic information (first name, last name, email, language) for the current user.
     *
     * @param firstName first name of user.
     * @param lastName  last name of user.
     * @param email     email of user.
     * @param langKey   language key.
     * @param imageUrl  image URL of user.
     */
    public void updateUser(String firstName, String lastName, String email, String langKey, String imageUrl) {
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .ifPresent(user -> {
                user.setFirstName(firstName);
                user.setLastName(lastName);
                if (email != null) {
                    user.setEmail(normalizeEmail(email));
                }
                user.setLangKey(langKey);
                user.setImageUrl(imageUrl);
                userRepository.save(user);
                LOG.debug("Changed Information for User #{}", user.getId());
            });
    }

    /**
     * Quản trị đặt mật khẩu cho tài khoản {@code login}; mật khẩu chỉ được lưu dạng băm, không ghi log.
     *
     * @return false nếu không có tài khoản
     */
    @Transactional
    public boolean setPasswordByAdmin(String login, AdminPasswordDTO password) {
        return userRepository
            .findOneByLogin(login)
            .map(user -> {
                user.setPassword(passwordEncoder.encode(password.newPassword()));
                // Quản trị biết mật khẩu này: người dùng phải tự đổi ở lần đăng nhập kế tiếp.
                user.setMustChangePassword(true);
                user.markCredentialsChanged();
                // Link đặt lại mật khẩu cũ (nếu có) hết hiệu lực: mật khẩu do Quản trị đặt là mật khẩu duy nhất còn dùng được.
                user.setResetKey(null);
                user.setResetDate(null);
                LOG.debug("Admin set password for User #{}", user.getId());
                return true;
            })
            .orElse(false);
    }

    /**
     * Quản trị đặt lại mật khẩu mặc định: người dùng phải đổi mật khẩu ở lần đăng nhập kế tiếp; phiên cũ hết hiệu lực.
     *
     * @return false nếu không có tài khoản
     */
    @Transactional
    public boolean resetToDefaultPassword(String login) {
        return userRepository
            .findOneByLogin(login)
            .map(user -> {
                user.setPassword(passwordEncoder.encode(applicationProperties.getAccount().getDefaultPassword()));
                user.setMustChangePassword(true);
                user.markCredentialsChanged();
                user.setResetKey(null);
                user.setResetDate(null);
                LOG.debug("Admin reset User #{} to the default password", user.getId());
                return true;
            })
            .orElse(false);
    }

    /** {@code password} là mật khẩu mặc định (không được dùng làm mật khẩu tự đặt). */
    public boolean isDefaultPassword(String password) {
        return applicationProperties.getAccount().getDefaultPassword().equals(password);
    }

    @Transactional
    public void changePassword(String currentClearTextPassword, String newPassword) {
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .ifPresent(user -> {
                String currentEncryptedPassword = user.getPassword();
                if (!passwordEncoder.matches(currentClearTextPassword, currentEncryptedPassword)) {
                    throw new InvalidPasswordException();
                }
                // Mật khẩu mới phải khác mật khẩu đang dùng (vd mật khẩu ban đầu người tạo đã biết).
                if (passwordEncoder.matches(newPassword, currentEncryptedPassword)) {
                    throw new EmployeeRuleException("Mật khẩu mới phải khác mật khẩu đang dùng", "samepassword");
                }
                String encryptedPassword = passwordEncoder.encode(newPassword);
                user.setPassword(encryptedPassword);
                user.setMustChangePassword(false);
                user.markCredentialsChanged();
                LOG.debug("Changed password for User #{}", user.getId());
            });
    }

    @Transactional(readOnly = true)
    public Page<AdminUserDTO> getAllManagedUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(AdminUserDTO::new);
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getAllPublicUsers(Pageable pageable) {
        return userRepository.findAllByIdNotNullAndActivatedIsTrue(pageable).map(UserDTO::new);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthoritiesByLogin(String login) {
        return userRepository.findOneWithAuthoritiesByLogin(login);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthorities() {
        return SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneWithAuthoritiesByLogin);
    }

    /**
     * Gets a list of all the authorities.
     * @return a list of all the authorities.
     */
    @Transactional(readOnly = true)
    public List<String> getAuthorities() {
        return authorityRepository.findAll().stream().map(Authority::getName).toList();
    }
}

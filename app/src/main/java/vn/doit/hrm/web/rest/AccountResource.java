package vn.doit.hrm.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.LoginMask;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeAccountService;
import vn.doit.hrm.service.MailService;
import vn.doit.hrm.service.UserService;
import vn.doit.hrm.service.dto.AdminUserDTO;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.PasswordChangeDTO;
import vn.doit.hrm.service.attendance.AttendanceAccess;
import vn.doit.hrm.service.leave.LeaveAccountFlags;
import vn.doit.hrm.web.rest.errors.*;
import vn.doit.hrm.web.rest.vm.KeyAndPasswordVM;
import vn.doit.hrm.web.rest.vm.ManagedUserVM;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
@Validated
public class AccountResource {

    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Account resource request invalid")
    private static class AccountResourceException extends RuntimeException {

        private AccountResourceException(String message) {
            super(message);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final MailService mailService;

    private final PasswordEncoder passwordEncoder;

    private final EmployeeAccountService employeeAccountService;

    private final LeaveAccountFlags leaveAccountFlags;

    private final AttendanceAccess attendanceAccess;

    private final vn.doit.hrm.service.attendance.AttendanceMonthService attendanceMonthService;

    public AccountResource(
        UserRepository userRepository,
        UserService userService,
        MailService mailService,
        PasswordEncoder passwordEncoder,
        EmployeeAccountService employeeAccountService,
        LeaveAccountFlags leaveAccountFlags,
        AttendanceAccess attendanceAccess,
        vn.doit.hrm.service.attendance.AttendanceMonthService attendanceMonthService
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.employeeAccountService = employeeAccountService;
        this.leaveAccountFlags = leaveAccountFlags;
        this.attendanceAccess = attendanceAccess;
        this.attendanceMonthService = attendanceMonthService;
    }

    /**
     * {@code GET  /account} : get the current user.
     *
     * @return the current user.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user couldn't be returned.
     */
    @GetMapping("/account")
    public AdminUserDTO getAccount() {
        LOG.debug("REST request to get account");
        AdminUserDTO account = userService
            .getUserWithAuthorities()
            .map(AdminUserDTO::new)
            .orElseThrow(() -> new AccountResourceException("User could not be found"));
        account.setHasEmployeeProfile(employeeAccountService.currentUserHasEmployeeProfile());
        LeaveAccountFlags.Flags flags = leaveAccountFlags.forCurrentUser();
        account.setLeaveApplicable(flags.leaveApplicable());
        account.setIsApprover(flags.approver());
        account.setSeesLeaveCalendar(flags.seesLeaveCalendar());
        account.setEntersAttendance(attendanceAccess.entersAnyone(account.getLogin()));
        account.setViewsAttendanceBoard(attendanceMonthService.viewsBoard(account.getLogin()));
        account.setOwnsAttendance(attendanceMonthService.ownsBoard(account.getLogin()));
        // Người tạo/sửa tài khoản là login (số CCCD) của HR/Quản trị: che với vai trò không được xem CCCD.
        account.setCreatedBy(LoginMask.forViewerIfDigits(account.getCreatedBy()));
        account.setLastModifiedBy(LoginMask.forViewerIfDigits(account.getLastModifiedBy()));
        return account;
    }

    /**
     * {@code GET  /account/employee} : hồ sơ nhân viên gắn với tài khoản đang đăng nhập (không có trường nhạy cảm;
     * xem trường nhạy cảm của mình qua {@code /api/employees/{id}/sensitive}).
     *
     * @return {@code 200} kèm hồ sơ, hoặc {@code 404} nếu tài khoản chưa gắn hồ sơ nào.
     */
    @GetMapping("/account/employee")
    public ResponseEntity<EmployeeDTO> getAccountEmployee() {
        LOG.debug("REST request to get own employee profile");
        return ResponseEntity.of(employeeAccountService.findCurrentUserEmployee());
    }

    /**
     * {@code POST  /account} : update the current user information.
     *
     * @param userDTO the current user information.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user login wasn't found.
     */
    @PostMapping("/account")
    public void saveAccount(@Valid @RequestBody AdminUserDTO userDTO) {
        LOG.debug("REST request to save account");
        String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow(() ->
            new AccountResourceException("Current user login not found")
        );
        Optional<User> existingUser = userService.findByEmail(userDTO.getEmail());
        if (existingUser.isPresent() && !existingUser.orElseThrow().getLogin().equalsIgnoreCase(userLogin)) {
            throw new EmailAlreadyUsedException();
        }
        Optional<User> user = userRepository.findOneByLogin(userLogin);
        if (user.isEmpty()) {
            throw new AccountResourceException("User could not be found");
        }
        userService.updateUser(
            userDTO.getFirstName(),
            userDTO.getLastName(),
            userDTO.getEmail(),
            userDTO.getLangKey(),
            userDTO.getImageUrl()
        );
    }

    /**
     * {@code POST  /account/change-password} : changes the current user's password.
     *
     * @param passwordChangeDto current and new password.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the new password is incorrect.
     */
    @PostMapping(path = "/account/change-password")
    public void changePassword(@RequestBody PasswordChangeDTO passwordChangeDto) {
        LOG.debug("REST request to change password");
        if (isPasswordLengthInvalid(passwordChangeDto.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        rejectDefaultPassword(passwordChangeDto.getNewPassword());
        try {
            userService.changePassword(passwordChangeDto.getCurrentPassword(), passwordChangeDto.getNewPassword());
        } catch (vn.doit.hrm.service.EmployeeRuleException e) {
            BadRequestAlertException ex = new BadRequestAlertException(e.getMessage(), "account", e.getErrorKey());
            ex.getBody().setDetail(e.getMessage());
            throw ex;
        }
    }

    /**
     * {@code POST   /account/reset-password/init} : Send an email to reset the password of the user.
     *
     * @param mail the mail of the user.
     */
    @PostMapping(path = "/account/reset-password/init")
    public void requestPasswordReset(@RequestBody @Email @Size(min = 5, max = 254) String mail) {
        LOG.debug("REST request to request password reset");
        Optional<User> user = userService.requestPasswordReset(mail);
        if (user.isPresent()) {
            mailService.sendPasswordResetMail(user.orElseThrow());
        } else {
            // Pretend the request has been successful to prevent checking which emails really exist
            // but log that an invalid attempt has been made
            LOG.warn("Password reset requested for non existing mail");
        }
    }

    /**
     * {@code POST   /account/reset-password/finish} : Finish to reset the password of the user.
     *
     * @param keyAndPassword the generated key and the new password.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the password is incorrect.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the password could not be reset.
     */
    @PostMapping(path = "/account/reset-password/finish")
    public void finishPasswordReset(@RequestBody KeyAndPasswordVM keyAndPassword) {
        if (isPasswordLengthInvalid(keyAndPassword.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        rejectDefaultPassword(keyAndPassword.getNewPassword());
        Optional<User> user = userService.completePasswordReset(keyAndPassword.getNewPassword(), keyAndPassword.getKey());

        if (user.isEmpty()) {
            // Dummy hash to prevent reset-key enumeration via response-time timing attack:
            // mirrors the bcrypt cost of a successful path so both branches take equal time.
            passwordEncoder.encode(keyAndPassword.getNewPassword());
            throw new AccountResourceException("No user was found for this reset key");
        }
    }

    /** Mật khẩu tự đặt không được là mật khẩu mặc định (ai cũng biết) → 400 {@code defaultpassword}. */
    private void rejectDefaultPassword(String newPassword) {
        if (userService.isDefaultPassword(newPassword)) {
            BadRequestAlertException ex = new BadRequestAlertException(
                "Mật khẩu mới không được trùng mật khẩu mặc định",
                "account",
                "defaultpassword"
            );
            ex.getBody().setDetail("Mật khẩu mới không được trùng mật khẩu mặc định");
            throw ex;
        }
    }

    private static boolean isPasswordLengthInvalid(String password) {
        return (
            StringUtils.isEmpty(password) ||
            password.length() < ManagedUserVM.PASSWORD_MIN_LENGTH ||
            password.length() > ManagedUserVM.PASSWORD_MAX_LENGTH
        );
    }
}

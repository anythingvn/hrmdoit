package vn.doit.hrm.service.reminder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.reminder.ReminderDigest;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.reminder.ReminderSnapshotService.Loaded;
import vn.doit.hrm.service.reminder.ReminderSnapshotService.Manager;
import vn.doit.hrm.service.reminder.ReminderSnapshotService.Ref;

/**
 * Người nhận nhắc việc: tài khoản HR, Kế toán, Quản trị nhận mọi mục; quản lý trực tiếp nhận mục về nhân viên của mình.
 * Email lấy từ tài khoản, không có thì từ hồ sơ gắn với tài khoản; địa chỉ {@code @localhost} coi như chưa có.
 */
@Service
public class ReminderRecipients {

    private static final List<String> STAFF_ROLES = List.of(AuthoritiesConstants.HR, AuthoritiesConstants.KE_TOAN, AuthoritiesConstants.ADMIN);

    /** Người nhận của một lần chạy, kèm danh sách người không có email để Quản trị bổ sung. */
    public record Resolved(ReminderDigest.RecipientResolver resolver, List<String> missing) {}

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public ReminderRecipients(UserRepository userRepository, EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public Resolved resolve(Loaded data) {
        List<String> missing = new ArrayList<>();
        Set<String> staff = new LinkedHashSet<>();
        for (User u : userRepository.findActiveWithAnyAuthority(STAFF_ROLES)) {
            accountEmail(u).ifPresentOrElse(staff::add, () -> missing.add("Tài khoản " + u.getLogin()));
        }
        Set<Long> reported = new LinkedHashSet<>();
        for (Ref ref : data.refs().values()) {
            Manager m = ref.manager();
            if (m != null && usable(m.email()).isEmpty() && reported.add(m.id())) {
                missing.add("Quản lý " + m.name() + " (" + m.code() + ")");
            }
        }
        List<String> staffEmails = List.copyOf(staff);
        ReminderDigest.RecipientResolver resolver = (role, code) ->
            switch (role) {
                case HR, DIRECTOR -> staffEmails;
                case MANAGER -> Optional.ofNullable(data.refs().get(code))
                    .map(Ref::manager)
                    .flatMap(m -> usable(m.email()))
                    .map(List::of)
                    .orElse(List.of());
                case EMPLOYEE -> List.of();
            };
        return new Resolved(resolver, missing);
    }

    /** Email nhận của một tài khoản (để gửi thử). */
    @Transactional(readOnly = true)
    public Optional<String> emailOf(String login) {
        return userRepository.findOneByLogin(login).flatMap(this::accountEmail);
    }

    private Optional<String> accountEmail(User u) {
        return usable(u.getEmail()).or(() -> employeeRepository.findOneByUserId(u.getId()).flatMap(e -> usable(e.getEmail())));
    }

    static Optional<String> usable(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String e = email.trim().toLowerCase(Locale.ROOT);
        int at = e.lastIndexOf('@');
        return at > 0 && at < e.length() - 1 && !e.substring(at + 1).equals("localhost") ? Optional.of(e) : Optional.empty();
    }
}

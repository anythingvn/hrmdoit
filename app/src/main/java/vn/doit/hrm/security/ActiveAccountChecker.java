package vn.doit.hrm.security;

import org.springframework.stereotype.Component;
import vn.doit.hrm.repository.UserRepository;

/**
 * Kiểm tra tài khoản còn hoạt động ở mỗi request mang JWT: tài khoản bị khóa (vd nhân viên nghỉ việc) hoặc không còn tồn tại
 * thì token cũ bị từ chối ngay, không chờ hết hạn. Không cache: một truy vấn theo khóa duy nhất mỗi request, luôn đúng ngay
 * sau khi khóa/mở (cho nghỉ, hoàn tác, quản trị sửa tài khoản).
 */
@Component
public class ActiveAccountChecker {

    private final UserRepository userRepository;

    public ActiveAccountChecker(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isActive(String login) {
        if (login == null || login.isBlank()) {
            return false;
        }
        return userRepository.findActivatedByLogin(login).orElse(false);
    }
}

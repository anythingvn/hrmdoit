package vn.doit.hrm.security;

import java.time.Instant;
import org.springframework.stereotype.Component;
import vn.doit.hrm.repository.UserLoginState;
import vn.doit.hrm.repository.UserRepository;

/**
 * Kiểm tra JWT còn dùng được ở mỗi request: tài khoản bị khóa (vd nhân viên nghỉ việc) hoặc không còn tồn tại thì token cũ
 * bị từ chối ngay, không chờ hết hạn; token phát hành trước lần đổi mật khẩu/vai trò/khóa-mở gần nhất
 * ({@code credentialsChangedAt}) cũng bị từ chối. Không cache: một truy vấn theo khóa duy nhất mỗi request.
 * <p>
 * {@code iat} của JWT tính theo giây nên so sánh theo giây: token phát hành trong cùng giây với lần đổi vẫn dùng được (để đăng
 * nhập lại ngay sau khi đổi mật khẩu không bị từ chối).
 */
@Component
public class ActiveAccountChecker {

    private final UserRepository userRepository;

    public ActiveAccountChecker(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Tài khoản đang mở và token phát hành ({@code issuedAt}) không trước lần đổi thông tin đăng nhập gần nhất. */
    public boolean isTokenValid(String login, Instant issuedAt) {
        if (login == null || login.isBlank()) {
            return false;
        }
        UserLoginState state = userRepository.findLoginStateByLogin(login).orElse(null);
        if (state == null || !state.activated()) {
            return false;
        }
        if (state.credentialsChangedAt() == null) {
            return true;
        }
        return issuedAt != null && issuedAt.getEpochSecond() >= state.credentialsChangedAt().getEpochSecond();
    }
}

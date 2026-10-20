package vn.doit.hrm.web.rest.errors;

import java.io.Serial;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;
import vn.doit.hrm.security.LoginAttemptService;

/** 429: đăng nhập sai quá nhiều lần; kèm {@code Retry-After} (giây). */
@SuppressWarnings("java:S110")
public class TooManyLoginAttemptsException extends ErrorResponseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TooManyLoginAttemptsException(String detail, Duration retryAfter) {
        super(
            HttpStatus.TOO_MANY_REQUESTS,
            ProblemDetailWithCauseBuilder.instance()
                .withStatus(HttpStatus.TOO_MANY_REQUESTS.value())
                .withTitle("Đăng nhập sai quá nhiều lần")
                .withDetail(detail)
                .withProperty("message", "error.toomanyloginattempts")
                .build(),
            null
        );
        getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfter.toSeconds())));
    }

    private static long minutes(Duration d) {
        return Math.max(1, (d.toSeconds() + 59) / 60);
    }

    /** Câu báo tiếng Việt theo loại chặn (theo tài khoản, theo IP, hoặc đang có nhiều lần thử song song). */
    public static TooManyLoginAttemptsException of(LoginAttemptService.Attempt attempt) {
        Duration wait = attempt.blockedFor();
        long window = LoginAttemptService.WINDOW.toMinutes();
        String detail;
        if (attempt.key() == null) {
            detail =
                "Có quá nhiều lần đăng nhập sai từ địa chỉ mạng này trong " +
                window +
                " phút. Vui lòng thử lại sau " +
                minutes(wait) +
                " phút.";
        } else if (wait.toSeconds() <= 1) {
            detail = "Đang xử lý các lần đăng nhập trước của tài khoản này. Vui lòng thử lại sau giây lát.";
        } else {
            detail =
                "Bạn đã nhập sai mật khẩu " +
                LoginAttemptService.MAX_FAILURES +
                " lần trong " +
                window +
                " phút. Vui lòng thử lại sau " +
                minutes(wait) +
                " phút hoặc nhờ Quản trị mở khóa.";
        }
        return new TooManyLoginAttemptsException(detail, wait);
    }
}

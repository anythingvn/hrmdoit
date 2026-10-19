package vn.doit.hrm.web.rest.errors;

import java.io.Serial;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;

/** 429: đăng nhập sai quá nhiều lần; kèm {@code Retry-After} (giây). */
@SuppressWarnings("java:S110")
public class TooManyLoginAttemptsException extends ErrorResponseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TooManyLoginAttemptsException(int maxFailures, Duration window, Duration retryAfter) {
        super(
            HttpStatus.TOO_MANY_REQUESTS,
            ProblemDetailWithCauseBuilder.instance()
                .withStatus(HttpStatus.TOO_MANY_REQUESTS.value())
                .withTitle("Đăng nhập sai quá nhiều lần")
                .withDetail(
                    "Bạn đã nhập sai mật khẩu " +
                        maxFailures +
                        " lần trong " +
                        window.toMinutes() +
                        " phút. Vui lòng thử lại sau " +
                        Math.max(1, (retryAfter.toSeconds() + 59) / 60) +
                        " phút."
                )
                .withProperty("message", "error.toomanyloginattempts")
                .build(),
            null
        );
        getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfter.toSeconds())));
    }
}

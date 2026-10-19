package vn.doit.hrm.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import vn.doit.hrm.management.SecurityMetersService;
import vn.doit.hrm.security.ActiveAccountChecker;

/** Kiểm tra tài khoản bị khóa ở JWT là bắt buộc: thiếu bean thì không khởi động (trừ khi bật cờ cho test cắt lát). */
class SecurityJwtConfigurationTest {

    private static final String KEY =
        "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8";

    private static SecurityJwtConfiguration config(boolean optional) {
        SecurityJwtConfiguration c = new SecurityJwtConfiguration();
        ReflectionTestUtils.setField(c, "jwtKey", KEY);
        ReflectionTestUtils.setField(c, "accountCheckOptional", optional);
        return c;
    }

    @Test
    void missingAccountCheckerFailsStartup() {
        var empty = new StaticListableBeanFactory().getBeanProvider(ActiveAccountChecker.class);
        assertThatThrownBy(() -> config(false).jwtDecoder(new SecurityMetersService(new SimpleMeterRegistry()), empty))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ActiveAccountChecker");
    }

    @Test
    void missingAccountCheckerAllowedOnlyWhenFlagged() {
        var empty = new StaticListableBeanFactory().getBeanProvider(ActiveAccountChecker.class);
        assertThatNoException().isThrownBy(() -> config(true).jwtDecoder(new SecurityMetersService(new SimpleMeterRegistry()), empty));
    }
}

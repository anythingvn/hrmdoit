package vn.doit.hrm.security.jwt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import tech.jhipster.config.JHipsterProperties;
import vn.doit.hrm.config.SecurityConfiguration;
import vn.doit.hrm.config.SecurityJwtConfiguration;
import vn.doit.hrm.config.WebConfigurer;
import vn.doit.hrm.management.SecurityMetersService;
import vn.doit.hrm.web.rest.AuthenticateController;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    properties = {
        "jhipster.security.authentication.jwt.base64-secret=fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8",
        "jhipster.security.authentication.jwt.token-validity-in-seconds=60000",
        // Cắt lát không có CSDL nên không có ActiveAccountChecker; ứng dụng thật bắt buộc phải có.
        "hrm.security.jwt.account-check-optional=true",
    },
    classes = {
        JHipsterProperties.class,
        WebConfigurer.class,
        SecurityConfiguration.class,
        SecurityJwtConfiguration.class,
        SecurityMetersService.class,
        AuthenticateController.class,
        vn.doit.hrm.security.LoginAttemptService.class,
        vn.doit.hrm.config.ClockConfiguration.class,
        JwtAuthenticationTestUtils.class,
    }
)
public @interface AuthenticationIntegrationTest {}

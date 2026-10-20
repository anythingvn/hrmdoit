package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.LoginAttemptService;
import vn.doit.hrm.security.UserNotActivatedException;
import vn.doit.hrm.web.rest.vm.LoginVM;

/** Đăng nhập: lỗi hệ thống (CSDL mất kết nối) không tính là sai mật khẩu (M10); tài khoản bị khóa vẫn tốn một lần băm (M3). */
class AuthenticateControllerTest {

    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private LoginAttemptService attempts;
    private AuthenticateController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void init() {
        ObjectProvider<UserRepository> repo = mock(ObjectProvider.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(repo.getIfAvailable()).thenReturn(userRepository);
        when(userRepository.findOneByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        attempts = new LoginAttemptService(Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneOffset.UTC), repo);
        AuthenticationManagerBuilder builder = mock(AuthenticationManagerBuilder.class);
        when(builder.getObject()).thenReturn(manager);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhas");
        controller = new AuthenticateController(mock(JwtEncoder.class), builder, attempts, passwordEncoder);
    }

    private static LoginVM login(String user, String password) {
        LoginVM vm = new LoginVM();
        vm.setUsername(user);
        vm.setPassword(password);
        return vm;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("10.0.0.9");
        return r;
    }

    @Test
    void databaseOutageIsNotCountedAsFailure() {
        when(manager.authenticate(any())).thenThrow(
            new InternalAuthenticationServiceException("db down", new DataAccessResourceFailureException("db down"))
        );
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES + 2; i++) {
            assertThatThrownBy(() -> controller.authorize(login("nv-a", "MatKhau123"), request())).isInstanceOf(
                InternalAuthenticationServiceException.class
            );
        }
        assertThat(attempts.tryAcquire("nv-a", "10.0.0.9").allowed()).isTrue();
    }

    @Test
    void lockedAccountCountsAsFailureAndRunsDummyHash() {
        when(manager.authenticate(any())).thenThrow(
            new InternalAuthenticationServiceException("locked", new UserNotActivatedException("User nv-b was not activated"))
        );
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            assertThatThrownBy(() -> controller.authorize(login("nv-b", "MatKhau123"), request())).isInstanceOf(
                BadCredentialsException.class
            );
        }
        verify(passwordEncoder, org.mockito.Mockito.times(LoginAttemptService.MAX_FAILURES)).matches(eq("MatKhau123"), anyString());
        assertThat(attempts.tryAcquire("nv-b", "10.0.0.9").allowed()).isFalse();
    }

    @Test
    void wrongPasswordDoesNotRunExtraHash() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        assertThatThrownBy(() -> controller.authorize(login("nv-c", "Sai12345"), request())).isInstanceOf(BadCredentialsException.class);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}

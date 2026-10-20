package vn.doit.hrm.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;

class LoginMaskTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(String login, String role) {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(login, "x", List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    void masksAllButLastFour() {
        assertThat(LoginMask.mask("079100000001")).isEqualTo("********0001");
        assertThat(LoginMask.mask("123456789")).isEqualTo("*****6789");
        assertThat(LoginMask.mask("abc")).isEqualTo("****");
        assertThat(LoginMask.mask(null)).isNull();
        assertThat(LoginMask.forLog(null)).isEqualTo("****");
    }

    @Test
    void adminAndHrSeeFullLogin() {
        as("hr", AuthoritiesConstants.HR);
        assertThat(LoginMask.forViewer("079100000001")).isEqualTo("079100000001");
        as("ad", AuthoritiesConstants.ADMIN);
        assertThat(LoginMask.forViewer("079100000001")).isEqualTo("079100000001");
    }

    @Test
    void otherRolesSeeMaskedLoginExceptTheirOwn() {
        as("079100000009", AuthoritiesConstants.BGD);
        assertThat(LoginMask.forViewer("079100000001")).isEqualTo("********0001");
        assertThat(LoginMask.forViewer("079100000009")).isEqualTo("079100000009");
        as("kt", AuthoritiesConstants.KE_TOAN);
        assertThat(LoginMask.forViewer("079100000001")).isEqualTo("********0001");
        assertThat(LoginMask.forViewerIfDigits("hr.lan")).isEqualTo("hr.lan");
        assertThat(LoginMask.forViewerIfDigits("079100000001")).isEqualTo("********0001");
    }

    @Test
    void mustChangePasswordAllowsOnlyAccountPasswordAndAuthenticate() {
        assertThat(MustChangePasswordFilter.isBlocked(new MockHttpServletRequest("GET", "/api/account"))).isFalse();
        assertThat(MustChangePasswordFilter.isBlocked(new MockHttpServletRequest("POST", "/api/account/change-password"))).isFalse();
        assertThat(MustChangePasswordFilter.isBlocked(new MockHttpServletRequest("POST", "/api/authenticate"))).isFalse();
        assertThat(MustChangePasswordFilter.isBlocked(new MockHttpServletRequest("GET", "/management/health"))).isFalse();
        assertThat(MustChangePasswordFilter.isBlocked(new MockHttpServletRequest("GET", "/index.html"))).isFalse();
        assertThat(MustChangePasswordFilter.isBlocked(new MockHttpServletRequest("POST", "/api/account"))).isTrue();
        assertThat(MustChangePasswordFilter.isBlocked(new MockHttpServletRequest("GET", "/api/account/employee"))).isTrue();
        assertThat(MustChangePasswordFilter.isBlocked(new MockHttpServletRequest("GET", "/api/employees"))).isTrue();
        assertThat(MustChangePasswordFilter.isBlocked(new MockHttpServletRequest("GET", "/management/env"))).isTrue();
    }
}

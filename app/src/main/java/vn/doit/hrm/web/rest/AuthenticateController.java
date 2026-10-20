package vn.doit.hrm.web.rest;

import static vn.doit.hrm.security.SecurityUtils.AUTHORITIES_CLAIM;
import static vn.doit.hrm.security.SecurityUtils.JWT_ALGORITHM;
import static vn.doit.hrm.security.SecurityUtils.USER_ID_CLAIM;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.DomainUserDetailsService.UserWithId;
import vn.doit.hrm.security.LoginAttemptService;
import vn.doit.hrm.security.LoginMask;
import vn.doit.hrm.security.UserNotActivatedException;
import vn.doit.hrm.web.rest.errors.TooManyLoginAttemptsException;
import vn.doit.hrm.web.rest.vm.LoginVM;

/**
 * Controller to authenticate users.
 */
@RestController
@RequestMapping("/api")
public class AuthenticateController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticateController.class);

    private final JwtEncoder jwtEncoder;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds:0}")
    private long tokenValidityInSeconds;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me:0}")
    private long tokenValidityInSecondsForRememberMe;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final LoginAttemptService loginAttemptService;

    private final PasswordEncoder passwordEncoder;

    /** Băm giả (tạo một lần) để nhánh tài khoản bị khóa tốn thời gian như kiểm tra mật khẩu thật. */
    private volatile String dummyHash;

    public AuthenticateController(
        JwtEncoder jwtEncoder,
        AuthenticationManagerBuilder authenticationManagerBuilder,
        LoginAttemptService loginAttemptService,
        PasswordEncoder passwordEncoder
    ) {
        this.jwtEncoder = jwtEncoder;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.loginAttemptService = loginAttemptService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<JWTToken> authorize(@Valid @RequestBody LoginVM loginVM, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        LoginAttemptService.Attempt attempt = loginAttemptService.tryAcquire(loginVM.getUsername(), ip);
        if (!attempt.allowed()) {
            // Tên đăng nhập là số CCCD: chỉ ghi dạng che.
            LOG.warn("Từ chối đăng nhập '{}' từ {}: sai quá nhiều lần", LoginMask.forLog(loginVM.getUsername()), ip);
            throw TooManyLoginAttemptsException.of(attempt);
        }
        var authenticationToken = new UsernamePasswordAuthenticationToken(loginVM.getUsername(), loginVM.getPassword());

        Authentication authentication;
        try {
            authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            loginAttemptService.recordFailure(attempt);
            throw e;
        } catch (org.springframework.security.core.AuthenticationException e) {
            if (isSystemError(e)) {
                // Lỗi hệ thống (vd mất kết nối CSDL) không phải lần đoán mật khẩu: không tính vào giới hạn đăng nhập sai.
                loginAttemptService.release(attempt);
                throw e;
            }
            loginAttemptService.recordFailure(attempt);
            if (isNotActivated(e)) {
                // Tài khoản bị khóa không kiểm tra mật khẩu: băm giả để thời gian phản hồi giống sai mật khẩu.
                passwordEncoder.matches(loginVM.getPassword() == null ? "" : loginVM.getPassword(), dummyHash());
            }
            // Tài khoản bị khóa (vd đã nghỉ việc) trả đúng như sai mật khẩu, không lộ tài khoản có tồn tại hay không.
            LOG.debug("Đăng nhập bị từ chối: {}", e.getMessage());
            throw new org.springframework.security.authentication.BadCredentialsException("Bad credentials");
        } catch (RuntimeException e) {
            loginAttemptService.release(attempt);
            throw e;
        }
        loginAttemptService.recordSuccess(attempt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = this.createToken(authentication, loginVM.isRememberMe());
        var httpHeaders = new HttpHeaders();
        httpHeaders.setBearerAuth(jwt);
        return new ResponseEntity<>(new JWTToken(jwt), httpHeaders, HttpStatus.OK);
    }

    private static boolean isNotActivated(Throwable e) {
        return e instanceof UserNotActivatedException || e.getCause() instanceof UserNotActivatedException;
    }

    /** Lỗi nội bộ khi tải tài khoản (không phải do tài khoản bị khóa) – vd CSDL không trả lời. */
    private static boolean isSystemError(Throwable e) {
        return e instanceof InternalAuthenticationServiceException && !isNotActivated(e);
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode("khong-phai-mat-khau-that");
            dummyHash = hash;
        }
        return hash;
    }

    /**
     * {@code GET /authenticate} : check if the user is authenticated.
     *
     * @return the {@link ResponseEntity} with status {@code 204 (No Content)},
     * or with status {@code 401 (Unauthorized)} if not authenticated.
     */
    @GetMapping("/authenticate")
    public ResponseEntity<Void> isAuthenticated(Principal principal) {
        LOG.debug("REST request to check if the current user is authenticated");
        return ResponseEntity.status(principal == null ? HttpStatus.UNAUTHORIZED : HttpStatus.NO_CONTENT).build();
    }

    public String createToken(Authentication authentication, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(" "));

        var now = Instant.now();
        Instant validity;
        if (rememberMe) {
            validity = now.plus(this.tokenValidityInSecondsForRememberMe, ChronoUnit.SECONDS);
        } else {
            validity = now.plus(this.tokenValidityInSeconds, ChronoUnit.SECONDS);
        }

        // @formatter:off
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
            .issuedAt(now)
            .expiresAt(validity)
            .subject(authentication.getName())
            .claim(AUTHORITIES_CLAIM, authorities);
        if (authentication.getPrincipal() instanceof UserWithId user) {
            builder.claim(USER_ID_CLAIM, user.getId());
        }

        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, builder.build())).getTokenValue();
    }

    /**
     * Object to return as body in JWT Authentication.
     */
    static class JWTToken {

        private String idToken;

        JWTToken(String idToken) {
            this.idToken = idToken;
        }

        @JsonProperty("id_token")
        String getIdToken() {
            return idToken;
        }

        void setIdToken(String idToken) {
            this.idToken = idToken;
        }
    }
}

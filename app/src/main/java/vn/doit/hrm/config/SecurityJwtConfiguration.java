package vn.doit.hrm.config;

import static vn.doit.hrm.security.SecurityUtils.JWT_ALGORITHM;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import vn.doit.hrm.management.SecurityMetersService;
import vn.doit.hrm.security.ActiveAccountChecker;

@Configuration
public class SecurityJwtConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityJwtConfiguration.class);

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    @Bean
    public JwtDecoder jwtDecoder(SecurityMetersService metersService, ObjectProvider<ActiveAccountChecker> activeAccountChecker) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(getSecretKey()).macAlgorithm(JWT_ALGORITHM).build();
        return token -> {
            Jwt jwt = decode(jwtDecoder, metersService, token);
            // Tài khoản bị khóa (vd nghỉ việc) hoặc đã xóa: từ chối ngay cả khi token còn hạn → 401.
            // (Bộ test cắt lát không có CSDL thì không có bean kiểm tra.)
            ActiveAccountChecker checker = activeAccountChecker.getIfAvailable();
            if (checker != null && !checker.isActive(jwt.getSubject())) {
                throw new BadJwtException("Tài khoản đã bị khóa hoặc không còn tồn tại");
            }
            return jwt;
        };
    }

    private static Jwt decode(NimbusJwtDecoder jwtDecoder, SecurityMetersService metersService, String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (Exception e) {
            if (e.getMessage().contains("Invalid signature")) {
                metersService.trackTokenInvalidSignature();
            } else if (e.getMessage().contains("Jwt expired at")) {
                metersService.trackTokenExpired();
            } else if (
                e.getMessage().contains("Invalid JWT serialization") ||
                e.getMessage().contains("Malformed token") ||
                e.getMessage().contains("Invalid unsecured/JWS/JWE")
            ) {
                metersService.trackTokenMalformed();
            } else {
                LOG.error("Unknown JWT error {}", e.getMessage());
            }
            throw e;
        }
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(getSecretKey()));
    }

    private SecretKey getSecretKey() {
        byte[] keyBytes = Base64.from(jwtKey).decode();
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, JWT_ALGORITHM.getName());
    }
}

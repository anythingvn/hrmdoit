package vn.doit.hrm.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tech.jhipster.config.JHipsterProperties;
import vn.doit.hrm.security.*;
import vn.doit.hrm.web.filter.SpaWebFilter;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    /** Vai trò được dùng các API nghiệp vụ (ROLE_NHAN_VIEN không có trong danh sách: chỉ hồ sơ của mình). */
    static final String[] STAFF_ROLES = {
        AuthoritiesConstants.ADMIN,
        AuthoritiesConstants.HR,
        AuthoritiesConstants.KE_TOAN,
        AuthoritiesConstants.BGD,
        AuthoritiesConstants.USER,
    };

    private final JHipsterProperties jHipsterProperties;

    public SecurityConfiguration(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Ghi (POST/PUT/PATCH/DELETE) trên dữ liệu hồ sơ chỉ ADMIN/HR; chặn ở tầng URL để trả 403 trước cả bước kiểm tra dữ liệu gửi lên.
     */
    private static RequestMatcher[] writeRules(String... patterns) {
        HttpMethod[] methods = { HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE };
        PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> list = new ArrayList<>();
        for (String pattern : patterns) {
            for (HttpMethod method : methods) {
                list.add(builder.matcher(method, pattern));
            }
        }
        return list.toArray(new RequestMatcher[0]);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
            // Đang dùng mật khẩu mặc định: chỉ được xem tài khoản và đổi mật khẩu (403 error.mustchangepassword).
            .addFilterAfter(new MustChangePasswordFilter(), BearerTokenAuthenticationFilter.class)
            .headers(headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(jHipsterProperties.getSecurity().getContentSecurityPolicy()))
                    .frameOptions(FrameOptionsConfig::sameOrigin)
                    // HSTS 1 năm, gồm tên miền con. Spring chỉ gửi trên request https (sau Caddy: nhờ X-Forwarded-Proto,
                    // xem server.forward-headers-strategy ở application-prod.yml); Caddy cũng đặt header này.
                    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicyHeader(permissions ->
                        permissions.policy(
                            "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"
                        )
                    )
            )
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers("/index.html", "/404.html", "/*.js", "/*.txt", "/*.json", "/*.map", "/*.css").permitAll()
                    .requestMatchers("/*.ico", "/*.png", "/*.svg", "/*.webapp").permitAll()
                    .requestMatchers("/assets/**").permitAll()
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/authenticate").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/authenticate").permitAll()
                    .requestMatchers("/api/account/reset-password/init").permitAll()
                    .requestMatchers("/api/account/reset-password/finish").permitAll()
                    // Mật khẩu mặc định để giao diện tạo tài khoản hiện đúng giá trị: Quản trị và Nhân sự (người tạo tài khoản).
                    .requestMatchers(HttpMethod.GET, "/api/admin/account-settings/default-password").hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR)
                    // Dãy số văn bản: Nhân sự xem được (đặt số bắt đầu chỉ Quản trị).
                    .requestMatchers(HttpMethod.GET, "/api/admin/document-sequences").hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR)
                    .requestMatchers("/api/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(
                        writeRules("/api/employees/**", "/api/employment-contracts/**", "/api/salary-changes/**", "/api/allowances/**", "/api/work-assignments/**", "/api/employee-documents/**")
                    ).hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR)
                    // Tự phục vụ: mọi tài khoản (kể cả ROLE_NHAN_VIEN) xem/sửa tài khoản của mình, đổi mật khẩu, xem hồ sơ của mình.
                    .requestMatchers(HttpMethod.GET, "/api/account", "/api/account/employee").authenticated()
                    // Nhân viên xem hợp đồng của mình và tải bản đã ký (service kiểm tra đúng chủ).
                    .requestMatchers(HttpMethod.GET, "/api/account/employee/contracts", "/api/account/employee/contracts/*/signed").authenticated()
                    // Phiếu lương đã gửi của chính mình.
                    .requestMatchers(HttpMethod.GET, "/api/account/employee/payslips", "/api/account/employee/payslips/*/pdf").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/account", "/api/account/change-password").authenticated()
                    // Dữ liệu nhạy cảm: @PreAuthorize cho phép vai trò nghiệp vụ hoặc chính chủ hồ sơ.
                    .requestMatchers(HttpMethod.GET, "/api/employees/*/sensitive").authenticated()
                    // Nghỉ phép: mọi tài khoản (kể cả ROLE_NHAN_VIEN); @PreAuthorize và kiểm tra theo dữ liệu ở từng API.
                    .requestMatchers("/api/leave/**").authenticated()
                    // Chấm công: mọi tài khoản xem bộ giờ làm (sửa: @PreAuthorize Quản trị, HR).
                    .requestMatchers(HttpMethod.GET, "/api/attendance/schedules").authenticated()
                    // Gõ giờ theo ngày: trưởng nhóm, trưởng nhóm onsite là quan hệ trong dữ liệu (thường chỉ có ROLE_NHAN_VIEN); service kiểm tra quyền.
                    .requestMatchers("/api/attendance/day").authenticated()
                    // Mọi API khác cần một vai trò nghiệp vụ; ROLE_NHAN_VIEN đơn thuần bị 403.
                    .requestMatchers("/api/**").hasAnyAuthority(STAFF_ROLES)
                    .requestMatchers("/v3/api-docs/**").hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers("/management/health").permitAll()
                    .requestMatchers("/management/health/**").permitAll()
                    .requestMatchers("/management/info").permitAll()
                    .requestMatchers("/management/**").hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        return http.build();
    }
}

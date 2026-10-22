package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.security.SecurityUtils.AUTHORITIES_CLAIM;
import static vn.doit.hrm.security.SecurityUtils.JWT_ALGORITHM;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.dto.AdminUserDTO;

/**
 * Vòng sửa cuối KH1b: thu hồi JWT khi đổi mật khẩu/vai trò/khóa-mở (M4), chặn khóa ADMIN cuối và tự hạ quyền (M5),
 * endpoint quản trị không công khai (M6).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AccountSecurityIT {

    private static final String PASSWORD = "MatKhauBanDau2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper om;

    private User newUser(String login, String... roles) {
        User u = new User();
        u.setLogin(login);
        u.setActivated(true);
        u.setLangKey("vi");
        u.setPassword(passwordEncoder.encode(PASSWORD));
        Set<Authority> authorities = new HashSet<>();
        for (String role : roles) {
            authorities.add(authorityRepository.findById(role).orElseThrow());
        }
        u.setAuthorities(authorities);
        return userRepository.saveAndFlush(u);
    }

    /** JWT phát hành vài giây trước (như phiên đăng nhập đang dùng), ký bằng khóa thật của ứng dụng. */
    private String oldToken(User u) {
        Instant issued = Instant.now().minusSeconds(5);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuedAt(issued)
            .expiresAt(issued.plusSeconds(3600))
            .subject(u.getLogin())
            .claim(AUTHORITIES_CLAIM, u.getAuthorities().stream().map(Authority::getName).collect(Collectors.joining(" ")))
            .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(JWT_ALGORITHM).build(), claims)).getTokenValue();
    }

    private ResultActions getAccount(String jwt) throws Exception {
        return mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + jwt));
    }

    private ResultActions authenticate(String login, String password) throws Exception {
        return mockMvc.perform(
            post("/api/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + login + "\",\"password\":\"" + password + "\"}")
        );
    }

    private static RequestPostProcessor admin(String login) {
        return user(login).authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }

    private ResultActions putUser(AdminUserDTO dto, RequestPostProcessor actor) throws Exception {
        return mockMvc.perform(
            put("/api/admin/users").with(actor).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto))
        );
    }

    private AdminUserDTO dto(User u) {
        return new AdminUserDTO(userRepository.findOneWithAuthoritiesByLogin(u.getLogin()).orElseThrow());
    }

    /** Chỉ {@code keep} còn là ADMIN đang hoạt động (các ADMIN khác bị khóa trong transaction của test). */
    private void onlyActiveAdmin(User keep) {
        Authority adminRole = authorityRepository.findById(AuthoritiesConstants.ADMIN).orElseThrow();
        for (User other : userRepository.findAll()) {
            if (!other.getId().equals(keep.getId()) && other.getAuthorities().contains(adminRole) && other.isActivated()) {
                other.setActivated(false);
                userRepository.saveAndFlush(other);
            }
        }
    }

    // ------------------------------------------------------------------ M4: thu hồi JWT

    @Test
    void passwordChangeRevokesOldTokens() throws Exception {
        User u = newUser("doi-mk-thu-hoi", AuthoritiesConstants.HR);
        String jwt = oldToken(u);
        getAccount(jwt).andExpect(status().isOk());
        mockMvc
            .perform(
                post("/api/account/change-password")
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"MatKhauMoi2027\"}")
            )
            .andExpect(status().isOk());
        getAccount(jwt).andExpect(status().isUnauthorized());
        authenticate("doi-mk-thu-hoi", "MatKhauMoi2027").andExpect(status().isOk());
    }

    @Test
    void adminPasswordSetRevokesOldTokens() throws Exception {
        User u = newUser("dat-mk-thu-hoi", AuthoritiesConstants.HR);
        String jwt = oldToken(u);
        getAccount(jwt).andExpect(status().isOk());
        mockMvc
            .perform(
                put("/api/admin/users/{login}/password", "dat-mk-thu-hoi")
                    .with(admin("quan-tri-dat-mk"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newPassword\":\"MatKhauMoi2027\"}")
            )
            .andExpect(status().isNoContent());
        getAccount(jwt).andExpect(status().isUnauthorized());
    }

    @Test
    void roleRemovalRevokesOldTokens() throws Exception {
        User u = newUser("bo-vai-tro", AuthoritiesConstants.HR, AuthoritiesConstants.KE_TOAN);
        String jwt = oldToken(u);
        mockMvc.perform(get("/api/employees").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
        AdminUserDTO d = dto(u);
        d.setAuthorities(Set.of(AuthoritiesConstants.KE_TOAN));
        putUser(d, admin("quan-tri-sua")).andExpect(status().isOk());
        getAccount(jwt).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/employees").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
    }

    @Test
    void editingNameOnlyKeepsTokens() throws Exception {
        User u = newUser("sua-ten", AuthoritiesConstants.HR);
        String jwt = oldToken(u);
        AdminUserDTO d = dto(u);
        d.setFirstName("Tên mới");
        putUser(d, admin("quan-tri-sua")).andExpect(status().isOk());
        getAccount(jwt).andExpect(status().isOk());
    }

    @Test
    void deactivateThenReactivateStillRevokesOldTokens() throws Exception {
        User u = newUser("khoa-mo", AuthoritiesConstants.HR);
        String jwt = oldToken(u);
        AdminUserDTO d = dto(u);
        d.setActivated(false);
        putUser(d, admin("quan-tri-sua")).andExpect(status().isOk());
        d.setActivated(true);
        putUser(d, admin("quan-tri-sua")).andExpect(status().isOk());
        getAccount(jwt).andExpect(status().isUnauthorized());
        authenticate("khoa-mo", PASSWORD).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ M5: ADMIN cuối, tự hạ quyền, mở tài khoản người đã nghỉ

    @Test
    void cannotDeactivateDemoteOrDeleteLastActiveAdmin() throws Exception {
        User last = newUser("admin-cuoi", AuthoritiesConstants.ADMIN);
        onlyActiveAdmin(last);

        AdminUserDTO d = dto(last);
        d.setActivated(false);
        putUser(d, admin("quan-tri-khac")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.lastadmin"));

        d = dto(last);
        d.setAuthorities(Set.of(AuthoritiesConstants.HR));
        putUser(d, admin("quan-tri-khac")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.lastadmin"));

        mockMvc
            .perform(delete("/api/admin/users/{login}", "admin-cuoi").with(admin("quan-tri-khac")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.lastadmin"));

        User reloaded = userRepository.findOneWithAuthoritiesByLogin("admin-cuoi").orElseThrow();
        assertThat(reloaded.isActivated()).isTrue();
        assertThat(reloaded.getAuthorities()).extracting(Authority::getName).contains(AuthoritiesConstants.ADMIN);

        // Có thêm một ADMIN đang hoạt động thì được.
        newUser("admin-moi", AuthoritiesConstants.ADMIN);
        d = dto(last);
        d.setActivated(false);
        putUser(d, admin("quan-tri-khac")).andExpect(status().isOk());
    }

    @Test
    void adminCannotDemoteOrDeactivateSelf() throws Exception {
        User self = newUser("admin-tu-sua", AuthoritiesConstants.ADMIN);
        newUser("admin-du-phong", AuthoritiesConstants.ADMIN);

        AdminUserDTO d = dto(self);
        d.setAuthorities(Set.of(AuthoritiesConstants.HR));
        putUser(d, admin("admin-tu-sua")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.selfadmin"));

        d = dto(self);
        d.setActivated(false);
        putUser(d, admin("admin-tu-sua")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.selfadmin"));

        mockMvc
            .perform(delete("/api/admin/users/{login}", "admin-tu-sua").with(admin("admin-tu-sua")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.selfadmin"));

        // Sửa thông tin khác của chính mình vẫn được.
        d = dto(self);
        d.setLastName("Quản trị");
        putUser(d, admin("admin-tu-sua")).andExpect(status().isOk());
    }

    @Test
    void cannotReactivateAccountOfTerminatedEmployee() throws Exception {
        User u = newUser("nv-da-nghi-khoa", AuthoritiesConstants.NHAN_VIEN);
        u.setActivated(false);
        userRepository.saveAndFlush(u);
        employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9701")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Người Đã Nghỉ")
                .searchText("nv9701")
                .dependants(0)
                .status(EmploymentStatus.DA_NGHI)
                .joinDate(LocalDate.of(2025, 1, 1))
                .lastWorkingDay(LocalDate.of(2026, 9, 30))
                .terminationDate(LocalDate.of(2026, 10, 1))
                .user(u)
        );
        AdminUserDTO d = dto(u);
        d.setActivated(true);
        putUser(d, admin("quan-tri-mo"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeeterminated"));
        assertThat(userRepository.findById(u.getId()).orElseThrow().isActivated()).isFalse();

        // Sửa thông tin khác (vẫn khóa) thì được.
        d.setActivated(false);
        d.setFirstName("Tên");
        putUser(d, admin("quan-tri-mo")).andExpect(status().isOk());
    }

    /** R22: tài khoản gắn với hồ sơ đã lưu trữ (đã nghỉ hoặc tạo nhầm) không mở lại được: khôi phục hồ sơ trước. */
    @Test
    void cannotReactivateAccountOfArchivedEmployee() throws Exception {
        int i = 0;
        for (EmploymentStatus status : new EmploymentStatus[] { EmploymentStatus.DA_NGHI, EmploymentStatus.DANG_LAM_VIEC }) {
            i++;
            User u = newUser("nv-luu-tru-khoa-" + i, AuthoritiesConstants.NHAN_VIEN);
            u.setActivated(false);
            userRepository.saveAndFlush(u);
            Employee e = new Employee()
                .code("NV971" + i)
                .personType(PersonType.NHAN_VIEN)
                .fullName("Người Lưu Trữ " + i)
                .searchText("nv971" + i)
                .dependants(0)
                .status(status)
                .joinDate(LocalDate.of(2025, 1, 1))
                .user(u);
            if (status == EmploymentStatus.DA_NGHI) {
                e.lastWorkingDay(LocalDate.of(2026, 9, 30)).terminationDate(LocalDate.of(2026, 10, 1));
            }
            e.setArchivedAt(java.time.Instant.parse("2026-10-05T00:00:00Z"));
            e.setArchivedBy("hr");
            employeeRepository.saveAndFlush(e);
            AdminUserDTO d = dto(u);
            d.setActivated(true);
            putUser(d, admin("quan-tri-mo-luu-tru"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.accountemployeearchived"));
            assertThat(userRepository.findById(u.getId()).orElseThrow().isActivated()).isFalse();
        }
    }

    // ------------------------------------------------------------------ M6: management

    @Test
    void prometheusIsNotPublic() throws Exception {
        mockMvc.perform(get("/management/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void prometheusForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/management/prometheus")).andExpect(status().isForbidden());
    }
}

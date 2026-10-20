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
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmployeeDocument;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.EmployeeDocumentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;

/**
 * Đăng nhập bằng số CCCD: tên đăng nhập = CCCD của hồ sơ, mật khẩu mặc định Doit@2014, bắt đổi mật khẩu lần đầu, đồng bộ khi
 * sửa CCCD, che tên đăng nhập (CCCD) với vai trò không được xem CCCD.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class CccdLoginIT {

    private static final String DEFAULT_PASSWORD = "Doit@2014";
    private static final String OTHER_PASSWORD = "MatKhauBanDau2026";
    private static final String CCCD = "079100000001";
    private static final String CCCD_OTHER = "079100000002";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeDocumentRepository employeeDocumentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private vn.doit.hrm.security.LoginAttemptService loginAttemptService;

    private Employee employee;

    private Employee other;

    @BeforeEach
    void init() {
        loginAttemptService.clear();
        employee = employeeRepository.saveAndFlush(newEmployee("NV9301", "Nguyễn Thị Mai", CCCD));
        other = employeeRepository.saveAndFlush(newEmployee("NV9302", "Lê Văn Hùng", CCCD_OTHER));
    }

    private static Employee newEmployee(String code, String name, String idNumber) {
        return new Employee()
            .code(code)
            .personType(PersonType.NHAN_VIEN)
            .fullName(name)
            .searchText(code.toLowerCase())
            .dependants(0)
            .status(EmploymentStatus.DANG_LAM_VIEC)
            .joinDate(LocalDate.of(2024, 1, 2))
            .idNumber(idNumber);
    }

    private User newUser(String login, String... roles) {
        User u = new User();
        u.setLogin(login);
        u.setActivated(true);
        u.setLangKey("vi");
        u.setPassword(passwordEncoder.encode(OTHER_PASSWORD));
        Set<Authority> authorities = new HashSet<>();
        for (String role : roles) {
            authorities.add(authorityRepository.findById(role).orElseThrow());
        }
        u.setAuthorities(authorities);
        return userRepository.saveAndFlush(u);
    }

    private static RequestPostProcessor as(String login, String role) {
        return user(login).authorities(new SimpleGrantedAuthority(role));
    }

    private static final RequestPostProcessor HR = as("hr-test", AuthoritiesConstants.HR);
    private static final RequestPostProcessor ADMIN = as("admin-test", AuthoritiesConstants.ADMIN);

    private ResultActions createAccount(Long employeeId, RequestPostProcessor actor, String... roles) throws Exception {
        String json =
            "{\"authorities\":[" + java.util.Arrays.stream(roles).map(r -> '"' + r + '"').collect(Collectors.joining(",")) + "]}";
        return mockMvc.perform(
            post("/api/employees/{id}/account", employeeId).with(actor).contentType(MediaType.APPLICATION_JSON).content(json)
        );
    }

    private ResultActions authenticate(String login, String password) throws Exception {
        return mockMvc.perform(
            post("/api/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + login + "\",\"password\":\"" + password + "\"}")
        );
    }

    private String token(String login, String password) throws Exception {
        String body = authenticate(login, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id_token").asText();
    }

    /** JWT phát hành vài giây trước (phiên đang dùng), ký bằng khóa thật. */
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

    private ResultActions changePassword(String jwt, String current, String next) throws Exception {
        return mockMvc.perform(
            post("/api/account/change-password")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}")
        );
    }

    private ResultActions patchIdNumber(Long id, String idNumber, RequestPostProcessor actor) throws Exception {
        return mockMvc.perform(
            patch("/api/employees/{id}", id)
                .with(actor)
                .contentType("application/merge-patch+json")
                .content("{\"id\":" + id + ",\"idNumber\":\"" + idNumber + "\"}")
        );
    }

    private void link(Employee e, User u) {
        e.setUser(u);
        employeeRepository.saveAndFlush(e);
    }

    // ---------------------------------------------------------------- 1. Tạo tài khoản từ hồ sơ: login = CCCD

    @Test
    void accountFromProfileUsesCccdAndDefaultPasswordAndMustChangeFirst() throws Exception {
        createAccount(employee.getId(), HR, AuthoritiesConstants.NHAN_VIEN)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.login").value(CCCD))
            .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findOneByLogin(CCCD).orElseThrow();
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, saved.getPassword())).isTrue();
        assertThat(employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow().getUser().getId()).isEqualTo(
            saved.getId()
        );

        String jwt = token(CCCD, DEFAULT_PASSWORD);
        mockMvc
            .perform(get("/api/account").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.login").value(CCCD))
            .andExpect(jsonPath("$.mustChangePassword").value(true));
        // Mọi API khác bị chặn cho tới khi đổi mật khẩu.
        for (String url : new String[] { "/api/account/employee", "/api/employees/" + employee.getId() + "/sensitive", "/api/employees" }) {
            mockMvc
                .perform(get(url).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("error.mustchangepassword"));
        }

        // Không được đổi sang chính mật khẩu mặc định.
        changePassword(jwt, DEFAULT_PASSWORD, DEFAULT_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.defaultpassword"));
        assertThat(userRepository.findOneByLogin(CCCD).orElseThrow().isMustChangePassword()).isTrue();

        changePassword(jwt, DEFAULT_PASSWORD, "MatKhauMoi2027").andExpect(status().isOk());
        assertThat(userRepository.findOneByLogin(CCCD).orElseThrow().isMustChangePassword()).isFalse();

        String fresh = token(CCCD, "MatKhauMoi2027");
        mockMvc
            .perform(get("/api/account").header("Authorization", "Bearer " + fresh))
            .andExpect(jsonPath("$.mustChangePassword").value(false));
        mockMvc
            .perform(get("/api/account/employee").header("Authorization", "Bearer " + fresh))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userLogin").value(CCCD));
    }

    @Test
    void cccdIsTrimmedAndNineDigitCmndAccepted() throws Exception {
        employee.setIdNumber("  079100000001 ");
        employeeRepository.saveAndFlush(employee);
        createAccount(employee.getId(), HR).andExpect(status().isCreated()).andExpect(jsonPath("$.login").value(CCCD));

        other.setIdNumber("123456789");
        employeeRepository.saveAndFlush(other);
        createAccount(other.getId(), HR).andExpect(status().isCreated()).andExpect(jsonPath("$.login").value("123456789"));
    }

    @Test
    void profileWithoutCccdCannotGetAccount() throws Exception {
        employee.setIdNumber(null);
        employeeRepository.saveAndFlush(employee);
        createAccount(employee.getId(), HR)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.idnumberrequired"));

        employee.setIdNumber("07910-ABC");
        employeeRepository.saveAndFlush(employee);
        createAccount(employee.getId(), HR)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.idnumberinvalid"));
        assertThat(employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow().getUser()).isNull();
    }

    @Test
    void cccdAlreadyUsedAsLoginIsRejected() throws Exception {
        newUser(CCCD, AuthoritiesConstants.HR);
        createAccount(employee.getId(), HR).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.loginexists"));
        assertThat(employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow().getUser()).isNull();
    }

    // ---------------------------------------------------------------- Đồng bộ khi sửa CCCD

    @Test
    void editingCccdRenamesLoginAndEndsOldSessions() throws Exception {
        createAccount(employee.getId(), HR).andExpect(status().isCreated());
        User before = userRepository.findOneWithAuthoritiesByLogin(CCCD).orElseThrow();
        String old = oldToken(before);
        mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + old)).andExpect(status().isOk());

        patchIdNumber(employee.getId(), "079100000099", HR).andExpect(status().isOk());

        assertThat(userRepository.findOneByLogin(CCCD)).isEmpty();
        User after = userRepository.findOneByLogin("079100000099").orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getCredentialsChangedAt()).isNotNull();
        mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + old)).andExpect(status().isUnauthorized());
        authenticate(CCCD, DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        authenticate("079100000099", DEFAULT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void editingCccdWithPutAlsoRenamesLogin() throws Exception {
        createAccount(employee.getId(), HR).andExpect(status().isCreated());
        String body = mockMvc
            .perform(get("/api/employees/{id}", employee.getId()).with(HR))
            .andReturn()
            .getResponse()
            .getContentAsString();
        tools.jackson.databind.node.ObjectNode node = (tools.jackson.databind.node.ObjectNode) om.readTree(body);
        node.put("idNumber", " 079100000088 ");
        mockMvc
            .perform(
                put("/api/employees/{id}", employee.getId())
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(node))
            )
            .andExpect(status().isOk());
        assertThat(userRepository.findOneByLogin("079100000088")).isPresent();
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getIdNumber()).isEqualTo("079100000088");
    }

    @Test
    void editingCccdToANumberUsedByAnotherLoginIsRejected() throws Exception {
        createAccount(employee.getId(), HR).andExpect(status().isCreated());
        newUser("079100000077", AuthoritiesConstants.KE_TOAN);

        patchIdNumber(employee.getId(), "079100000077", HR)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.loginexists"));

        assertThat(userRepository.findOneByLogin(CCCD)).isPresent();
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getIdNumber()).isEqualTo(CCCD);
    }

    @Test
    void editingCccdOfLinkedProfileMustStayAValidNumber() throws Exception {
        createAccount(employee.getId(), HR).andExpect(status().isCreated());
        patchIdNumber(employee.getId(), "abc", HR)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.idnumberinvalid"));
        patchIdNumber(employee.getId(), "  ", HR)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.idnumberrequired"));
        assertThat(userRepository.findOneByLogin(CCCD)).isPresent();
    }

    @Test
    void editingCccdOfProfileWithoutAccountDoesNotTouchUsers() throws Exception {
        long users = userRepository.count();
        patchIdNumber(employee.getId(), "079100000055", HR).andExpect(status().isOk());
        assertThat(userRepository.count()).isEqualTo(users);
    }

    // ---------------------------------------------------------------- Gắn tài khoản có sẵn: đổi login sang CCCD

    @Test
    void linkingExistingAccountRenamesItToCccd() throws Exception {
        User existing = newUser("mai.nguyen", AuthoritiesConstants.NHAN_VIEN);
        String old = oldToken(existing);
        mockMvc
            .perform(
                put("/api/employees/{id}/account", employee.getId())
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + existing.getId() + "}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.login").value(CCCD));
        assertThat(userRepository.findById(existing.getId()).orElseThrow().getLogin()).isEqualTo(CCCD);
        mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + old)).andExpect(status().isUnauthorized());
        authenticate(CCCD, OTHER_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void linkingIsRejectedWhenCccdBelongsToAnotherLogin() throws Exception {
        newUser(CCCD, AuthoritiesConstants.HR);
        User existing = newUser("mai.nguyen", AuthoritiesConstants.NHAN_VIEN);
        mockMvc
            .perform(
                put("/api/employees/{id}/account", employee.getId())
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + existing.getId() + "}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.loginexists"));
        assertThat(userRepository.findById(existing.getId()).orElseThrow().getLogin()).isEqualTo("mai.nguyen");
    }

    @Test
    void systemAdminAccountIsNeverRenamed() throws Exception {
        User admin = userRepository.findOneByLogin("admin").orElseThrow();
        mockMvc
            .perform(
                put("/api/employees/{id}/account", employee.getId())
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + admin.getId() + "}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.systemaccount"));
        assertThat(userRepository.findOneByLogin("admin")).isPresent();
    }

    // ---------------------------------------------------------------- Quản trị tạo người dùng bằng CCCD

    @Test
    void adminCreatesUserFromCccdAndItIsLinkedToMatchingProfile() throws Exception {
        mockMvc
            .perform(
                post("/api/admin/users")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"bo-qua\",\"idNumber\":\" " + CCCD_OTHER + "\",\"authorities\":[\"ROLE_HR\"]}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.login").value(CCCD_OTHER));
        User saved = userRepository.findOneByLogin(CCCD_OTHER).orElseThrow();
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, saved.getPassword())).isTrue();
        assertThat(employeeRepository.findOneByUserId(saved.getId())).map(Employee::getId).contains(other.getId());
        assertThat(userRepository.findOneByLogin("bo-qua")).isEmpty();
    }

    @Test
    void adminCreatesUserWithoutMatchingProfile() throws Exception {
        mockMvc
            .perform(
                post("/api/admin/users")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"001200000123\",\"idNumber\":\"001200000123\",\"authorities\":[\"ROLE_ADMIN\"]}")
            )
            .andExpect(status().isCreated());
        User saved = userRepository.findOneByLogin("001200000123").orElseThrow();
        assertThat(employeeRepository.findOneByUserId(saved.getId())).isEmpty();
        assertThat(saved.isMustChangePassword()).isTrue();
    }

    @Test
    void adminCreateNeedsAValidCccd() throws Exception {
        mockMvc
            .perform(
                post("/api/admin/users")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"khong-cccd\",\"authorities\":[\"ROLE_HR\"]}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.idnumberrequired"));
        mockMvc
            .perform(
                post("/api/admin/users")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"x\",\"idNumber\":\"12ab\",\"authorities\":[\"ROLE_HR\"]}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.idnumberinvalid"));
        newUser("079100000066", AuthoritiesConstants.HR);
        mockMvc
            .perform(
                post("/api/admin/users")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"079100000066\",\"idNumber\":\"079100000066\",\"authorities\":[\"ROLE_HR\"]}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.userexists"));
        assertThat(userRepository.findOneByLogin("khong-cccd")).isEmpty();
    }

    @Test
    void adminCannotRenameLinkedAccount() throws Exception {
        createAccount(employee.getId(), HR).andExpect(status().isCreated());
        vn.doit.hrm.service.dto.AdminUserDTO dto = new vn.doit.hrm.service.dto.AdminUserDTO(
            userRepository.findOneWithAuthoritiesByLogin(CCCD).orElseThrow()
        );
        dto.setLogin("ten-khac");
        mockMvc
            .perform(put("/api/admin/users").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.loginfromidnumber"));
        assertThat(userRepository.findOneByLogin(CCCD)).isPresent();
    }

    // ---------------------------------------------------------------- Đặt lại mật khẩu mặc định

    @Test
    void adminResetsToDefaultPasswordAndFlagIsBackOn() throws Exception {
        createAccount(employee.getId(), HR).andExpect(status().isCreated());
        String jwt = token(CCCD, DEFAULT_PASSWORD);
        changePassword(jwt, DEFAULT_PASSWORD, "MatKhauMoi2027").andExpect(status().isOk());
        assertThat(userRepository.findOneByLogin(CCCD).orElseThrow().isMustChangePassword()).isFalse();

        mockMvc.perform(put("/api/admin/users/{login}/password/default", CCCD).with(ADMIN)).andExpect(status().isNoContent());

        User saved = userRepository.findOneByLogin(CCCD).orElseThrow();
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, saved.getPassword())).isTrue();
        String again = token(CCCD, DEFAULT_PASSWORD);
        mockMvc
            .perform(get("/api/account/employee").header("Authorization", "Bearer " + again))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.mustchangepassword"));

        mockMvc.perform(put("/api/admin/users/{login}/password/default", CCCD).with(HR)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/users/{login}/password/default", "khong-co").with(ADMIN)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- Không lộ CCCD qua tên đăng nhập

    @Test
    void cccdLoginIsMaskedForRolesThatCannotSeeCccd() throws Exception {
        createAccount(employee.getId(), HR).andExpect(status().isCreated());
        User hrUser = newUser(CCCD_OTHER, AuthoritiesConstants.HR);
        link(other, hrUser);
        EmployeeDocument doc = new EmployeeDocument()
            .employee(employee)
            .fileName("cccd.pdf")
            .contentType("application/pdf")
            .sizeBytes(10L)
            .storagePath("x/cccd.pdf")
            .uploadedBy(CCCD_OTHER)
            .uploadedAt(Instant.now());
        employeeDocumentRepository.saveAndFlush(doc);

        for (String role : new String[] { AuthoritiesConstants.BGD, AuthoritiesConstants.KE_TOAN }) {
            RequestPostProcessor viewer = as("viewer", role);
            String[] urls = {
                "/api/employees",
                "/api/employees?sort=code,asc",
                "/api/employees/" + employee.getId(),
                "/api/employees/" + employee.getId() + "/documents",
                "/api/employee-documents",
                "/api/employee-documents/" + doc.getId(),
            };
            for (String url : urls) {
                String body = mockMvc.perform(get(url).with(viewer)).andReturn().getResponse().getContentAsString();
                assertThat(body).as(role + " " + url).doesNotContain(CCCD).doesNotContain(CCCD_OTHER);
            }
            mockMvc
                .perform(get("/api/employees/{id}", employee.getId()).with(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userLogin").value("********0001"))
                .andExpect(jsonPath("$.user.login").value("********0001"));
            mockMvc
                .perform(get("/api/employee-documents/{id}", doc.getId()).with(viewer))
                .andExpect(jsonPath("$.uploadedBy").value("********0002"));
            // Danh sách tài khoản công khai không còn dành cho vai trò này.
            String users = mockMvc.perform(get("/api/users").with(viewer)).andReturn().getResponse().getContentAsString();
            assertThat(users).doesNotContain(CCCD).doesNotContain(CCCD_OTHER);
            mockMvc.perform(get("/api/employees/{id}/history", employee.getId()).with(viewer)).andExpect(status().isForbidden());
        }

        // ADMIN/HR thấy đủ.
        mockMvc.perform(get("/api/employees/{id}", employee.getId()).with(HR)).andExpect(jsonPath("$.userLogin").value(CCCD));
        mockMvc.perform(get("/api/admin/users/{login}", CCCD).with(ADMIN)).andExpect(jsonPath("$.login").value(CCCD));
    }

    @Test
    void ownProfileShowsOwnLoginEvenForBgd() throws Exception {
        User bgd = newUser(CCCD, AuthoritiesConstants.BGD);
        link(employee, bgd);
        mockMvc
            .perform(get("/api/account/employee").with(as(CCCD, AuthoritiesConstants.BGD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userLogin").value(CCCD));
        mockMvc
            .perform(get("/api/employees/{id}", other.getId()).with(as(CCCD, AuthoritiesConstants.BGD)))
            .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- Bộ đếm đăng nhập sai với login CCCD

    @Test
    void failedLoginCounterWorksWithCccdLogin() throws Exception {
        createAccount(employee.getId(), HR).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            authenticate(CCCD, "SaiMatKhau" + i).andExpect(status().isUnauthorized());
        }
        authenticate(CCCD, DEFAULT_PASSWORD).andExpect(status().isTooManyRequests());
        mockMvc.perform(delete("/api/admin/login-attempts/{login}", CCCD).with(ADMIN)).andExpect(status().isNoContent());
        authenticate(CCCD, DEFAULT_PASSWORD).andExpect(status().isOk());
    }
}

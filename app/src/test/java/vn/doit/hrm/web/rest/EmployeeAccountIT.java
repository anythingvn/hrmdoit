package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.SensitiveAccessLogRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;

/**
 * Tài khoản đăng nhập gắn hồ sơ nhân viên (1–1), tạo tài khoản kèm mật khẩu ban đầu, vai trò ROLE_NHAN_VIEN.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class EmployeeAccountIT {

    private static final String PASSWORD = "MatKhauBanDau2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private SensitiveAccessLogRepository sensitiveAccessLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper om;

    private Employee employee;

    private Employee other;

    @BeforeEach
    void init() {
        employee = employeeRepository.saveAndFlush(newEmployee("NV9101", "Nguyễn Thị Mai", "079100000001"));
        other = employeeRepository.saveAndFlush(newEmployee("NV9102", "Lê Văn Hùng", "079100000002"));
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
            .idNumber(idNumber)
            .taxCode("8001234567")
            .bankAccount("111222333")
            .bankName("Vietcombank")
            .socialInsuranceNo("7900112233");
    }

    private User newUser(String login, String... roles) {
        User user = new User();
        user.setLogin(login);
        user.setActivated(true);
        user.setLangKey("vi");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        Set<Authority> authorities = new java.util.HashSet<>();
        for (String role : roles) {
            authorities.add(authorityRepository.findById(role).orElseThrow());
        }
        user.setAuthorities(authorities);
        return userRepository.saveAndFlush(user);
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
        JsonNode node = om.readTree(body);
        return node.get("id_token").asText();
    }

    private ResultActions createAccount(Long employeeId, String login, String password, String... roles) throws Exception {
        StringBuilder json = new StringBuilder("{\"login\":\"" + login + "\",\"password\":\"" + password + "\",\"authorities\":[");
        for (int i = 0; i < roles.length; i++) {
            json.append(i > 0 ? "," : "")
                .append('"')
                .append(roles[i])
                .append('"');
        }
        json.append("]}");
        return mockMvc.perform(
            post("/api/employees/{id}/account", employeeId).contentType(MediaType.APPLICATION_JSON).content(json.toString())
        );
    }

    // ---------- Vai trò ----------

    @Test
    void nhanVienRoleSeeded() {
        assertThat(AuthoritiesConstants.NHAN_VIEN).isEqualTo("ROLE_NHAN_VIEN");
        assertThat(authorityRepository.findById("ROLE_NHAN_VIEN")).isPresent();
    }

    // ---------- Quản trị tạo tài khoản kèm mật khẩu ban đầu ----------

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.ADMIN)
    void adminCreatesUserWithInitialPasswordWithoutEmailAndItLogsInImmediately() throws Exception {
        mockMvc
            .perform(
                post("/api/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"ketoan-moi\",\"password\":\"" + PASSWORD + "\",\"authorities\":[\"ROLE_KE_TOAN\"]}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findOneWithAuthoritiesByLogin("ketoan-moi").orElseThrow();
        assertThat(saved.isActivated()).isTrue();
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getResetKey()).as("không cần link đặt mật khẩu").isNull();
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPassword())).isTrue();

        authenticate("ketoan-moi", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.id_token").isNotEmpty());
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.ADMIN)
    void twoUsersWithoutEmailCanBeCreated() throws Exception {
        for (String login : new String[] { "khong-email-1", "khong-email-2" }) {
            mockMvc
                .perform(
                    post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\",\"authorities\":[\"ROLE_HR\"]}")
                )
                .andExpect(status().isCreated());
        }
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.ADMIN)
    void adminInitialPasswordTooShortIsRejected() throws Exception {
        mockMvc
            .perform(
                post("/api/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"ngan-qua\",\"password\":\"1234567\",\"authorities\":[\"ROLE_HR\"]}")
            )
            .andExpect(status().isBadRequest());
        assertThat(userRepository.findOneByLogin("ngan-qua")).isEmpty();
    }

    // ---------- Tạo + gắn tài khoản trên hồ sơ ----------

    @Test
    @WithMockUser(username = "hr-test", authorities = AuthoritiesConstants.HR)
    void hrCreatesAccountForEmployeeAndItLogsInImmediately() throws Exception {
        createAccount(employee.getId(), "Mai.Nguyen", PASSWORD, AuthoritiesConstants.NHAN_VIEN)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.login").value("mai.nguyen"))
            .andExpect(jsonPath("$.activated").value(true))
            .andExpect(jsonPath("$.authorities").value(hasItem("ROLE_NHAN_VIEN")))
            .andExpect(jsonPath("$.password").doesNotExist());

        Employee linked = employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow();
        assertThat(linked.getUser()).isNotNull();
        assertThat(linked.getUser().getLogin()).isEqualTo("mai.nguyen");
        assertThat(linked.getUser().getEmail()).isNull();

        mockMvc.perform(get("/api/employees/{id}", employee.getId())).andExpect(jsonPath("$.userLogin").value("mai.nguyen"));
        mockMvc
            .perform(get("/api/employees/{id}/account", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.login").value("mai.nguyen"));

        authenticate("mai.nguyen", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void createAccountWithoutRolesDefaultsToNhanVien() throws Exception {
        createAccount(employee.getId(), "mac-dinh", PASSWORD).andExpect(status().isCreated());
        User user = userRepository.findOneWithAuthoritiesByLogin("mac-dinh").orElseThrow();
        assertThat(user.getAuthorities()).extracting(Authority::getName).containsExactly(AuthoritiesConstants.NHAN_VIEN);
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void createAccountRejectsBadInput() throws Exception {
        createAccount(employee.getId(), "ngan", "1234567").andExpect(status().isBadRequest());
        createAccount(employee.getId(), "co dau cach", PASSWORD).andExpect(status().isBadRequest());
        createAccount(employee.getId(), "vai-tro-la", PASSWORD, "ROLE_KHONG_CO")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.badauthority"));
        createAccount(999_999L, "khong-co-nv", PASSWORD).andExpect(status().isNotFound());
        assertThat(userRepository.findOneByLogin("vai-tro-la")).isEmpty();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void createAccountWithExistingLoginIsRejected() throws Exception {
        newUser("da-co", AuthoritiesConstants.HR);
        createAccount(employee.getId(), "DA-CO", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.userexists"));
        assertThat(employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow().getUser()).isNull();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void employeeWithAccountCannotGetASecondOne() throws Exception {
        createAccount(employee.getId(), "lan-mot", PASSWORD).andExpect(status().isCreated());
        createAccount(employee.getId(), "lan-hai", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeehasaccount"));
        assertThat(userRepository.findOneByLogin("lan-hai")).isEmpty();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void hrCannotGrantAdmin() throws Exception {
        createAccount(employee.getId(), "muon-admin", PASSWORD, AuthoritiesConstants.ADMIN).andExpect(status().isForbidden());
        createAccount(employee.getId(), "muon-admin", PASSWORD, AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN).andExpect(
            status().isForbidden()
        );
        assertThat(userRepository.findOneByLogin("muon-admin")).isEmpty();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void hrMayGrantNonAdminRoles() throws Exception {
        createAccount(
            employee.getId(),
            "nhieu-vai-tro",
            PASSWORD,
            AuthoritiesConstants.NHAN_VIEN,
            AuthoritiesConstants.HR,
            AuthoritiesConstants.KE_TOAN,
            AuthoritiesConstants.BGD
        ).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.ADMIN)
    void adminMayGrantAdmin() throws Exception {
        createAccount(employee.getId(), "admin-moi", PASSWORD, AuthoritiesConstants.ADMIN).andExpect(status().isCreated());
    }

    @Test
    void otherRolesCannotManageAccounts() throws Exception {
        for (String role : new String[] { AuthoritiesConstants.KE_TOAN, AuthoritiesConstants.BGD, AuthoritiesConstants.NHAN_VIEN }) {
            var auth = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("x").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(role)
            );
            mockMvc
                .perform(
                    post("/api/employees/{id}/account", employee.getId())
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"khong-duoc\",\"password\":\"" + PASSWORD + "\"}")
                )
                .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/employees/{id}/account", employee.getId()).with(auth)).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/employees/{id}/account", employee.getId()).with(auth)).andExpect(status().isForbidden());
        }
        assertThat(userRepository.findOneByLogin("khong-duoc")).isEmpty();
    }

    // ---------- Gắn tài khoản có sẵn / bỏ gắn ----------

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void linkExistingUser() throws Exception {
        User user = newUser("co-san", AuthoritiesConstants.NHAN_VIEN);
        mockMvc
            .perform(
                put("/api/employees/{id}/account", employee.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + user.getId() + "}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.login").value("co-san"));
        assertThat(employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow().getUser().getId()).isEqualTo(
            user.getId()
        );
        mockMvc
            .perform(get("/api/employees/account-candidates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].login").value(not(hasItem("co-san"))));
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void linkUserAlreadyLinkedToAnotherEmployeeIsRejected() throws Exception {
        User user = newUser("da-gan", AuthoritiesConstants.NHAN_VIEN);
        other.setUser(user);
        employeeRepository.saveAndFlush(other);

        mockMvc
            .perform(
                put("/api/employees/{id}/account", employee.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + user.getId() + "}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.useralreadylinked"));
        assertThat(employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow().getUser()).isNull();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void linkUnknownUserIsRejected() throws Exception {
        mockMvc
            .perform(
                put("/api/employees/{id}/account", employee.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":987654}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.usernotfound"));
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void hrCannotLinkAdminAccount() throws Exception {
        User admin = newUser("quan-tri-2", AuthoritiesConstants.ADMIN);
        mockMvc
            .perform(
                put("/api/employees/{id}/account", employee.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + admin.getId() + "}")
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/employees/account-candidates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].login").value(not(hasItem("quan-tri-2"))));
    }

    @Test
    void databaseRejectsOneUserOnTwoEmployees() {
        User user = newUser("hai-ho-so", AuthoritiesConstants.NHAN_VIEN);
        employee.setUser(user);
        employeeRepository.saveAndFlush(employee);
        other.setUser(user);
        assertThatThrownBy(() -> employeeRepository.saveAndFlush(other)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void unlinkKeepsUser() throws Exception {
        User user = newUser("bo-gan", AuthoritiesConstants.NHAN_VIEN);
        employee.setUser(user);
        employeeRepository.saveAndFlush(employee);

        mockMvc.perform(delete("/api/employees/{id}/account", employee.getId())).andExpect(status().isNoContent());

        assertThat(employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow().getUser()).isNull();
        assertThat(userRepository.findOneByLogin("bo-gan")).isPresent();
        mockMvc
            .perform(get("/api/employees/{id}/account", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.login").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void employeeUpdateDoesNotChangeAccountLink() throws Exception {
        User user = newUser("giu-lien-ket", AuthoritiesConstants.NHAN_VIEN);
        employee.setUser(user);
        employeeRepository.saveAndFlush(employee);
        mockMvc
            .perform(
                patch("/api/employees/{id}", employee.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + employee.getId() + ",\"userLogin\":\"khac\",\"user\":null,\"phone\":\"0901234567\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userLogin").value("giu-lien-ket"));
    }

    // ---------- ROLE_NHAN_VIEN: chỉ hồ sơ của mình ----------

    private void linkSelf() {
        User user = newUser("nv-self", AuthoritiesConstants.NHAN_VIEN);
        employee.setUser(user);
        employeeRepository.saveAndFlush(employee);
    }

    @Test
    @WithMockUser(username = "nv-self", authorities = AuthoritiesConstants.NHAN_VIEN)
    void nhanVienReadsOwnProfile() throws Exception {
        linkSelf();
        mockMvc
            .perform(get("/api/account/employee"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(employee.getId()))
            .andExpect(jsonPath("$.fullName").value("Nguyễn Thị Mai"))
            .andExpect(jsonPath("$.idNumber").doesNotExist())
            .andExpect(jsonPath("$.bankAccount").doesNotExist());
        mockMvc.perform(get("/api/account")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "nv-self", authorities = AuthoritiesConstants.NHAN_VIEN)
    void nhanVienReadsOwnSensitiveAndItIsLogged() throws Exception {
        linkSelf();
        long before = sensitiveAccessLogRepository.count();
        mockMvc
            .perform(get("/api/employees/{id}/sensitive", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idNumber").value("079100000001"))
            .andExpect(jsonPath("$.bankAccount").value("111222333"));
        assertThat(sensitiveAccessLogRepository.count()).isEqualTo(before + 1);
    }

    @Test
    @WithMockUser(username = "nv-self", authorities = AuthoritiesConstants.NHAN_VIEN)
    void nhanVienIsForbiddenEverywhereElse() throws Exception {
        linkSelf();
        mockMvc.perform(get("/api/employees/{id}/sensitive", other.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}", employee.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}", other.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/count")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/export.xlsx")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}/contracts", employee.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}/salary-changes", employee.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}/history", employee.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}/assignments/timeline", employee.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}/documents", employee.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}/account", employee.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employment-contracts")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/salary-changes")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/departments")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/work-sites")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/salary-tools/contract-terms")).andExpect(status().isForbidden());
        mockMvc
            .perform(put("/api/employees/{id}", employee.getId()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "nv-khong-gan", authorities = AuthoritiesConstants.NHAN_VIEN)
    void nhanVienWithoutLinkedProfileGets404() throws Exception {
        newUser("nv-khong-gan", AuthoritiesConstants.NHAN_VIEN);
        mockMvc.perform(get("/api/account/employee")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employees/{id}/sensitive", employee.getId())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "nv-hr", authorities = { AuthoritiesConstants.NHAN_VIEN, AuthoritiesConstants.HR })
    void severalRolesGetTheUnion() throws Exception {
        mockMvc.perform(get("/api/employees")).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}/sensitive", other.getId())).andExpect(status().isOk());
    }

    @Test
    void nhanVienEndToEndWithJwtAndChangesOwnPassword() throws Exception {
        linkSelf();
        String jwt = token("nv-self", PASSWORD);
        mockMvc
            .perform(get("/api/account/employee").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(employee.getId()));
        mockMvc.perform(get("/api/employees").header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
        mockMvc
            .perform(
                post("/api/account/change-password")
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"MatKhauMoi2027\"}")
            )
            .andExpect(status().isOk());
        authenticate("nv-self", "MatKhauMoi2027").andExpect(status().isOk());
    }

    // ---------- Vòng sửa 1 ----------

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void createEmployeeIgnoresUserLink() throws Exception {
        User admin = newUser("quan-tri-3", AuthoritiesConstants.ADMIN);
        String body = mockMvc
            .perform(
                post("/api/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"NV9199\",\"personType\":\"NHAN_VIEN\",\"fullName\":\"Người Mới\",\"user\":{\"id\":" +
                            admin.getId() +
                            "}}"
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userLogin").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long id = om.readTree(body).get("id").asLong();
        assertThat(employeeRepository.findOneWithToOneRelationships(id).orElseThrow().getUser()).isNull();
        assertThat(employeeRepository.findOneByUserId(admin.getId())).isEmpty();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.ADMIN)
    void deletingLinkedUserIsRejected() throws Exception {
        User user = newUser("dang-gan", AuthoritiesConstants.NHAN_VIEN);
        employee.setUser(user);
        employeeRepository.saveAndFlush(employee);
        mockMvc
            .perform(delete("/api/admin/users/{login}", "dang-gan"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.userlinked"));
        assertThat(userRepository.findOneByLogin("dang-gan")).isPresent();
        assertThat(employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow().getUser()).isNotNull();

        newUser("khong-gan", AuthoritiesConstants.NHAN_VIEN);
        mockMvc.perform(delete("/api/admin/users/{login}", "khong-gan")).andExpect(status().isNoContent());
        assertThat(userRepository.findOneByLogin("khong-gan")).isEmpty();
    }

    @Test
    @WithMockUser(username = "doi-mk-ngan", authorities = AuthoritiesConstants.NHAN_VIEN)
    void selfPasswordChangeNeedsAtLeast8Characters() throws Exception {
        User user = newUser("doi-mk-ngan", AuthoritiesConstants.NHAN_VIEN);
        mockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"1234567\"}")
            )
            .andExpect(status().isBadRequest());
        assertThat(passwordEncoder.matches(PASSWORD, userRepository.findById(user.getId()).orElseThrow().getPassword())).isTrue();
        mockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"12345678\"}")
            )
            .andExpect(status().isOk());
    }
    // ---------- Vòng sửa cuối: hồ sơ đã nghỉ, "Hồ sơ của tôi" ----------

    private void markTerminated(Employee e) {
        e.setStatus(EmploymentStatus.DA_NGHI);
        e.setLastWorkingDay(LocalDate.of(2026, 9, 30));
        e.setTerminationDate(LocalDate.of(2026, 10, 1));
        employeeRepository.saveAndFlush(e);
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void cannotCreateAccountForTerminatedEmployee() throws Exception {
        markTerminated(employee);
        createAccount(employee.getId(), "nv-da-nghi", PASSWORD, AuthoritiesConstants.NHAN_VIEN)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeeterminated"));
        assertThat(userRepository.findOneByLogin("nv-da-nghi")).isEmpty();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.HR)
    void linkToTerminatedEmployeeOnlyForLockedAccount() throws Exception {
        markTerminated(employee);
        User active = newUser("tk-dang-mo", AuthoritiesConstants.NHAN_VIEN);
        mockMvc
            .perform(
                put("/api/employees/{id}/account", employee.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + active.getId() + "}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeeterminated"));
        assertThat(employeeRepository.findOneWithToOneRelationships(employee.getId()).orElseThrow().getUser()).isNull();

        User locked = newUser("tk-da-khoa", AuthoritiesConstants.NHAN_VIEN);
        locked.setActivated(false);
        userRepository.saveAndFlush(locked);
        mockMvc
            .perform(
                put("/api/employees/{id}/account", employee.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + locked.getId() + "}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activated").value(false));
        assertThat(userRepository.findById(locked.getId()).orElseThrow().isActivated()).isFalse();
    }

    @Test
    @WithMockUser(username = "hr-co-ho-so", authorities = AuthoritiesConstants.HR)
    void accountReportsLinkedProfileForAnyRole() throws Exception {
        User user = newUser("hr-co-ho-so", AuthoritiesConstants.HR);
        mockMvc.perform(get("/api/account")).andExpect(status().isOk()).andExpect(jsonPath("$.hasEmployeeProfile").value(false));
        employee.setUser(user);
        employeeRepository.saveAndFlush(employee);
        mockMvc.perform(get("/api/account")).andExpect(status().isOk()).andExpect(jsonPath("$.hasEmployeeProfile").value(true));
        mockMvc.perform(get("/api/account/employee")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(employee.getId()));
    }
}

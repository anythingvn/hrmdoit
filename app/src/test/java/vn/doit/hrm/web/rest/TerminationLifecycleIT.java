package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.TerminationScheduler;

/**
 * Cho nghỉ việc (KH1b Task 3): khóa tài khoản, không xóa dữ liệu, hẹn ngày nghỉ (job hằng ngày), hủy lịch, hoàn tác.
 * Hôm nay ban đầu = 27/10/2026 (giờ Việt Nam); đồng hồ đổi được trong từng test.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(TerminationLifecycleIT.MutableClockConfig.class)
class TerminationLifecycleIT {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String PASSWORD = "MatKhauBanDau2026";

    /** Đồng hồ đổi được giữa các bước của một test. */
    static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return VN;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(at(LocalDate.of(2026, 10, 27), 10));
        }
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(VN).toInstant();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MutableClock clock;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private WorkAssignmentRepository workAssignmentRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private SalaryChangeRepository salaryChangeRepository;

    @Autowired
    private TerminationRecordRepository terminationRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TerminationScheduler terminationScheduler;

    private Employee employee;

    private User account;

    private EmploymentContract contract;

    @BeforeEach
    void init() {
        clock.set(at(LocalDate.of(2026, 10, 27), 10));
        account = newUser("nv-nghi", AuthoritiesConstants.NHAN_VIEN);
        employee = employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9300")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Lê Văn Nghỉ")
                .searchText("nv9300 le van nghi")
                .dependants(0)
                .phone("0900000300")
                .status(EmploymentStatus.DANG_LAM_VIEC)
                .joinDate(LocalDate.of(2026, 1, 1))
                .user(account)
        );
        workAssignmentRepository.saveAndFlush(
            new WorkAssignment().type(WorkLocationType.VAN_PHONG).fromDate(LocalDate.of(2026, 1, 1)).employee(employee)
        );
        contract = contractRepository.saveAndFlush(
            new EmploymentContract()
                .contractNo("HD9300")
                .type(ContractType.HDLD_XDTH)
                .termMonths(12)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .position("Kỹ sư")
                .salary(20000000L)
                .salaryBasis(SalaryBasis.GROSS)
                .status(ContractStatus.DA_KY)
                .employee(employee)
        );
        salaryChangeRepository.saveAndFlush(
            new SalaryChange().effectiveDate(LocalDate.of(2026, 7, 1)).amount(22000000L).basis(SalaryBasis.GROSS).employee(employee)
        );
    }

    @AfterEach
    void resetClock() {
        clock.set(at(LocalDate.of(2026, 10, 27), 10));
    }

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

    private static RequestPostProcessor hr() {
        return user("hr-test").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(AuthoritiesConstants.HR));
    }

    private String url(String suffix) {
        return "/api/employees/" + employee.getId() + suffix;
    }

    private static String body(String lastWorkingDay) {
        return "{\"lastWorkingDay\":\"" + lastWorkingDay + "\",\"terminationType\":\"TU_NGHI\",\"terminationReason\":\"Chuyển việc\"}";
    }

    private ResultActions terminate(String lastWorkingDay) throws Exception {
        return mockMvc.perform(post(url("/terminate")).with(hr()).contentType(MediaType.APPLICATION_JSON).content(body(lastWorkingDay)));
    }

    private ResultActions cancel() throws Exception {
        return mockMvc.perform(delete(url("/termination")).with(hr()));
    }

    private ResultActions undo() throws Exception {
        return mockMvc.perform(post(url("/termination/undo")).with(hr()));
    }

    /** Ảnh chụp mọi dữ liệu liên quan để so sánh "không đổi gì". */
    private String state() {
        Employee e = employeeRepository.findById(employee.getId()).orElseThrow();
        StringBuilder sb = new StringBuilder();
        sb
            .append(e.getStatus())
            .append('|')
            .append(e.getLastWorkingDay())
            .append('|')
            .append(e.getTerminationDate())
            .append('|')
            .append(e.getTerminationType())
            .append('|')
            .append(e.getTerminationReason())
            .append('|')
            .append(e.getPhone())
            .append('|')
            .append(e.getNote());
        workAssignmentRepository
            .findByEmployeeId(employee.getId())
            .stream()
            .sorted(Comparator.comparing(WorkAssignment::getId))
            .forEach(w -> sb.append("|A").append(w.getId()).append(' ').append(w.getFromDate()).append(' ').append(w.getToDate()));
        contractRepository
            .findByEmployeeId(employee.getId())
            .stream()
            .sorted(Comparator.comparing(EmploymentContract::getId))
            .forEach(c -> sb.append("|C").append(c.getId()).append(' ').append(c.getStatus()).append(' ').append(c.getEndDate()));
        sb.append("|S").append(salaryChangeRepository.findByEmployeeId(employee.getId()).size());
        sb.append("|U").append(userRepository.findById(account.getId()).orElseThrow().isActivated());
        return sb.toString();
    }

    private Employee reloaded() {
        return employeeRepository.findById(employee.getId()).orElseThrow();
    }

    private boolean accountActive() {
        return userRepository.findById(account.getId()).orElseThrow().isActivated();
    }

    private String token() throws Exception {
        String res = mockMvc
            .perform(
                post("/api/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nv-nghi\",\"password\":\"" + PASSWORD + "\"}")
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode node = om.readTree(res);
        return node.get("id_token").asText();
    }

    // ------------------------------------------------------------------ áp dụng ngay

    @Test
    void pastLastWorkingDayAppliesNowLocksAccountAndDeletesNothing() throws Exception {
        int assignments = workAssignmentRepository.findByEmployeeId(employee.getId()).size();
        int contracts = contractRepository.findByEmployeeId(employee.getId()).size();
        int salaries = salaryChangeRepository.findByEmployeeId(employee.getId()).size();

        terminate("2026-10-26")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_NGHI"))
            .andExpect(jsonPath("$.lastWorkingDay").value("2026-10-26"))
            .andExpect(jsonPath("$.terminationDate").value("2026-10-27"))
            .andExpect(jsonPath("$.scheduledTermination").doesNotExist())
            .andExpect(jsonPath("$.terminationUndoable").value(true));

        assertThat(workAssignmentRepository.findByEmployeeId(employee.getId())).hasSize(assignments);
        assertThat(contractRepository.findByEmployeeId(employee.getId())).hasSize(contracts);
        assertThat(salaryChangeRepository.findByEmployeeId(employee.getId())).hasSize(salaries);
        assertThat(workAssignmentRepository.findByEmployeeId(employee.getId()).get(0).getToDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        EmploymentContract c = contractRepository.findById(contract.getId()).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(ContractStatus.DA_KET_THUC);
        assertThat(c.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(accountActive()).isFalse();
        List<TerminationRecord> records = terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(TerminationRecordStatus.APPLIED);
        assertThat(records.get(0).getAppliedAt()).isNotNull();
        assertThat(records.get(0).getSnapshotJson()).isNotBlank();
    }

    @Test
    void oldJwtRejectedRightAfterTermination() throws Exception {
        String jwt = token();
        mockMvc.perform(get("/api/account/employee").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
        mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());

        terminate("2026-10-26").andExpect(status().isOk());

        mockMvc.perform(get("/api/account/employee").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        mockMvc
            .perform(
                post("/api/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nv-nghi\",\"password\":\"" + PASSWORD + "\"}")
            )
            .andExpect(status().isUnauthorized());

        // Hoàn tác mở lại tài khoản: token cũ dùng lại được.
        undo().andExpect(status().isOk());
        mockMvc.perform(get("/api/account/employee").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ hẹn ngày nghỉ

    @Test
    void futureLastWorkingDaySchedulesWithoutChangingAnything() throws Exception {
        String before = state();
        terminate("2026-10-31")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DANG_LAM_VIEC"))
            .andExpect(jsonPath("$.lastWorkingDay").doesNotExist())
            .andExpect(jsonPath("$.scheduledTermination").value("2026-10-31"))
            .andExpect(jsonPath("$.terminationUndoable").value(false));
        assertThat(state()).isEqualTo(before);
        assertThat(terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId()).get(0).getStatus()).isEqualTo(
            TerminationRecordStatus.SCHEDULED
        );

        mockMvc
            .perform(get("/api/employees").with(hr()).param("code.equals", "NV9300"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].scheduledTermination").value("2026-10-31"));

        // Đã có lịch: cho nghỉ lần nữa phải hủy lịch trước.
        terminate("2026-10-30").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.terminationscheduled"));
    }

    @Test
    void todayAsLastWorkingDayIsScheduledNotApplied() throws Exception {
        terminate("2026-10-27").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DANG_LAM_VIEC"));
        assertThat(accountActive()).isTrue();
    }

    @Test
    void schedulerAppliesDueTerminationIdempotently() throws Exception {
        terminate("2026-10-31").andExpect(status().isOk());

        clock.set(at(LocalDate.of(2026, 10, 31), 23));
        terminationScheduler.applyDue();
        assertThat(reloaded().getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
        assertThat(accountActive()).isTrue();

        clock.set(at(LocalDate.of(2026, 11, 1), 0).plusSeconds(300));
        terminationScheduler.applyDue();
        Employee e = reloaded();
        assertThat(e.getStatus()).isEqualTo(EmploymentStatus.DA_NGHI);
        assertThat(e.getLastWorkingDay()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(e.getTerminationDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(accountActive()).isFalse();
        assertThat(workAssignmentRepository.findByEmployeeId(employee.getId()).get(0).getToDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(contractRepository.findById(contract.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.DA_KET_THUC);
        String afterFirst = state();

        terminationScheduler.applyDue();
        assertThat(state()).isEqualTo(afterFirst);
        assertThat(terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId())).hasSize(1);
        assertThat(terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId()).get(0).getStatus()).isEqualTo(
            TerminationRecordStatus.APPLIED
        );

        mockMvc.perform(get(url("")).with(hr())).andExpect(jsonPath("$.scheduledTermination").doesNotExist());
    }

    @Test
    void schedulerSkipsRecordThatNoLongerPassesRules() throws Exception {
        terminate("2026-10-31").andExpect(status().isOk());
        // Sau khi hẹn, HR thêm một đợt công tác sau ngày làm việc cuối: job không áp dụng, giữ lịch để HR xử lý.
        workAssignmentRepository.saveAndFlush(
            new WorkAssignment()
                .type(WorkLocationType.CONG_TAC)
                .unit("Đà Nẵng")
                .fromDate(LocalDate.of(2026, 11, 3))
                .toDate(LocalDate.of(2026, 11, 5))
                .employee(employee)
        );
        String before = state();
        clock.set(at(LocalDate.of(2026, 11, 1), 1));
        terminationScheduler.applyDue();
        assertThat(state()).isEqualTo(before);
        assertThat(terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId()).get(0).getStatus()).isEqualTo(
            TerminationRecordStatus.SCHEDULED
        );

        // Quá hạn mà chưa áp dụng được: nhãn quá hạn kèm lý do ở hồ sơ và danh sách.
        mockMvc
            .perform(get(url("")).with(hr()))
            .andExpect(jsonPath("$.scheduledTermination").value("2026-10-31"))
            .andExpect(jsonPath("$.scheduledTerminationOverdue").value(true))
            .andExpect(jsonPath("$.scheduledTerminationError").value(containsString("03/11/2026")));
        mockMvc
            .perform(get("/api/employees").with(hr()).param("code.equals", "NV9300"))
            .andExpect(jsonPath("$[0].scheduledTerminationOverdue").value(true))
            .andExpect(jsonPath("$[0].scheduledTerminationError").value(containsString("giai đoạn")));
    }

    @Test
    void notOverdueBeforeLastWorkingDayPasses() throws Exception {
        terminate("2026-10-31").andExpect(status().isOk());
        mockMvc.perform(get(url("")).with(hr())).andExpect(jsonPath("$.scheduledTerminationOverdue").doesNotExist());
    }

    @Test
    void pendingTerminationBlocksNewAssignmentsAndSignedContractsAfterLastDay() throws Exception {
        terminate("2026-10-31").andExpect(status().isOk());
        String before = state();
        mockMvc
            .perform(
                post(url("/assignments"))
                    .with(hr())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"CONG_TAC\",\"unit\":\"Đà Nẵng\",\"fromDate\":\"2026-11-03\",\"toDate\":\"2026-11-05\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.pendingtermination"));
        // Giai đoạn kết thúc sau ngày làm việc cuối sẽ tự tạo giai đoạn "quay về" sau ngày đó → cũng bị chặn.
        mockMvc
            .perform(
                post(url("/assignments"))
                    .with(hr())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"KHACH_HANG\",\"unit\":\"LP Bank\",\"fromDate\":\"2026-10-28\",\"toDate\":\"2026-11-15\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.pendingtermination"));
        String contract =
            "{\"type\":\"HDLD_KXDTH\",\"startDate\":\"2026-11-05\",\"salary\":25000000,\"salaryBasis\":\"GROSS\",\"status\":\"DA_KY\",\"employee\":{\"id\":" +
            employee.getId() +
            "}}";
        mockMvc
            .perform(post("/api/employment-contracts").with(hr()).contentType(MediaType.APPLICATION_JSON).content(contract))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.pendingtermination"))
            .andExpect(jsonPath("$.title").exists());
        assertThat(state()).isEqualTo(before);

        // Trước ngày làm việc cuối vẫn thêm được; HĐ nháp sau ngày đó cũng được.
        mockMvc
            .perform(
                post(url("/assignments"))
                    .with(hr())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"CONG_TAC\",\"unit\":\"Huế\",\"fromDate\":\"2026-10-28\",\"toDate\":\"2026-10-29\"}")
            )
            .andExpect(status().isCreated());
        mockMvc
            .perform(
                post("/api/employment-contracts")
                    .with(hr())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contract.replace("DA_KY", "NHAP"))
            )
            .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ tài khoản: tự cho nghỉ, ADMIN cuối cùng

    @Test
    void cannotTerminateYourself() throws Exception {
        String before = state();
        mockMvc
            .perform(
                post(url("/terminate"))
                    .with(user("nv-nghi").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(AuthoritiesConstants.HR)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("2026-10-26"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.selftermination"));
        assertThat(state()).isEqualTo(before);
    }

    private void makeAccountTheOnlyActiveAdmin() {
        Authority admin = authorityRepository.findById(AuthoritiesConstants.ADMIN).orElseThrow();
        User u = userRepository.findById(account.getId()).orElseThrow();
        u.getAuthorities().add(admin);
        userRepository.saveAndFlush(u);
        for (User other : userRepository.findAll()) {
            if (!other.getId().equals(account.getId()) && other.getAuthorities().contains(admin) && other.isActivated()) {
                other.setActivated(false);
                userRepository.saveAndFlush(other);
            }
        }
    }

    @Test
    void cannotLockTheLastActiveAdmin() throws Exception {
        makeAccountTheOnlyActiveAdmin();
        String before = state();
        terminate("2026-10-26").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.lastadmin"));
        assertThat(state()).isEqualTo(before);
    }

    @Test
    void jobDoesNotLockTheLastActiveAdmin() throws Exception {
        terminate("2026-10-31").andExpect(status().isOk());
        makeAccountTheOnlyActiveAdmin();
        clock.set(at(LocalDate.of(2026, 11, 1), 0).plusSeconds(300));
        terminationScheduler.applyDue();
        assertThat(reloaded().getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
        assertThat(accountActive()).isTrue();
        TerminationRecord r = terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId()).get(0);
        assertThat(r.getStatus()).isEqualTo(TerminationRecordStatus.SCHEDULED);
        assertThat(r.getLastError()).contains("quản trị cuối cùng");
        mockMvc.perform(get(url("")).with(hr())).andExpect(jsonPath("$.scheduledTerminationOverdue").value(true));
    }

    // ------------------------------------------------------------------ hủy lịch (Review Focus 2)

    @Test
    void cancelScheduledTerminationChangesNothing() throws Exception {
        String before = state();
        terminate("2026-10-31").andExpect(status().isOk());
        cancel().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DANG_LAM_VIEC")).andExpect(
            jsonPath("$.scheduledTermination").doesNotExist()
        );
        assertThat(state()).isEqualTo(before);
        assertThat(accountActive()).isTrue();
        assertThat(terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId()).get(0).getStatus()).isEqualTo(
            TerminationRecordStatus.CANCELLED
        );

        // Qua ngày đã hẹn: job không làm gì.
        clock.set(at(LocalDate.of(2026, 11, 2), 0));
        terminationScheduler.applyDue();
        assertThat(state()).isEqualTo(before);

        cancel().andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.noscheduledtermination"));
    }

    // ------------------------------------------------------------------ hoàn tác (Review Focus 1)

    @Test
    void undoRestoresTerminationChangesAndKeepsLaterHrEdits() throws Exception {
        String before = state();
        terminate("2026-10-26").andExpect(status().isOk());

        // HR sửa hồ sơ sau khi cho nghỉ (trường mà việc cho nghỉ không đụng tới).
        mockMvc
            .perform(
                patch(url(""))
                    .with(hr())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + employee.getId() + ",\"phone\":\"0911111111\",\"note\":\"Sửa sau khi nghỉ\"}")
            )
            .andExpect(status().isOk());
        EmploymentContract c = contractRepository.findById(contract.getId()).orElseThrow();
        c.setPosition("Kỹ sư chính");
        contractRepository.saveAndFlush(c);

        undo()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DANG_LAM_VIEC"))
            .andExpect(jsonPath("$.lastWorkingDay").doesNotExist())
            .andExpect(jsonPath("$.terminationUndoable").value(false));

        String expected = before.replace("0900000300|null", "0911111111|Sửa sau khi nghỉ");
        assertThat(state()).isEqualTo(expected);
        Employee e = reloaded();
        assertThat(e.getTerminationType()).isNull();
        assertThat(e.getTerminationReason()).isNull();
        assertThat(contractRepository.findById(contract.getId()).orElseThrow().getPosition()).isEqualTo("Kỹ sư chính");
        assertThat(accountActive()).isTrue();
        assertThat(terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId()).get(0).getStatus()).isEqualTo(
            TerminationRecordStatus.UNDONE
        );

        undo().andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.nothingtoundo"));
    }

    @Test
    void undoAfterScheduledApplyRestores() throws Exception {
        String before = state();
        terminate("2026-10-31").andExpect(status().isOk());
        clock.set(at(LocalDate.of(2026, 11, 1), 0).plusSeconds(300));
        terminationScheduler.applyDue();
        assertThat(reloaded().getStatus()).isEqualTo(EmploymentStatus.DA_NGHI);
        undo().andExpect(status().isOk());
        assertThat(state()).isEqualTo(before);
    }

    @Test
    void undoRejectedWhenTerminatedFieldChangedAfterwards() throws Exception {
        terminate("2026-10-26").andExpect(status().isOk());
        EmploymentContract c = contractRepository.findById(contract.getId()).orElseThrow();
        c.setEndDate(LocalDate.of(2026, 10, 20));
        contractRepository.saveAndFlush(c);
        String before = state();

        undo()
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.undoconflict"))
            .andExpect(jsonPath("$.detail").value(containsString("HD9300")));
        assertThat(state()).isEqualTo(before);
        assertThat(terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId()).get(0).getStatus()).isEqualTo(
            TerminationRecordStatus.APPLIED
        );
    }

    @Test
    void undoRejectedWhenAssignmentAddedAfterLastWorkingDay() throws Exception {
        terminate("2026-10-26").andExpect(status().isOk());
        mockMvc
            .perform(
                post(url("/assignments"))
                    .with(hr())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"CONG_TAC\",\"unit\":\"Đà Nẵng\",\"fromDate\":\"2026-11-03\",\"toDate\":\"2026-11-05\"}")
            )
            .andExpect(status().isCreated());
        String before = state();
        undo()
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.undoconflict"))
            .andExpect(jsonPath("$.detail").value(containsString("03/11/2026")));
        assertThat(state()).isEqualTo(before);
        assertThat(accountActive()).isFalse();
    }

    @Test
    void undoRejectedWhenOverlappingContractAddedAfterTermination() throws Exception {
        terminate("2026-10-26").andExpect(status().isOk());
        contractRepository.saveAndFlush(
            new EmploymentContract()
                .contractNo("HD9301")
                .type(ContractType.HDLD_KXDTH)
                .startDate(LocalDate.of(2026, 11, 15))
                .salary(25000000L)
                .salaryBasis(SalaryBasis.GROSS)
                .status(ContractStatus.DA_KY)
                .employee(employee)
        );
        String before = state();
        undo()
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.undoconflict"))
            .andExpect(jsonPath("$.detail").value(containsString("HD9301")));
        assertThat(state()).isEqualTo(before);
    }

    @Test
    void undoDoesNotReopenAccountThatWasUnlinked() throws Exception {
        terminate("2026-10-26").andExpect(status().isOk());
        Employee e = reloaded();
        e.setUser(null);
        employeeRepository.saveAndFlush(e);
        undo()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DANG_LAM_VIEC"))
            .andExpect(jsonPath("$.terminationNotice").value(containsString("không mở lại")));
        assertThat(accountActive()).isFalse();
    }

    @Test
    void undoNoticeAbsentWhenAccountReopened() throws Exception {
        terminate("2026-10-26").andExpect(status().isOk());
        undo().andExpect(status().isOk()).andExpect(jsonPath("$.terminationNotice").doesNotExist());
        assertThat(accountActive()).isTrue();
    }

    @Test
    void lockedAccountLoginLooksLikeWrongPassword() throws Exception {
        String wrong = mockMvc
            .perform(
                post("/api/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nv-nghi\",\"password\":\"SaiMatKhau123\"}")
            )
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
        terminate("2026-10-26").andExpect(status().isOk());
        String locked = mockMvc
            .perform(
                post("/api/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nv-nghi\",\"password\":\"" + PASSWORD + "\"}")
            )
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode w = om.readTree(wrong);
        JsonNode l = om.readTree(locked);
        assertThat(l.get("title")).isEqualTo(w.get("title"));
        assertThat(l.get("detail")).isEqualTo(w.get("detail"));
        assertThat(l.get("message")).isEqualTo(w.get("message"));
        assertThat(locked).doesNotContainIgnoringCase("activated").doesNotContain("nv-nghi");
    }

    @Test
    void nothingToUndoWhenOnlyScheduledOrNeverTerminated() throws Exception {
        undo().andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.nothingtoundo"));
        terminate("2026-10-31").andExpect(status().isOk());
        undo().andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.nothingtoundo"));
    }

    @Test
    void terminateAgainAfterUndo() throws Exception {
        terminate("2026-10-26").andExpect(status().isOk());
        undo().andExpect(status().isOk());
        terminate("2026-10-25").andExpect(status().isOk()).andExpect(jsonPath("$.lastWorkingDay").value("2026-10-25"));
        assertThat(terminationRecordRepository.findByEmployeeIdOrderByIdDesc(employee.getId())).hasSize(2);
    }

    // ------------------------------------------------------------------ giai đoạn sau ngày làm việc cuối

    @Test
    void futurePlannedPeriodBlocksAndIsNotDeleted() throws Exception {
        mockMvc
            .perform(
                post(url("/assignments"))
                    .with(hr())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"KHACH_HANG\",\"unit\":\"LP Bank\",\"fromDate\":\"2026-10-01\",\"toDate\":\"2027-01-31\"}")
            )
            .andExpect(status().isCreated());
        mockMvc
            .perform(
                post(url("/assignments"))
                    .with(hr())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"CONG_TAC\",\"unit\":\"Đà Nẵng\",\"fromDate\":\"2026-11-10\",\"toDate\":\"2026-11-12\"}")
            )
            .andExpect(status().isCreated());
        String before = state();

        terminate("2026-10-26")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.terminatebeforeassignment"));
        assertThat(state()).isEqualTo(before);

        // Danh sách giai đoạn chặn: giai đoạn "quay về văn phòng" tự tạo được đánh dấu.
        String blockers = mockMvc
            .perform(get(url("/termination/blockers")).with(hr()).param("lastWorkingDay", "2026-10-26"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode nodes = om.readTree(blockers);
        long autoReturnId = -1;
        long tripId = -1;
        for (JsonNode n : nodes) {
            if ("CONG_TAC".equals(n.get("type").asText())) {
                tripId = n.get("id").asLong();
                assertThat(n.get("autoReturn").asBoolean()).isFalse();
            } else {
                autoReturnId = n.get("id").asLong();
                assertThat(n.get("autoReturn").asBoolean()).isTrue();
                assertThat(n.get("fromDate").asText()).isEqualTo("2027-02-01");
            }
        }
        assertThat(autoReturnId).isPositive();
        assertThat(tripId).isPositive();

        mockMvc.perform(delete(url("/assignments/" + tripId)).with(hr())).andExpect(status().isNoContent());
        mockMvc.perform(delete(url("/assignments/" + autoReturnId)).with(hr())).andExpect(status().isNoContent());
        terminate("2026-10-26").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_NGHI"));
        List<WorkAssignment> rows = workAssignmentRepository
            .findByEmployeeId(employee.getId())
            .stream()
            .sorted(Comparator.comparing(WorkAssignment::getFromDate))
            .toList();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getType()).isEqualTo(WorkLocationType.KHACH_HANG);
        assertThat(rows.get(1).getToDate()).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    // ------------------------------------------------------------------ quyền

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantForbidden() throws Exception {
        assertForbiddenForCurrentUser();
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void boardForbidden() throws Exception {
        assertForbiddenForCurrentUser();
    }

    private void assertForbiddenForCurrentUser() throws Exception {
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(body("2026-10-26")))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(url("/termination"))).andExpect(status().isForbidden());
        mockMvc.perform(post(url("/termination/undo"))).andExpect(status().isForbidden());
        mockMvc.perform(get(url("/termination/blockers")).param("lastWorkingDay", "2026-10-26")).andExpect(status().isForbidden());
        assertThat(reloaded().getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanTerminateCancelAndUndo() throws Exception {
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(body("2026-10-31")))
            .andExpect(status().isOk());
        mockMvc.perform(delete(url("/termination"))).andExpect(status().isOk());
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(body("2026-10-26")))
            .andExpect(status().isOk());
        mockMvc.perform(post(url("/termination/undo"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ báo cáo / xuất Excel

    @Test
    void terminatedEmployeeStillInListAndExportWithIntactData() throws Exception {
        terminate("2026-10-26").andExpect(status().isOk());
        mockMvc
            .perform(get("/api/employees").with(hr()).param("code.equals", "NV9300"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].status", hasItem("DA_NGHI")));
        mockMvc
            .perform(get(url("/assignments/timeline")).with(hr()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].from").value("2026-01-01"))
            .andExpect(jsonPath("$[0].to").value("2026-10-26"));

        byte[] xlsx = mockMvc
            .perform(get("/api/employees/export.xlsx").with(hr()).param("code.equals", "NV9300"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            boolean found = false;
            for (Row row : sheet) {
                if (row.getCell(0) != null && "NV9300".equals(row.getCell(0).toString())) {
                    found = true;
                    assertThat(row.getCell(1).toString()).isEqualTo("Lê Văn Nghỉ");
                }
            }
            assertThat(found).isTrue();
        }
    }
}

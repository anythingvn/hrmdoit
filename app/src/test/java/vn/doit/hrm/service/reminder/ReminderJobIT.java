package vn.doit.hrm.service.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hr.leave.DayPart;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.reminder.dto.UpcomingDTO;

/** Email nhắc việc hằng ngày: người nhận, không số tiền, không gửi trùng, gửi lại khi lỗi, ngày làm việc, chạy bù. */
@IntegrationTest
class ReminderJobIT {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "smtp.test");
    }

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private Clock clock;

    @Autowired
    private ReminderJob job;

    @Autowired
    private ReminderSnapshotService snapshots;

    @Autowired
    private ReminderService reminders;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LeaveRequestRepository leaveRepository;

    @Autowired
    private OvertimeRequestRepository overtimeRepository;

    /** email → nội dung đã gửi thành công trong lần chạy (theo thứ tự). */
    private final List<Map.Entry<String, String>> sent = new ArrayList<>();
    private final Set<String> failing = new HashSet<>();
    private final List<Long> employees = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();

    private void at(String isoLocal) {
        ZonedDateTime t = java.time.LocalDateTime.parse(isoLocal).atZone(VN);
        when(clock.instant()).thenReturn(t.toInstant());
        when(clock.getZone()).thenReturn(VN);
    }

    @BeforeEach
    void setUp() throws Exception {
        clean();
        at("2026-10-22T07:30");
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        doAnswer(inv -> {
            MimeMessage m = inv.getArgument(0);
            String to = Arrays.stream(m.getAllRecipients()).map(Address::toString).collect(Collectors.joining(","));
            if (failing.contains(to)) {
                throw new MailSendException("Không gửi được tới " + to);
            }
            sent.add(Map.entry(to, (String) m.getContent()));
            return null;
        })
            .when(mailSender)
            .send(any(MimeMessage.class));
        tx.executeWithoutResult(s -> {
            user("hr-rem", "hr-rem@doit.test", "ROLE_HR");
            user("kt-rem", "kt-rem@doit.test", "ROLE_KE_TOAN");
            user("qt-rem", "qt-rem@doit.test", "ROLE_ADMIN");
            Employee managerA = employee("REM-QL-A", "Quản Lý A", "qla@doit.test", null);
            Employee managerB = employee("REM-QL-B", "Quản Lý B", "hr-rem@doit.test", null);
            contract(employee("REM-1", "Nhân Viên Một", null, managerA), LocalDate.of(2026, 12, 6));
            contract(employee("REM-2", "Nhân Viên Hai", null, managerB), LocalDate.of(2026, 12, 6));
            Employee left = employee("REM-3", "Đã Nghỉ", null, managerA);
            left.setStatus(EmploymentStatus.DA_NGHI);
            contract(left, LocalDate.of(2026, 12, 6));
        });
    }

    @AfterEach
    void clean() {
        reset(mailSender);
        sent.clear();
        failing.clear();
        tx.executeWithoutResult(s -> {
            if (!employees.isEmpty()) {
                String ids = employees.stream().map(String::valueOf).collect(Collectors.joining(","));
                jdbc.update("delete from leave_request where employee_id in (" + ids + ")");
                jdbc.update("delete from overtime_request where employee_id in (" + ids + ")");
            }
            jdbc.update("delete from reminder_delivery");
            jdbc.update("delete from reminder_log");
            jdbc.update("delete from reminder_run");
            for (int i = employees.size() - 1; i >= 0; i--) {
                Long id = employees.get(i);
                contractRepository.findByEmployeeId(id).forEach(contractRepository::delete);
                employeeRepository.findById(id).ifPresent(employeeRepository::delete);
            }
            users.forEach(userRepository::deleteById);
        });
        employees.clear();
        users.clear();
    }

    private String body(String email) {
        return sent.stream().filter(e -> e.getKey().equalsIgnoreCase(email)).map(Map.Entry::getValue).collect(Collectors.joining("\n"));
    }

    private long count(String email) {
        return sent.stream().filter(e -> e.getKey().equalsIgnoreCase(email)).count();
    }

    @Test
    void sendsToStaffAndOwnManagerOnlyWithoutAmounts() {
        ReminderJob.RunResult r = job.run(true);
        assertThat(r.error()).isNull();
        for (String staff : List.of("hr-rem@doit.test", "kt-rem@doit.test", "qt-rem@doit.test")) {
            assertThat(count(staff)).as(staff).isEqualTo(1);
            assertThat(body(staff)).contains("Nhân Viên Một").contains("Nhân Viên Hai");
        }
        assertThat(body("qla@doit.test")).contains("Nhân Viên Một").doesNotContain("Nhân Viên Hai");
        // Quản lý B trùng email với tài khoản HR: chỉ một email.
        assertThat(count("hr-rem@doit.test")).isEqualTo(1);
        assertThat(sent.stream().map(Map.Entry::getValue)).noneMatch(b -> b.contains("26.215.000") || b.contains("26215000"));
        assertThat(sent.stream().map(Map.Entry::getValue)).noneMatch(b -> b.contains("Đã Nghỉ"));
        assertThat(jdbc.queryForObject("select count(*) from reminder_run where error is null and automatic", Integer.class)).isEqualTo(1);

        sent.clear();
        job.run(true);
        assertThat(sent).isEmpty();
    }

    @Test
    void failedRecipientGetsItAgainOthersNotDuplicated() {
        failing.add("kt-rem@doit.test");
        ReminderJob.RunResult first = job.run(true);
        assertThat(first.error()).contains("kt-rem@doit.test");
        assertThat(count("hr-rem@doit.test")).isEqualTo(1);

        failing.clear();
        sent.clear();
        job.run(true);
        assertThat(sent).extracting(Map.Entry::getKey).containsExactly("kt-rem@doit.test");
        assertThat(body("kt-rem@doit.test")).contains("Nhân Viên Một");

        sent.clear();
        job.run(true);
        assertThat(sent).isEmpty();
    }

    @Test
    void noMailOnSaturday() {
        at("2026-10-24T07:30");
        job.scheduled();
        assertThat(sent).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from reminder_run", Integer.class)).isZero();
    }

    @Test
    void catchUpAfterStartLate() {
        at("2026-10-22T09:00");
        job.catchUp();
        assertThat(sent).isNotEmpty();
        sent.clear();
        job.catchUp();
        assertThat(sent).isEmpty();
    }

    @Test
    void catchUpBeforeSevenThirtyDoesNothing() {
        at("2026-10-22T07:00");
        job.catchUp();
        assertThat(sent).isEmpty();
    }

    @Test
    void todayIsVietnamDateNearMidnight() {
        at("2026-10-22T23:50");
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 22));
        job.run(true);
        assertThat(jdbc.queryForObject("select run_date from reminder_run", LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    void terminatedEmployeesHaveNoSnapshot() {
        assertThat(snapshots.load().employees()).extracting(vn.doit.hr.reminder.EmployeeSnapshot::code).doesNotContain("REM-3").contains("REM-1");
    }

    @Test
    void contractAt45DaysIsMailedButNotInUpcoming() {
        assertThat(reminders.upcoming(null)).extracting(UpcomingDTO::employeeCode).doesNotContain("REM-1", "REM-2");
        job.run(true);
        assertThat(body("qla@doit.test")).contains("còn 45 ngày");
    }

    @Test
    void approverGetsPendingRequestsOnlyRoutedToThem() {
        tx.executeWithoutResult(s -> {
            User leadUser = user("lead-rem", "lead-rem@doit.test", "ROLE_NHAN_VIEN");
            user("bgd-rem", "bgd-rem@doit.test", "ROLE_BGD");
            Employee lead = employee("REM-LEAD", "Trưởng Nhóm", null, null);
            lead.setUser(leadUser);
            Employee staff = employee("REM-4", "Nhân Viên Bốn", null, lead);
            leave(staff, ApproverKind.TRUONG_NHOM, lead, LeaveRequestStatus.CHO_DUYET);
            leave(staff, ApproverKind.TRUONG_NHOM, lead, LeaveRequestStatus.TU_CHOI);
            overtime(staff, lead);
            leave(staff, ApproverKind.BGD, null, LeaveRequestStatus.CHO_DUYET);
        });
        job.run(true);
        assertThat(body("lead-rem@doit.test")).contains("Đơn chờ bạn duyệt (2)").contains("Nhân Viên Bốn");
        assertThat(body("bgd-rem@doit.test")).contains("Đơn chờ bạn duyệt (1)");
        // HR, Quản trị duyệt thay được nhưng không nhận danh sách đơn không giao cho họ.
        assertThat(body("hr-rem@doit.test")).doesNotContain("Đơn chờ bạn duyệt");
        assertThat(body("qt-rem@doit.test")).doesNotContain("Đơn chờ bạn duyệt");

        sent.clear();
        job.run(true);
        assertThat(sent).isEmpty();
    }

    @Test
    void noApprovalSectionWhenNothingPending() {
        tx.executeWithoutResult(s -> {
            User leadUser = user("lead-rem", "lead-rem@doit.test", "ROLE_NHAN_VIEN");
            Employee lead = employee("REM-LEAD", "Trưởng Nhóm", null, null);
            lead.setUser(leadUser);
            Employee staff = employee("REM-4", "Nhân Viên Bốn", null, lead);
            leave(staff, ApproverKind.TRUONG_NHOM, lead, LeaveRequestStatus.DA_DUYET);
        });
        job.run(true);
        assertThat(sent.stream().map(Map.Entry::getValue)).noneMatch(b -> b.contains("Đơn chờ bạn duyệt"));
    }

    private void leave(Employee e, ApproverKind kind, Employee approver, LeaveRequestStatus status) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(e);
        r.setType(LeaveRequestType.PHEP_NAM);
        r.setFromDate(LocalDate.of(2026, 10, 26));
        r.setToDate(LocalDate.of(2026, 10, 26));
        r.setFirstPart(DayPart.CA_NGAY);
        r.setLastPart(DayPart.CA_NGAY);
        r.setDays(BigDecimal.ONE);
        r.setStatus(status);
        r.setApproverKind(kind);
        r.setApproverEmployee(approver);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        leaveRepository.save(r);
    }

    private void overtime(Employee e, Employee approver) {
        OvertimeRequest r = new OvertimeRequest();
        r.setEmployee(e);
        r.setWorkDate(LocalDate.of(2026, 10, 24));
        r.setStartTime(LocalTime.of(8, 0));
        r.setEndTime(LocalTime.of(10, 0));
        r.setPlannedHours(new BigDecimal("2"));
        r.setRate(200);
        r.setNight(false);
        r.setStatus(OvertimeStatus.CHO_DUYET);
        r.setApproverKind(ApproverKind.TRUONG_NHOM);
        r.setApproverEmployee(approver);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        overtimeRepository.save(r);
    }

    private User user(String login, String email, String role) {
        User u = new User();
        u.setLogin(login);
        u.setEmail(email);
        u.setActivated(true);
        u.setLangKey("vi");
        u.setPassword(passwordEncoder.encode("MatKhau@2026"));
        u.setAuthorities(Set.of(authorityRepository.findById(role).orElseThrow()));
        userRepository.save(u);
        users.add(u.getId());
        return u;
    }

    private Employee employee(String code, String name, String email, Employee manager) {
        Employee e = employeeRepository.save(
            new Employee()
                .code(code)
                .fullName(name)
                .email(email)
                .personType(PersonType.NHAN_VIEN)
                .status(EmploymentStatus.DANG_LAM_VIEC)
                .dependants(0)
                .manager(manager)
        );
        employees.add(e.getId());
        return e;
    }

    private void contract(Employee e, LocalDate end) {
        contractRepository.save(
            new EmploymentContract()
                .contractNo("HĐ-" + e.getCode())
                .type(ContractType.HDLD_XDTH)
                .termMonths(12)
                .startDate(end.minusMonths(12).plusDays(1))
                .endDate(end)
                .salary(26_215_000L)
                .salaryBasis(SalaryBasis.NET)
                .status(ContractStatus.DA_KY)
                .employee(e)
        );
    }
}

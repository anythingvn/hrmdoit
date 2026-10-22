package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.DayPart;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.domain.AssignmentDecisionRecord;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmployeeDocument;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.EmploymentPeriod;
import vn.doit.hrm.domain.GeneratedDocument;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.LeaveRequestTerminationChange;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.PayslipDelivery;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.SensitiveAccessLog;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.DocumentOwnerType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.GeneratedFileKind;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.domain.enumeration.PayslipDeliveryStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AllowanceRepository;
import vn.doit.hrm.repository.AssignmentDecisionRecordRepository;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeDocumentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.EmploymentPeriodRepository;
import vn.doit.hrm.repository.GeneratedDocumentRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.LeaveRequestTerminationChangeRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayslipDeliveryRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.repository.SensitiveAccessLogRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.service.EmployeeDeletionService;
import vn.doit.hrm.service.FileStorageService;
import vn.doit.hrm.service.StoredFileMissingException;
import vn.doit.hrm.service.payroll.PayrollTestData;

/**
 * Xóa hẳn nhân viên (chỉ login admin). Không chạy trong transaction của test: giao dịch xóa phải commit thật (file chỉ bị xóa sau
 * commit, Envers chỉ ghi {@code _aud} khi commit) và rollback thật khi lỗi giữa chừng. Dữ liệu được dọn ở {@link #clean()}.
 */
@IntegrationTest
@AutoConfigureMockMvc
class EmployeeDeletionIT {

    private static final RequestPostProcessor SYSTEM_ADMIN = user("admin").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor OTHER_ADMIN = user("admin2").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor HR = user("hr-del").authorities(() -> "ROLE_HR");

    private static final String APPROVED = "2026-04";
    private static final String OPEN = "2026-05";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-employee-deletion-it");
        registry.add("application.storage.root", storage::toString);
    }

    @MockitoSpyBean
    private LeaveLedgerEntryRepository ledgerRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private SalaryChangeRepository salaryChangeRepository;

    @Autowired
    private AllowanceRepository allowanceRepository;

    @Autowired
    private WorkAssignmentRepository workAssignmentRepository;

    @Autowired
    private AssignmentDecisionRecordRepository decisionRepository;

    @Autowired
    private GeneratedDocumentRepository generatedDocumentRepository;

    @Autowired
    private EmployeeDocumentRepository documentRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private OvertimeRequestRepository overtimeRepository;

    @Autowired
    private TerminationRecordRepository terminationRepository;

    @Autowired
    private EmploymentPeriodRepository employmentPeriodRepository;

    @Autowired
    private LeaveRequestTerminationChangeRepository terminationChangeRepository;

    @Autowired
    private PayslipDeliveryRepository deliveryRepository;

    @Autowired
    private PayrollPeriodRepository periodRepository;

    @Autowired
    private SensitiveAccessLogRepository accessLogRepository;

    @Autowired
    private FileStorageService fileStorage;

    @Autowired
    private EmployeeDeletionService deletionService;

    private PayrollTestData payroll;

    private final List<Long> tracked = new ArrayList<>();
    private final List<Long> departments = new ArrayList<>();
    private final Set<String> codes = new LinkedHashSet<>();
    private Map<String, Long> payrollIds;
    private static int seq;

    /** Hồ sơ X đầy đủ và các file của nó. */
    private record Person(Long id, String code, String fullName, String login, Long userId, String idNumber, List<String> files) {}

    @BeforeEach
    void seedPayrollProfiles() {
        payroll = new PayrollTestData(employeeRepository, jdbc, tx);
        update("delete from payslip_delivery");
        payroll.clean();
        payrollIds = new HashMap<>(payroll.seedProfiles());
        payrollIds.put("TT2601", payroll.seedService("TT2601", "Mai Văn Phúc"));
        payrollIds.put("HDDV9001", payroll.seedService("HDDV9001", "Tạ Văn Quý"));
        payrollIds.put("HDDV9002", payroll.seedService("HDDV9002", "Hồ Thị Sen"));
    }

    @AfterEach
    void clean() {
        org.mockito.Mockito.reset(ledgerRepository);
        update("delete from payslip_delivery");
        payroll.clean();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
        try {
            for (Long id : tracked) {
                String code = jdbc.query("select code from employee where id = ?", rs -> rs.next() ? rs.getString(1) : null, id);
                if (code == null) {
                    continue;
                }
                update("update employee set user_id = null where id = ? and user_id = (select id from jhi_user where login = 'admin')", id);
                update("update employee set archived_at = now() where id = ? and archived_at is null", id);
                tx.executeWithoutResult(s -> deletionService.delete(id, code, "Dọn dữ liệu test"));
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
        departments.forEach(id -> update("delete from department where id = ?", id));
        departments.clear();
        for (String code : codes) {
            update("delete from employee_deletion_log where employee_code = ?", code);
        }
        codes.clear();
        tracked.clear();
    }

    // ------------------------------------------------------------------ dữ liệu

    private String nextCode() {
        String code = "XH" + (System.nanoTime() % 100000) + ++seq;
        codes.add(code);
        return code;
    }

    private Employee simpleEmployee(String name) {
        String code = nextCode();
        Employee e = tx.execute(s ->
            employeeRepository.save(
                new Employee()
                    .code(code)
                    .personType(PersonType.NHAN_VIEN)
                    .fullName(name)
                    .searchText(vn.doit.hrm.service.VietnameseText.fold(code + " " + name))
                    .dependants(0)
                    .status(EmploymentStatus.DANG_LAM_VIEC)
                    .joinDate(LocalDate.of(2024, 3, 1))
            )
        );
        tracked.add(e.getId());
        return e;
    }

    private String store(String folder, String name, String content) {
        return fileStorage.store(folder, name, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    /** X đang làm việc, có tài khoản; chưa có dữ liệu con. */
    private Person activeX() {
        String code = nextCode();
        String login = "xh-" + code.toLowerCase();
        String idNumber = "0790%08d".formatted(Math.abs(System.nanoTime()) % 100000000L);
        Long[] ids = tx.execute(s -> {
            User u = new User();
            u.setLogin(login);
            u.setActivated(true);
            u.setLangKey("vi");
            u.setEmail(login + "@doit.vn");
            u.setPassword("$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234");
            u = userRepository.save(u);
            Employee e = employeeRepository.save(
                new Employee()
                    .code(code)
                    .personType(PersonType.NHAN_VIEN)
                    .fullName("Nguyễn Văn Xóa")
                    .searchText(vn.doit.hrm.service.VietnameseText.fold(code + " Nguyễn Văn Xóa"))
                    .dependants(1)
                    .email(login + "@doit.vn")
                    .phone("0901234567")
                    .idNumber(idNumber)
                    .taxCode("8000123456")
                    .bankAccount("1903456789012")
                    .bankName("Techcombank")
                    .residence("12 Ngõ Huế, Hai Bà Trưng, Hà Nội")
                    .status(EmploymentStatus.DANG_LAM_VIEC)
                    .joinDate(LocalDate.of(2024, 3, 1))
                    .user(u)
            );
            return new Long[] { e.getId(), u.getId() };
        });
        update("insert into jhi_user_authority (user_id, authority_name) values (?, 'ROLE_NHAN_VIEN')", ids[1]);
        tracked.add(ids[0]);
        return new Person(ids[0], code, "Nguyễn Văn Xóa", login, ids[1], idNumber, new ArrayList<>());
    }

    /** Kỳ {@code period} với dòng 18 gắn X; duyệt nếu {@code approve}. */
    private void payrollWith(Person x, String period, boolean approve) throws Exception {
        Map<String, Long> ids = new HashMap<>(payrollIds);
        ids.put("DOIT2515", x.id());
        Integer draft = PayrollFlow.readyDraft(mockMvc, period, ids);
        mockMvc.perform(post("/api/payroll/imports/{id}/confirm", draft).with(PayrollFlow.KE_TOAN)).andExpect(status().isCreated());
        if (approve) {
            mockMvc.perform(post("/api/payroll/periods/{p}/approve", period).with(PayrollFlow.ADMIN)).andExpect(status().isOk());
        }
    }

    /** Mọi bản ghi con của X (qua JPA, để Envers ghi lịch sử), sửa hồ sơ một lần, rồi cho nghỉ và lưu trữ. */
    private void fillAndArchive(Person x) {
        Long id = x.id();
        String docPath = store("employees/" + id, "cccd.pdf", "CCCD scan");
        String sickPath = store("employees/" + id, "giay-om.pdf", "Giấy ốm");
        String docxPath = store("generated", "hd.docx", "HĐ docx");
        String signedPath = store("generated", "hd-ky.pdf", "HĐ đã ký");
        String decisionPath = store("generated", "qd.docx", "QĐ docx");
        x.files().addAll(List.of(docPath, sickPath, docxPath, signedPath, decisionPath));
        tx.executeWithoutResult(s -> {
            Employee e = employeeRepository.findById(id).orElseThrow();
            EmploymentContract c = contractRepository.save(
                new EmploymentContract()
                    .contractNo("HD-" + x.code())
                    .type(ContractType.HDLD_XDTH)
                    .termMonths(12)
                    .startDate(LocalDate.of(2024, 3, 1))
                    .endDate(LocalDate.of(2025, 2, 28))
                    .position("Kỹ sư phần mềm")
                    .salary(25000000L)
                    .salaryBasis(SalaryBasis.GROSS)
                    .status(ContractStatus.DA_KET_THUC)
                    .employee(e)
            );
            GeneratedDocument docx = generatedDocumentRepository.save(
                new GeneratedDocument()
                    .ownerType(DocumentOwnerType.CONTRACT)
                    .ownerId(c.getId())
                    .kind(GeneratedFileKind.DOCX)
                    .storagePath(docxPath)
                    .fileName("HD.docx")
                    .createdBy("hr")
                    .createdAt(Instant.now())
            );
            generatedDocumentRepository.save(
                new GeneratedDocument()
                    .ownerType(DocumentOwnerType.CONTRACT)
                    .ownerId(c.getId())
                    .kind(GeneratedFileKind.SIGNED)
                    .storagePath(signedPath)
                    .fileName("HD-ky.pdf")
                    .sourceDocumentId(docx.getId())
                    .createdBy("hr")
                    .createdAt(Instant.now())
            );
            salaryChangeRepository.save(
                new SalaryChange()
                    .effectiveDate(LocalDate.of(2025, 1, 1))
                    .amount(28000000L)
                    .basis(SalaryBasis.GROSS)
                    .employee(e)
            );
            allowanceRepository.save(
                new Allowance()
                    .name("Ăn trưa")
                    .amountPerMonth(730000L)
                    .fromDate(LocalDate.of(2024, 3, 1))
                    .employee(e)
            );
            WorkAssignment wa = workAssignmentRepository.save(
                new WorkAssignment()
                    .type(WorkLocationType.KHACH_HANG)
                    .unit("Ngân hàng A")
                    .fromDate(LocalDate.of(2025, 1, 1))
                    .employee(e)
            );
            AssignmentDecisionRecord d = new AssignmentDecisionRecord();
            d.setDecisionNo("QD-" + x.code());
            d.setEmployeeId(id);
            d.setAssignmentId(wa.getId());
            d.setType(WorkLocationType.KHACH_HANG);
            d.setUnit("Ngân hàng A");
            d.setFromDate(LocalDate.of(2025, 1, 1));
            d.setSignDate(LocalDate.of(2024, 12, 20));
            d.setCreatedAt(Instant.now());
            d = decisionRepository.save(d);
            generatedDocumentRepository.save(
                new GeneratedDocument()
                    .ownerType(DocumentOwnerType.DECISION)
                    .ownerId(d.getId())
                    .kind(GeneratedFileKind.DOCX)
                    .storagePath(decisionPath)
                    .fileName("QD.docx")
                    .createdBy("hr")
                    .createdAt(Instant.now())
            );
            documentRepository.save(
                new EmployeeDocument()
                    .employee(e)
                    .fileName("cccd.pdf")
                    .contentType("application/pdf")
                    .sizeBytes(9L)
                    .storagePath(docPath)
                    .uploadedBy("hr")
                    .uploadedAt(Instant.now())
            );
            EmployeeDocument sick = new EmployeeDocument()
                .employee(e)
                .fileName("giay-om.pdf")
                .contentType("application/pdf")
                .sizeBytes(8L)
                .storagePath(sickPath)
                .uploadedBy(x.login())
                .uploadedAt(Instant.now());
            sick.setSickCertificate(true);
            sick = documentRepository.save(sick);
            LeaveRequest leave = leave(e, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2), LeaveRequestType.PHEP_NAM, null);
            LeaveRequest om = leave(e, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3), LeaveRequestType.OM, null);
            om.setSickDocId(sick.getId());
            leaveRequestRepository.save(om);
            OvertimeRequest ot = new OvertimeRequest();
            ot.setEmployee(e);
            ot.setWorkDate(LocalDate.of(2026, 7, 10));
            ot.setStartTime(LocalTime.of(18, 0));
            ot.setEndTime(LocalTime.of(20, 0));
            ot.setPlannedHours(new BigDecimal("2"));
            ot.setActualHours(new BigDecimal("2"));
            ot.setRate(150);
            ot.setNight(false);
            ot.setStatus(OvertimeStatus.HOAN_TAT);
            ot.setCompensation(OvertimeCompensation.TRA_TIEN);
            ot.setApproverKind(ApproverKind.TRUONG_PHONG);
            ot.setCreatedAt(Instant.now());
            ot.setSubmittedBy(x.login());
            overtimeRepository.save(ot);
            ledgerRepository.save(
                new LeaveLedgerEntry(
                    id,
                    Pool.PHEP_NAM,
                    Bucket.NAM_NAY,
                    new BigDecimal("1.00"),
                    LocalDate.of(2026, 1, 31),
                    LedgerReason.CONG_THANG,
                    "MONTH",
                    202601L,
                    null,
                    "system",
                    Instant.now()
                )
            );
            ledgerRepository.save(
                new LeaveLedgerEntry(
                    id,
                    Pool.PHEP_NAM,
                    Bucket.NAM_NAY,
                    new BigDecimal("-3.00"),
                    LocalDate.of(2026, 9, 28),
                    LedgerReason.DUYET_DON,
                    "LEAVE_REQUEST",
                    leave.getId(),
                    null,
                    "hr",
                    Instant.now()
                )
            );
            TerminationRecord t = new TerminationRecord();
            t.setEmployeeId(id);
            t.setLastWorkingDay(LocalDate.of(2026, 9, 30));
            t.setTerminationDate(LocalDate.of(2026, 10, 1));
            t.setTerminationType(TerminationType.TU_NGHI);
            t.setStatus(TerminationRecordStatus.APPLIED);
            t = terminationRepository.save(t);
            terminationChangeRepository.save(
                new LeaveRequestTerminationChange(
                    leave.getId(),
                    t.getId(),
                    Instant.now(),
                    LocalDate.of(2026, 10, 2),
                    DayPart.CA_NGAY,
                    new BigDecimal("5")
                )
            );
            EmploymentPeriod period = new EmploymentPeriod();
            period.setEmployeeId(id);
            period.setJoinDate(LocalDate.of(2022, 1, 1));
            period.setLastWorkingDay(LocalDate.of(2023, 6, 30));
            period.setTerminationType(TerminationType.TU_NGHI);
            period.setCreatedAt(Instant.now());
            employmentPeriodRepository.save(period);
            periodRepository.findByPeriod(APPROVED).ifPresent(pp -> {
                PayslipDelivery delivery = new PayslipDelivery();
                delivery.setPeriod(pp);
                delivery.setVersion(1);
                delivery.setEmployee(e);
                delivery.setEmail(x.login() + "@doit.vn");
                delivery.setStatus(PayslipDeliveryStatus.SENT);
                delivery.setRequestedBy("ketoan");
                delivery.setRequestedAt(Instant.now());
                delivery.setSentAt(Instant.now());
                deliveryRepository.save(delivery);
            });
            accessLogRepository.save(new SensitiveAccessLog(id, "hr-del", Instant.now()));
            e.setPhone("0907654321");
        });
        tx.executeWithoutResult(s -> {
            Employee e = employeeRepository.findById(id).orElseThrow();
            e.setStatus(EmploymentStatus.DA_NGHI);
            e.setLastWorkingDay(LocalDate.of(2026, 9, 30));
            e.setTerminationDate(LocalDate.of(2026, 10, 1));
            e.setTerminationType(TerminationType.TU_NGHI);
            e.setTerminationReason("Chuyển việc");
            e.getUser().setActivated(false);
        });
        archive(id);
    }

    private LeaveRequest leave(Employee e, LocalDate from, LocalDate to, LeaveRequestType type, Employee approver) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(e);
        r.setType(type);
        r.setFromDate(from);
        r.setToDate(to);
        r.setFirstPart(DayPart.CA_NGAY);
        r.setLastPart(DayPart.CA_NGAY);
        r.setDays(new BigDecimal(to.toEpochDay() - from.toEpochDay() + 1));
        r.setStatus(LeaveRequestStatus.DA_DUYET);
        r.setApproverKind(ApproverKind.TRUONG_PHONG);
        r.setApproverEmployee(approver);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        return leaveRequestRepository.save(r);
    }

    private void archive(Long id) {
        tx.executeWithoutResult(s -> {
            Employee e = employeeRepository.findById(id).orElseThrow();
            e.setArchivedAt(Instant.now());
            e.setArchivedBy("hr");
            e.setArchiveNote("Nghỉ từ tháng 9");
        });
    }

    private ResultActions deleteAs(RequestPostProcessor who, Long id, String confirmCode, String reason) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("confirmCode", confirmCode);
        body.put("reason", reason);
        return mockMvc.perform(
            delete("/api/employees/{id}", id).with(who).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))
        );
    }

    private boolean fileExists(String path) {
        try {
            fileStorage.load(path);
            return true;
        } catch (StoredFileMissingException e) {
            return false;
        }
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /** Id lịch sử (Envers) của mọi bản ghi con của X, theo bảng. */
    private Map<String, Set<Long>> auditIds(Long id) {
        Map<String, Set<Long>> result = new LinkedHashMap<>();
        for (String table : EmployeeDeletionService.AUDITED_CHILDREN) {
            result.put(
                table,
                new LinkedHashSet<>(jdbc.queryForList("select id from " + table + "_aud where employee_id = ?", Long.class, id))
            );
        }
        return result;
    }

    /** Ảnh chụp mọi dữ liệu con của X để so trước/sau khi rollback. */
    private Map<String, Long> footprint(Person x) {
        Map<String, Long> m = new TreeMap<>();
        for (String col : EmployeeDeletionService.DELETED_COLUMNS) {
            String[] tc = col.split("\\.");
            m.put(col, count("select count(*) from " + tc[0] + " where " + tc[1] + " = ?", x.id()));
        }
        for (String table : EmployeeDeletionService.AUDITED_CHILDREN) {
            m.put(table + "_aud", count("select count(*) from " + table + "_aud where employee_id = ?", x.id()));
        }
        m.put("employee", count("select count(*) from employee where id = ?", x.id()));
        m.put("employee_aud", count("select count(*) from employee_aud where id = ?", x.id()));
        m.put("sensitive_access_log", count("select count(*) from sensitive_access_log where employee_id = ?", x.id()));
        m.put("payroll_line", count("select count(*) from payroll_line where employee_id = ?", x.id()));
        m.put(
            "generated_document",
            count(
                "select count(*) from generated_document where storage_path in (?, ?, ?)",
                x.files().get(2),
                x.files().get(3),
                x.files().get(4)
            )
        );
        m.put("jhi_user", count("select count(*) from jhi_user where id = ?", x.userId()));
        m.put("employee_deletion_log", count("select count(*) from employee_deletion_log where employee_code = ?", x.code()));
        return m;
    }

    /** Tổng mọi cột của mọi dòng (theo API) và tổng chính thức của kỳ. */
    private Map<String, BigDecimal> periodTotals(String period) throws Exception {
        String res = mockMvc
            .perform(get("/api/payroll/periods/{p}/versions/1/lines", period).with(PayrollFlow.KE_TOAN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Map<String, BigDecimal> sums = new TreeMap<>();
        for (JsonNode line : om.readTree(res)) {
            line.get("amounts")
                .properties()
                .forEach(f -> sums.merge(f.getKey(), new BigDecimal(f.getValue().asText()), BigDecimal::add));
            for (String total : List.of("thucNhan", "tongChiPhi", "thueTncn", "tongBhNld")) {
                if (!line.get(total).isNull()) {
                    sums.merge(total, new BigDecimal(line.get(total).asText()), BigDecimal::add);
                }
            }
        }
        sums.put("lines", BigDecimal.valueOf(om.readTree(res).size()));
        return sums;
    }

    private byte[] periodFile(String period) throws Exception {
        return mockMvc
            .perform(get("/api/payroll/periods/{p}/versions/1/file", period).with(PayrollFlow.KE_TOAN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    }

    // ------------------------------------------------------------------ test

    @Test
    void onlySystemAdmin() throws Exception {
        Person x = activeX();
        fillAndArchive(x);
        deleteAs(OTHER_ADMIN, x.id(), x.code(), "Hết thời hạn lưu")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.deleterequiressystemadmin"));
        deleteAs(HR, x.id(), x.code(), "Hết thời hạn lưu")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.deleterequiressystemadmin"));
        // Dữ liệu không hợp lệ (lý do quá dài) không làm lộ 400 với người không có quyền: quyền được kiểm tra trước.
        deleteAs(HR, x.id(), x.code() + "x".repeat(60), "x".repeat(600))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.deleterequiressystemadmin"));
        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "x".repeat(501))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.deletereasontoolong"));
        assertThat(count("select count(*) from employee where id = ?", x.id())).isEqualTo(1);
    }

    @Test
    void requiresArchived() throws Exception {
        Person x = activeX();
        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Tạo nhầm")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.deletenotarchived"));
    }

    @Test
    void blockedByOpenPayroll() throws Exception {
        Person x = activeX();
        payrollWith(x, OPEN, false);
        // Bản nháp import đang làm có dòng gắn X.
        Integer draft = PayrollFlow.upload(mockMvc, "2026-06");
        PayrollFlow.map(mockMvc, draft, 18, x.id());
        fillAndArchive(x);

        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Hết thời hạn lưu")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.deleteblocked"))
            .andExpect(jsonPath("$.detail").value(containsString("Kỳ lương 05/2026")))
            .andExpect(jsonPath("$.detail").value(containsString("Bản nháp import bảng lương 06/2026")));
        assertThat(count("select count(*) from employee where id = ?", x.id())).isEqualTo(1);

        // Lượt gửi phiếu lương đang chờ cũng chặn.
        update("delete from payroll_import_mapping where employee_id = ?", x.id());
        update("update payroll_period set status = 'DA_DUYET' where period = ?", OPEN);
        tx.executeWithoutResult(s -> {
            PayslipDelivery d = new PayslipDelivery();
            d.setPeriod(periodRepository.findByPeriod(OPEN).orElseThrow());
            d.setVersion(1);
            d.setEmployee(employeeRepository.findById(x.id()).orElseThrow());
            d.setStatus(PayslipDeliveryStatus.PENDING);
            d.setRequestedAt(Instant.now());
            deliveryRepository.save(d);
        });
        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Hết thời hạn lưu")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.deleteblocked"))
            .andExpect(jsonPath("$.detail").value(containsString("Phiếu lương 05/2026 đang chờ gửi")));
    }

    @Test
    void confirmMismatch() throws Exception {
        Person x = activeX();
        fillAndArchive(x);
        deleteAs(SYSTEM_ADMIN, x.id(), x.code() + "1", "Hết thời hạn lưu")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.deleteconfirmmismatch"));
        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "   ")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.deletereasonrequired"));
        assertThat(count("select count(*) from employee where id = ?", x.id())).isEqualTo(1);
    }

    @Test
    void deleteKeepsApprovedPayroll() throws Exception {
        Person x = activeX();
        payrollWith(x, APPROVED, true);
        fillAndArchive(x);
        Map<String, BigDecimal> totalsBefore = periodTotals(APPROVED);
        byte[] fileBefore = periodFile(APPROVED);
        Map<String, Object> lineBefore = jdbc.queryForMap(
            "select id, file_code, file_name, amounts, thuc_nhan, tong_chi_phi from payroll_line where employee_id = ?",
            x.id()
        );

        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Hết thời hạn lưu hồ sơ").andExpect(status().isNoContent());

        assertThat(periodTotals(APPROVED)).isEqualTo(totalsBefore);
        assertThat(periodFile(APPROVED)).isEqualTo(fileBefore);
        Map<String, Object> lineAfter = jdbc.queryForMap(
            "select employee_id, file_code, file_name, amounts, thuc_nhan, tong_chi_phi from payroll_line where id = ?",
            lineBefore.get("id")
        );
        assertThat(lineAfter.get("employee_id")).isNull();
        assertThat(lineAfter.get("file_code")).isEqualTo(lineBefore.get("file_code"));
        assertThat(lineAfter.get("file_name")).isEqualTo(lineBefore.get("file_name"));
        assertThat(lineAfter.get("amounts")).isEqualTo(lineBefore.get("amounts"));
        assertThat(lineAfter.get("thuc_nhan")).isEqualTo(lineBefore.get("thuc_nhan"));
        mockMvc
            .perform(get("/api/payroll/periods/{p}/versions/1/lines", APPROVED).with(PayrollFlow.KE_TOAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.excelRow == 18)].employeeCode").value(hasItem(lineBefore.get("file_code"))))
            .andExpect(jsonPath("$[?(@.excelRow == 18)].employeeName").value(hasItem(containsString("(đã xóa hồ sơ)"))))
            .andExpect(jsonPath("$[?(@.excelRow == 18)].employeeId").value(hasItem(nullValue())));
        mockMvc.perform(get("/api/payroll/periods/{p}", APPROVED).with(PayrollFlow.KE_TOAN)).andExpect(status().isOk());
    }

    @Test
    void deleteRemovesEverythingElse() throws Exception {
        Person x = activeX();
        payrollWith(x, APPROVED, true);
        fillAndArchive(x);
        Map<String, Set<Long>> audit = auditIds(x.id());
        // Dữ liệu thật: mỗi loại bản ghi con có lịch sử.
        audit.forEach((table, ids) ->
            assertThat(ids)
                .as(table + "_aud")
                .isNotEmpty()
        );
        List<Long> generated = jdbc.queryForList(
            "select id from generated_document where storage_path in (?, ?, ?)",
            Long.class,
            x.files().get(2),
            x.files().get(3),
            x.files().get(4)
        );
        assertThat(generated).hasSize(3);
        List<Long> leaveIds = jdbc.queryForList("select id from leave_request where employee_id = ?", Long.class, x.id());
        assertThat(
            count("select count(*) from leave_request_termination_change where leave_request_id in (" + join(leaveIds) + ")")
        ).isEqualTo(1);
        assertThat(count("select count(*) from employee_aud where id = ?", x.id())).isGreaterThanOrEqualTo(3);
        x.files().forEach(f -> assertThat(fileExists(f)).as(f).isTrue());
        String otherEmail = "nguoi-khac-" + x.code().toLowerCase() + "@doit.vn";
        update(
            "insert into reminder_delivery (event_key, step, email, sent_at) values (?, 0, ?, now())",
            "DEL-IT:" + x.code(),
            (x.login() + "@doit.vn").toUpperCase()
        );
        update("insert into reminder_delivery (event_key, step, email, sent_at) values (?, 0, ?, now())", "DEL-IT:" + x.code(), otherEmail);

        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Hết thời hạn lưu hồ sơ").andExpect(status().isNoContent());

        assertThat(count("select count(*) from reminder_delivery where lower(email) = ?", x.login() + "@doit.vn")).isZero();
        assertThat(count("select count(*) from reminder_delivery where email = ?", otherEmail)).isEqualTo(1);
        update("delete from reminder_delivery where event_key = ?", "DEL-IT:" + x.code());
        for (String col : EmployeeDeletionService.DELETED_COLUMNS) {
            String[] tc = col.split("\\.");
            assertThat(count("select count(*) from " + tc[0] + " where " + tc[1] + " = ?", x.id()))
                .as(col)
                .isZero();
        }
        assertThat(count("select count(*) from sensitive_access_log where employee_id = ?", x.id())).isZero();
        assertThat(
            count("select count(*) from leave_request_termination_change where leave_request_id in (" + join(leaveIds) + ")")
        ).isZero();
        assertThat(count("select count(*) from generated_document where id in (" + join(generated) + ")")).isZero();
        assertThat(count("select count(*) from generated_document_aud where id in (" + join(generated) + ")")).isZero();
        assertThat(count("select count(*) from employee where id = ?", x.id())).isZero();
        assertThat(count("select count(*) from employee_aud where id = ?", x.id())).isZero();
        audit.forEach((table, ids) ->
            assertThat(count("select count(*) from " + table + "_aud where id in (" + join(ids) + ")"))
                .as(table + "_aud")
                .isZero()
        );
        assertThat(count("select count(*) from payroll_line_aud where employee_id = ?", x.id())).isZero();
        x.files().forEach(f -> assertThat(fileExists(f)).as(f).isFalse());
        assertThat(count("select count(*) from jhi_user where id = ?", x.userId())).isZero();
        assertThat(count("select count(*) from jhi_user_authority where user_id = ?", x.userId())).isZero();
        assertThat(count("select count(*) from jhi_user where login = 'admin'")).isEqualTo(1);

        List<Map<String, Object>> log = jdbc.queryForList("select * from employee_deletion_log where employee_code = ?", x.code());
        assertThat(log).hasSize(1);
        Map<String, Object> row = log.get(0);
        assertThat(row.get("deleted_by")).isEqualTo("admin");
        assertThat(row.get("reason")).isEqualTo("Hết thời hạn lưu hồ sơ");
        assertThat(row.get("employee_name")).isEqualTo(x.fullName());
        assertThat(row.get("deleted_at")).isNotNull();
        for (Object value : row.values()) {
            String text = String.valueOf(value);
            assertThat(text).doesNotContain(x.idNumber()).doesNotContain("1903456789012").doesNotContain("Ngõ Huế");
        }
    }

    @Test
    void deleteNullsReferencesFromOthers() throws Exception {
        Person x = activeX();
        Employee y = simpleEmployee("Lê Thị Ở Lại");
        Employee z = simpleEmployee("Phạm Văn Đơn");
        Long[] refs = tx.execute(s -> {
            Employee xe = employeeRepository.findById(x.id()).orElseThrow();
            Employee ye = employeeRepository.findById(y.getId()).orElseThrow();
            ye.setManager(xe);
            Department p = new Department();
            p.setName("Phòng xóa " + x.code());
            p.setActive(true);
            p.setHead(xe);
            p = departmentRepository.save(p);
            LeaveRequest r = leave(
                employeeRepository.findById(z.getId()).orElseThrow(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                LeaveRequestType.PHEP_NAM,
                xe
            );
            r.setDecidedBy(x.login());
            r.setDecidedAt(Instant.now());
            OvertimeRequest ot = new OvertimeRequest();
            ot.setEmployee(employeeRepository.findById(z.getId()).orElseThrow());
            ot.setWorkDate(LocalDate.of(2026, 6, 5));
            ot.setStartTime(LocalTime.of(18, 0));
            ot.setEndTime(LocalTime.of(19, 0));
            ot.setPlannedHours(BigDecimal.ONE);
            ot.setRate(150);
            ot.setNight(false);
            ot.setStatus(OvertimeStatus.DA_DUYET);
            ot.setCompensation(OvertimeCompensation.NGHI_BU);
            ot.setApproverKind(ApproverKind.TRUONG_PHONG);
            ot.setApproverEmployee(xe);
            ot.setDecidedBy(x.login());
            ot.setCreatedAt(Instant.now());
            ot.setSubmittedBy("test");
            ot = overtimeRepository.save(ot);
            return new Long[] { p.getId(), r.getId(), ot.getId() };
        });
        departments.add(refs[0]);
        fillAndArchive(x);

        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Hết thời hạn lưu hồ sơ").andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap("select manager_id, full_name from employee where id = ?", y.getId()))
            .containsEntry("manager_id", null)
            .containsEntry("full_name", "Lê Thị Ở Lại");
        assertThat(jdbc.queryForMap("select head_id, name from department where id = ?", refs[0])).containsEntry("head_id", null);
        Map<String, Object> leave = jdbc.queryForMap(
            "select approver_employee_id, decided_by, status from leave_request where id = ?",
            refs[1]
        );
        assertThat(leave)
            .containsEntry("approver_employee_id", null)
            .containsEntry("decided_by", x.login())
            .containsEntry("status", "DA_DUYET");
        Map<String, Object> ot = jdbc.queryForMap("select approver_employee_id, decided_by from overtime_request where id = ?", refs[2]);
        assertThat(ot).containsEntry("approver_employee_id", null).containsEntry("decided_by", x.login());
    }

    /** R9: đơn đang chờ mà người bị xóa là người duyệt được chuyển lên cấp kế tiếp (trưởng phòng W), không để trống người duyệt. */
    @Test
    void pendingRequestsEscalateToNextApprover() throws Exception {
        Person x = activeX();
        Employee w = simpleEmployee("Trưởng Phòng Còn Làm");
        Employee z = simpleEmployee("Phạm Văn Chờ Duyệt");
        Long[] refs = tx.execute(s -> {
            User wu = new User();
            wu.setLogin("xh-w-" + w.getCode().toLowerCase());
            wu.setActivated(true);
            wu.setLangKey("vi");
            wu.setEmail(wu.getLogin() + "@doit.vn");
            wu.setPassword("$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234");
            wu = userRepository.save(wu);
            Employee we = employeeRepository.findById(w.getId()).orElseThrow();
            we.setUser(wu);
            Employee xe = employeeRepository.findById(x.id()).orElseThrow();
            Department p = new Department();
            p.setName("Phòng chờ duyệt " + x.code());
            p.setActive(true);
            p.setHead(we);
            p = departmentRepository.save(p);
            Employee ze = employeeRepository.findById(z.getId()).orElseThrow();
            ze.setDepartment(p);
            ze.setManager(xe);
            LeaveRequest r = leave(ze, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), LeaveRequestType.PHEP_NAM, xe);
            r.setStatus(LeaveRequestStatus.CHO_DUYET);
            r.setApproverKind(ApproverKind.TRUONG_NHOM);
            OvertimeRequest ot = new OvertimeRequest();
            ot.setEmployee(ze);
            ot.setWorkDate(LocalDate.of(2026, 11, 6));
            ot.setStartTime(LocalTime.of(18, 0));
            ot.setEndTime(LocalTime.of(20, 0));
            ot.setPlannedHours(new BigDecimal("2"));
            ot.setRate(150);
            ot.setNight(false);
            ot.setStatus(OvertimeStatus.CHO_DUYET);
            ot.setCompensation(OvertimeCompensation.TRA_TIEN);
            ot.setApproverKind(ApproverKind.TRUONG_NHOM);
            ot.setApproverEmployee(xe);
            ot.setCreatedAt(Instant.now());
            ot.setSubmittedBy("test");
            ot = overtimeRepository.save(ot);
            return new Long[] { p.getId(), r.getId(), ot.getId() };
        });
        departments.add(refs[0]);
        fillAndArchive(x);

        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Hết thời hạn lưu hồ sơ").andExpect(status().isNoContent());

        Map<String, Object> leave = jdbc.queryForMap(
            "select approver_employee_id, approver_kind, status, system_note from leave_request where id = ?",
            refs[1]
        );
        assertThat(leave)
            .containsEntry("approver_employee_id", w.getId())
            .containsEntry("approver_kind", "TRUONG_PHONG")
            .containsEntry("status", "CHO_DUYET");
        assertThat((String) leave.get("system_note")).contains("đã bị xóa hẳn");
        assertThat(jdbc.queryForMap("select approver_employee_id, approver_kind, status from overtime_request where id = ?", refs[2]))
            .containsEntry("approver_employee_id", w.getId())
            .containsEntry("approver_kind", "TRUONG_PHONG")
            .containsEntry("status", "CHO_DUYET");
        assertThat(jdbc.queryForObject("select manager_id from employee where id = ?", Long.class, z.getId())).isNull();
    }

    /** R10: dòng gắn bảng lương được dùng lại khi gắn lại sang W: chỉ xóa lịch sử có X, giữ dòng và lịch sử của W. */
    @Test
    void remappedImportRowKeepsOthersHistory() throws Exception {
        Person x = activeX();
        Employee w = simpleEmployee("Người Được Gắn Lại");
        Integer draft = PayrollFlow.upload(mockMvc, "2026-06");
        PayrollFlow.map(mockMvc, draft, 18, x.id());
        PayrollFlow.map(mockMvc, draft, 18, w.getId());
        Long mappingId = jdbc.queryForObject(
            "select id from payroll_import_mapping where import_id = ? and excel_row = 18",
            Long.class,
            draft.longValue()
        );
        assertThat(
            count("select count(*) from payroll_import_mapping_aud where id = ? and employee_id = ?", mappingId, x.id())
        ).isPositive();
        long othersHistory = count(
            "select count(*) from payroll_import_mapping_aud where id = ? and employee_id = ?",
            mappingId,
            w.getId()
        );
        assertThat(othersHistory).isPositive();
        fillAndArchive(x);

        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Hết thời hạn lưu hồ sơ").andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select employee_id from payroll_import_mapping where id = ?", Long.class, mappingId)).isEqualTo(
            w.getId()
        );
        assertThat(count("select count(*) from payroll_import_mapping_aud where id = ? and employee_id = ?", mappingId, x.id())).isZero();
        assertThat(
            count("select count(*) from payroll_import_mapping_aud where id = ? and employee_id = ?", mappingId, w.getId())
        ).isEqualTo(othersHistory);
    }

    @Test
    void deleteRollsBackOnFailure() throws Exception {
        Person x = activeX();
        Employee y = simpleEmployee("Lê Văn Quản Lý");
        tx.executeWithoutResult(s ->
            employeeRepository.findById(y.getId()).orElseThrow().setManager(employeeRepository.findById(x.id()).orElseThrow())
        );
        payrollWith(x, APPROVED, true);
        fillAndArchive(x);
        Map<String, Long> before = footprint(x);
        doThrow(new IllegalStateException("Lỗi giả lập giữa chừng")).when(ledgerRepository).purgeByEmployeeId(anyLong());

        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Hết thời hạn lưu hồ sơ").andExpect(status().isInternalServerError());

        assertThat(footprint(x)).isEqualTo(before);
        assertThat(count("select count(*) from leave_ledger_entry where employee_id = ?", x.id())).isEqualTo(2);
        assertThat(count("select count(*) from employee where id = ? and manager_id = ?", y.getId(), x.id())).isEqualTo(1);
        x.files().forEach(f -> assertThat(fileExists(f)).as(f).isTrue());
    }

    @Test
    void adminLinkedProfileNotDeletable() throws Exception {
        Employee e = simpleEmployee("Quản Trị Hệ Thống");
        update("update employee set user_id = (select id from jhi_user where login = 'admin') where id = ?", e.getId());
        archive(e.getId());
        deleteAs(SYSTEM_ADMIN, e.getId(), e.getCode(), "Thử xóa")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.deleteadminaccount"));
        assertThat(count("select count(*) from employee where id = ?", e.getId())).isEqualTo(1);
        assertThat(count("select count(*) from jhi_user where login = 'admin'")).isEqualTo(1);
    }

    @Test
    void deletionLogOnlyForAdmin() throws Exception {
        Person x = activeX();
        fillAndArchive(x);
        deleteAs(SYSTEM_ADMIN, x.id(), x.code(), "Hết thời hạn lưu hồ sơ").andExpect(status().isNoContent());
        mockMvc
            .perform(get("/api/admin/employee-deletions").with(SYSTEM_ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].employeeCode").value(x.code()))
            .andExpect(jsonPath("$[0].deletedBy").value("admin"))
            .andExpect(jsonPath("$[0].reason").value("Hết thời hạn lưu hồ sơ"));
        mockMvc
            .perform(get("/api/admin/employee-deletions").with(OTHER_ADMIN))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.deleterequiressystemadmin"));
        mockMvc.perform(get("/api/admin/employee-deletions").with(HR)).andExpect(status().isForbidden());
    }

    /** JHipster tắt auto-commit: mỗi lệnh ghi trực tiếp chạy trong một transaction để thực sự được commit. */
    private void update(String sql, Object... args) {
        tx.executeWithoutResult(s -> jdbc.update(sql, args));
    }

    private static String join(java.util.Collection<Long> ids) {
        return ids.isEmpty() ? "-1" : String.join(",", ids.stream().map(String::valueOf).toList());
    }
}

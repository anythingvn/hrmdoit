package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.Gender;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.DocumentSequenceRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.GeneratedDocumentRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.pdf.PdfService;
import vn.doit.hrm.service.pdf.PdfUnavailableException;

/** Sinh văn bản hợp đồng từ mẫu: đánh số, DOCX + PDF, thiếu dữ liệu, PDF lỗi, bản đã ký, phân quyền. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "hr-doc", authorities = "ROLE_HR")
class ContractDocumentResourceIT {

    private static final byte[] PDF = "%PDF-1.4\n% bản PDF giả\n".getBytes(StandardCharsets.UTF_8);

    private static Path storageRoot;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        storageRoot = Files.createTempDirectory("hrm-contract-doc-it");
        registry.add("application.storage.root", () -> storageRoot.toString());
        registry.add("application.company.representative-name", () -> "Trần Ngọc Nga");
    }

    @MockitoBean
    private PdfService pdfService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private GeneratedDocumentRepository documentRepository;

    @Autowired
    private DocumentSequenceRepository sequenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private vn.doit.hrm.repository.SensitiveAccessLogRepository accessLogRepository;

    private final List<Long> employees = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();
    private int codeSeq;

    private static String year() {
        return String.valueOf(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear());
    }

    @BeforeEach
    void setUp() {
        when(pdfService.toPdf(any(), anyString())).thenReturn(PDF);
        sequenceRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        reset(pdfService);
        documentRepository.deleteAll();
        sequenceRepository.deleteAll();
        tx.executeWithoutResult(s -> {
            employees.forEach(id -> {
                contractRepository.findByEmployeeId(id).forEach(contractRepository::delete);
                employeeRepository.findById(id).ifPresent(e -> {
                    e.setUser(null);
                    employeeRepository.delete(e);
                });
            });
            users.forEach(userRepository::deleteById);
        });
        employees.clear();
        users.clear();
    }

    private Employee newEmployee() {
        return new Employee()
            .code("DOC-IT-" + (++codeSeq))
            .personType(PersonType.NHAN_VIEN)
            .status(EmploymentStatus.DANG_LAM_VIEC)
            .dependants(0)
            .fullName("Nguyễn Thị Hồng Nhung")
            .gender(Gender.NU)
            .birthDate(LocalDate.of(1996, 4, 2))
            .residence("Phường Cầu Giấy, Hà Nội")
            .phone("0900000000")
            .education("Đại học")
            .idNumber("001196000" + "%03d".formatted(codeSeq))
            .idIssueDate(LocalDate.of(2021, 5, 10))
            .idIssuePlace("Cục Cảnh sát QLHC về TTXH")
            .bankAccount("0123456789")
            .bankName("Vietcombank")
            .position("Lập trình viên");
    }

    private EmploymentContract newContract(ContractType type) {
        return new EmploymentContract()
            .type(type)
            .termMonths(type == ContractType.HDLD_XDTH ? 24 : null)
            .startDate(LocalDate.of(2026, 10, 25))
            .endDate(type == ContractType.HDLD_XDTH ? LocalDate.of(2028, 10, 24) : type == ContractType.HDTV ? LocalDate.of(2026, 12, 24) : null)
            .position("Lập trình viên")
            .salary(26_215_000L)
            .salaryBasis(SalaryBasis.NET)
            .grossAmount(30_500_000L)
            .status(ContractStatus.NHAP);
    }

    /** Lưu nhân viên + hợp đồng; trả id hợp đồng. */
    private Long persist(Employee e, EmploymentContract c) {
        return tx.execute(s -> {
            employeeRepository.save(e);
            employees.add(e.getId());
            c.setEmployee(e);
            return contractRepository.save(c).getId();
        });
    }

    private Long contract() {
        return persist(newEmployee(), newContract(ContractType.HDLD_XDTH));
    }

    private String generate(Long contractId) throws Exception {
        return mockMvc
            .perform(post("/api/employment-contracts/{id}/documents", contractId))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private byte[] download(Object docId) throws Exception {
        return mockMvc
            .perform(get("/api/generated-documents/{id}/download", docId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    }

    @Test
    void firstGenerationAssignsNumberAndMakesDocxAndPdf() throws Exception {
        Long id = contract();
        long logsBefore = accessLogRepository.count();
        String body = generate(id);
        // Văn bản chứa CCCD, tài khoản: sinh và tải về đều ghi nhật ký truy cập dữ liệu nhạy cảm.
        assertThat(accessLogRepository.count()).isEqualTo(logsBefore + 1);
        String number = "DOIT/" + year() + "/HĐLĐ/1";
        assertThat((String) JsonPath.read(body, "$.contractNo")).isEqualTo(number);
        assertThat((Object) JsonPath.read(body, "$.pdfError")).isNull();
        assertThat(contractRepository.findById(id).orElseThrow().getContractNo()).isEqualTo(number);

        byte[] docx = download(JsonPath.read(body, "$.docx.id"));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx)); XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            String text = ex.getText();
            assertThat(text).contains("NGUYỄN THỊ HỒNG NHUNG").contains("26.215.000").contains(number).doesNotContain("30.500.000");
        }
        assertThat(download(JsonPath.read(body, "$.pdf.id"))).isEqualTo(PDF);
        assertThat(accessLogRepository.count()).isEqualTo(logsBefore + 3);

        mockMvc
            .perform(get("/api/employment-contracts/{id}/documents", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.kind=='DOCX')].templateKind").value("HDLD"))
            .andExpect(jsonPath("$[?(@.kind=='DOCX')].templateVersion").value(0))
            .andExpect(jsonPath("$[0].storagePath").doesNotExist());
    }

    @Test
    void secondGenerationKeepsNumber() throws Exception {
        Long id = contract();
        generate(id);
        String body = generate(id);
        assertThat((String) JsonPath.read(body, "$.contractNo")).isEqualTo("DOIT/" + year() + "/HĐLĐ/1");
        mockMvc
            .perform(get("/api/employment-contracts/{id}/documents", id))
            .andExpect(jsonPath("$[?(@.kind=='DOCX')].id", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void missingFieldsListedThenForce() throws Exception {
        Long id = persist(newEmployee().idNumber(null).residence(null), newContract(ContractType.HDLD_XDTH));
        mockMvc
            .perform(post("/api/employment-contracts/{id}/documents", id))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.missingfields"))
            .andExpect(jsonPath("$.detail").value(containsString("Số CCCD")))
            .andExpect(jsonPath("$.detail").value(containsString("Nơi cư trú")))
            .andExpect(jsonPath("$.missingFields[0]").value("Số CCCD"))
            .andExpect(jsonPath("$.missingFields[1]").value("Nơi cư trú"));
        assertThat(contractRepository.findById(id).orElseThrow().getContractNo()).isNull();
        assertThat(documentRepository.count()).isZero();

        mockMvc.perform(post("/api/employment-contracts/{id}/documents", id).param("force", "true")).andExpect(status().isCreated());
    }

    @Test
    void noTemplateForProbation() throws Exception {
        Long id = persist(newEmployee(), newContract(ContractType.HDTV));
        mockMvc
            .perform(post("/api/employment-contracts/{id}/documents", id))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.notemplate"));
        assertThat(contractRepository.findById(id).orElseThrow().getContractNo()).isNull();
    }

    @Test
    void pdfServiceDownStillGivesDocxThenRetry() throws Exception {
        when(pdfService.toPdf(any(), anyString())).thenThrow(new PdfUnavailableException(new IOException("connection refused")));
        Long id = contract();
        mockMvc
            .perform(post("/api/employment-contracts/{id}/documents", id))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.docx.id").isNumber())
            .andExpect(jsonPath("$.pdf").doesNotExist())
            .andExpect(jsonPath("$.pdfError").value(PdfUnavailableException.MESSAGE));

        reset(pdfService);
        when(pdfService.toPdf(any(), anyString())).thenReturn(PDF);
        mockMvc
            .perform(post("/api/employment-contracts/{id}/documents/pdf", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pdf.id").isNumber())
            .andExpect(jsonPath("$.pdfError").doesNotExist());
    }

    @Test
    void retryPdfWithoutDocx() throws Exception {
        Long id = contract();
        mockMvc
            .perform(post("/api/employment-contracts/{id}/documents/pdf", id))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.nodocx"));
    }

    @Test
    void uploadSignedThenMarkSigned() throws Exception {
        Long id = contract();
        String body = mockMvc
            .perform(multipart("/api/employment-contracts/{id}/signed", id).file(new MockMultipartFile("file", "HĐ đã ký.pdf", "application/pdf", PDF)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("SIGNED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        Integer docId = JsonPath.read(body, "$.id");
        assertThat(contractRepository.findById(id).orElseThrow().getSignedFileId()).isEqualTo(docId.longValue());

        mockMvc
            .perform(multipart("/api/employment-contracts/{id}/signed", id).file(new MockMultipartFile("file", "anh.pdf", "application/pdf", "PNG".getBytes())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.signednotpdf"));

        mockMvc
            .perform(post("/api/employment-contracts/{id}/sign", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_KY"));
        mockMvc.perform(post("/api/employment-contracts/{id}/sign", id)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.notdraft"));
    }

    @Test
    void editingContractKeepsSignedFile() throws Exception {
        Long id = contract();
        mockMvc
            .perform(multipart("/api/employment-contracts/{id}/signed", id).file(new MockMultipartFile("file", "ky.pdf", "application/pdf", PDF)))
            .andExpect(status().isCreated());
        Long signed = contractRepository.findById(id).orElseThrow().getSignedFileId();
        Long employeeId = employees.getLast();
        String json =
            "{\"id\":" + id + ",\"type\":\"HDLD_XDTH\",\"termMonths\":24,\"startDate\":\"2026-10-25\",\"position\":\"Trưởng nhóm\"," +
            "\"salary\":26215000,\"salaryBasis\":\"NET\",\"status\":\"NHAP\",\"employee\":{\"id\":" + employeeId + "}}";
        mockMvc
            .perform(put("/api/employment-contracts/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isOk());
        assertThat(contractRepository.findById(id).orElseThrow().getSignedFileId()).isEqualTo(signed);
        mockMvc
            .perform(
                patch("/api/employment-contracts/{id}", id)
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + id + ",\"signedFileId\":999999}")
            )
            .andExpect(status().isOk());
        assertThat(contractRepository.findById(id).orElseThrow().getSignedFileId()).isEqualTo(signed);
    }

    @Test
    void handTypedNumberMustBeUnique() throws Exception {
        Long a = contract();
        Long b = contract();
        generate(a);
        String taken = "DOIT/" + year() + "/HĐLĐ/1";
        mockMvc
            .perform(
                patch("/api/employment-contracts/{id}", b).contentType("application/merge-patch+json").content("{\"id\":" + b + ",\"contractNo\":\"" + taken + "\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.numbertaken"));
        mockMvc
            .perform(
                patch("/api/employment-contracts/{id}", a).contentType("application/merge-patch+json").content("{\"id\":" + a + ",\"contractNo\":\"" + taken + "\"}")
            )
            .andExpect(status().isOk());
        // Số gõ tay chưa ai dùng thì được; lần cấp sau bỏ qua số đó.
        mockMvc
            .perform(
                patch("/api/employment-contracts/{id}", b)
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + b + ",\"contractNo\":\"DOIT/" + year() + "/HĐLĐ/2\"}")
            )
            .andExpect(status().isOk());
        Long c = contract();
        assertThat((String) JsonPath.read(generate(c), "$.contractNo")).isEqualTo("DOIT/" + year() + "/HĐLĐ/3");
    }

    @Test
    void accountantAndBoardCannotTouchDocuments() throws Exception {
        Long id = contract();
        Integer docId = JsonPath.read(generate(id), "$.docx.id");
        for (String role : List.of("ROLE_KE_TOAN", "ROLE_BGD", "ROLE_NHAN_VIEN")) {
            mockMvc.perform(get("/api/generated-documents/{id}/download", docId).with(user("x").authorities(() -> role))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/employment-contracts/{id}/documents", id).with(user("x").authorities(() -> role))).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/employment-contracts/{id}/documents", id).with(user("x").authorities(() -> role))).andExpect(status().isForbidden());
        }
    }

    @Test
    void employeeDownloadsOwnSignedCopyOnly() throws Exception {
        Employee self = newEmployee();
        Long own = persist(self, newContract(ContractType.HDLD_XDTH));
        Long other = contract();
        tx.executeWithoutResult(s -> {
            User u = new User();
            u.setLogin("nv-doc-it");
            u.setActivated(true);
            u.setLangKey("vi");
            u.setPassword(passwordEncoder.encode("MatKhau@2026"));
            u.setAuthorities(java.util.Set.of(authorityRepository.findById("ROLE_NHAN_VIEN").orElseThrow()));
            userRepository.save(u);
            users.add(u.getId());
            Employee e = employeeRepository.findById(self.getId()).orElseThrow();
            e.setUser(u);
        });
        for (Long id : List.of(own, other)) {
            mockMvc
                .perform(multipart("/api/employment-contracts/{id}/signed", id).file(new MockMultipartFile("file", "ky.pdf", "application/pdf", PDF)))
                .andExpect(status().isCreated());
        }
        var nv = user("nv-doc-it").authorities(() -> "ROLE_NHAN_VIEN");
        mockMvc
            .perform(get("/api/account/employee/contracts").with(nv))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(own))
            .andExpect(jsonPath("$[0].hasSigned").value(true))
            .andExpect(jsonPath("$[0].salary").doesNotExist());
        mockMvc.perform(get("/api/account/employee/contracts/{id}/signed", own).with(nv)).andExpect(status().isOk());
        mockMvc.perform(get("/api/account/employee/contracts/{id}/signed", other).with(nv)).andExpect(status().isForbidden());
    }
}

package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AssignmentDecisionRecordRepository;
import vn.doit.hrm.repository.DocumentSequenceRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.GeneratedDocumentRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.service.pdf.PdfService;
import vn.doit.hrm.service.pdf.PdfUnavailableException;

/** Quyết định cử nhân sự: ghi giai đoạn vị trí + cấp số + sinh văn bản trong một transaction. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "hr-qd", authorities = "ROLE_HR")
class AssignmentDecisionResourceIT {

    private static final byte[] PDF = "%PDF-1.4\n".getBytes(StandardCharsets.UTF_8);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-decision-it");
        registry.add("application.storage.root", storage::toString);
        registry.add("application.company.representative-name", () -> "Trần Ngọc Nga");
        registry.add("application.company.representative-title", () -> "Giám đốc");
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
    private WorkAssignmentRepository assignmentRepository;

    @Autowired
    private TerminationRecordRepository terminationRepository;

    @Autowired
    private AssignmentDecisionRecordRepository decisionRepository;

    @Autowired
    private GeneratedDocumentRepository documentRepository;

    @Autowired
    private DocumentSequenceRepository sequenceRepository;

    @Autowired
    private vn.doit.hrm.repository.WorkSiteRepository siteRepository;

    private final List<Long> employees = new ArrayList<>();
    private int seq;

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
        decisionRepository.deleteAll();
        sequenceRepository.deleteAll();
        tx.executeWithoutResult(s ->
            employees.forEach(id -> {
                terminationRepository.findAll().stream().filter(r -> id.equals(r.getEmployeeId())).forEach(terminationRepository::delete);
                assignmentRepository.findByEmployeeId(id).forEach(assignmentRepository::delete);
                contractRepository.findByEmployeeId(id).forEach(contractRepository::delete);
                employeeRepository.deleteById(id);
            })
        );
        employees.clear();
    }

    /** Nhân viên đang làm ở văn phòng từ 01/01/2026, có HĐLĐ đã ký đang hiệu lực. */
    private Long officeEmployee() {
        return tx.execute(s -> {
            Employee e = employeeRepository.save(
                new Employee()
                    .code("QD-IT-" + (++seq))
                    .personType(PersonType.NHAN_VIEN)
                    .status(EmploymentStatus.DANG_LAM_VIEC)
                    .dependants(0)
                    .fullName("Lê Văn Tâm")
                    .position("Kỹ sư phần mềm")
            );
            employees.add(e.getId());
            contractRepository.save(
                new EmploymentContract()
                    .contractNo("DOIT/2025/HĐLĐ/77-" + seq)
                    .type(ContractType.HDLD_KXDTH)
                    .startDate(LocalDate.of(2025, 1, 1))
                    .status(ContractStatus.DA_KY)
                    .employee(e)
            );
            WorkAssignment office = new WorkAssignment();
            office.setEmployee(e);
            office.setType(WorkLocationType.VAN_PHONG);
            office.setUnit("Văn phòng DO IT");
            office.setFromDate(LocalDate.of(2026, 1, 1));
            assignmentRepository.save(office);
            return e.getId();
        });
    }

    private static String onsite(String from, String to) {
        return (
            "{\"type\":\"KHACH_HANG\",\"unit\":\"LP Bank\",\"address\":\"210 Trần Quang Khải, Hà Nội\"," +
            "\"fromDate\":\"" + from + "\",\"toDate\":\"" + to + "\",\"task\":\"Triển khai hệ thống core banking\"," +
            "\"benefits\":\"Hưởng nguyên lương và phụ cấp đi lại theo quy chế\"}"
        );
    }

    @Test
    void onsiteDecisionCreatesAssignmentNumberAndDocx() throws Exception {
        Long id = officeEmployee();
        String body = mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", id).contentType(MediaType.APPLICATION_JSON).content(onsite("2026-11-01", "2027-01-31")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision.decisionNo").value("DOIT/" + year() + "/QĐCNS/1"))
            .andExpect(jsonPath("$.decision.signerName").value("Trần Ngọc Nga"))
            .andExpect(jsonPath("$.decision.signerTitle").value("Giám đốc"))
            .andExpect(jsonPath("$.decision.unit").value("LP Bank"))
            .andExpect(jsonPath("$.pdf.id").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();

        List<WorkAssignment> rows = assignmentRepository.findByEmployeeId(id);
        assertThat(rows)
            .anySatisfy(a -> {
                assertThat(a.getType()).isEqualTo(WorkLocationType.KHACH_HANG);
                assertThat(a.getFromDate()).isEqualTo(LocalDate.of(2026, 11, 1));
                assertThat(a.getDecisionNo()).isEqualTo("DOIT/" + year() + "/QĐCNS/1");
            })
            .anySatisfy(a -> {
                assertThat(a.getType()).isEqualTo(WorkLocationType.VAN_PHONG);
                assertThat(a.getFromDate()).isEqualTo(LocalDate.of(2027, 2, 1));
            });

        byte[] docx = mockMvc
            .perform(get("/api/generated-documents/{id}/download", (Object) JsonPath.read(body, "$.docx.id")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx)); XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            assertThat(ex.getText()).contains("làm việc tại").contains("LP Bank").contains("Lê Văn Tâm").contains("DOIT/2025/HĐLĐ/77-" + seq);
        }

        mockMvc
            .perform(get("/api/employees/{id}/assignment-decisions", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].decision.decisionNo").value("DOIT/" + year() + "/QĐCNS/1"))
            .andExpect(jsonPath("$[0].documents.length()").value(2));
    }

    @Test
    void overlapRejectedWithoutDecisionOrNumber() throws Exception {
        Long id = officeEmployee();
        mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", id).contentType(MediaType.APPLICATION_JSON).content(onsite("2026-11-01", "2027-01-31")))
            .andExpect(status().isCreated());
        long assignmentsBefore = assignmentRepository.findByEmployeeId(id).size();
        mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", id).contentType(MediaType.APPLICATION_JSON).content(onsite("2026-12-01", "2027-03-31")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.assignmentconflict"))
            .andExpect(jsonPath("$.detail").isString());
        assertThat(decisionRepository.count()).isEqualTo(1);
        assertThat(assignmentRepository.findByEmployeeId(id)).hasSize((int) assignmentsBefore);
        // Dãy số không tăng: quyết định tiếp theo vẫn là số 2.
        mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", id).contentType(MediaType.APPLICATION_JSON).content(onsite("2027-06-01", "2027-06-30")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision.decisionNo").value("DOIT/" + year() + "/QĐCNS/2"));
    }

    @Test
    void pendingTerminationRejected() throws Exception {
        Long id = officeEmployee();
        tx.executeWithoutResult(s -> {
            TerminationRecord r = new TerminationRecord();
            r.setEmployeeId(id);
            r.setLastWorkingDay(LocalDate.of(2026, 11, 15));
            r.setTerminationDate(LocalDate.of(2026, 11, 16));
            r.setTerminationType(TerminationType.TU_NGHI);
            r.setStatus(TerminationRecordStatus.SCHEDULED);
            r.setCreatedAt(Instant.now());
            terminationRepository.save(r);
        });
        mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", id).contentType(MediaType.APPLICATION_JSON).content(onsite("2026-12-01", "2027-01-31")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.pendingtermination"));
        assertThat(decisionRepository.count()).isZero();
    }

    @Test
    void pdfDownKeepsDecisionThenRetry() throws Exception {
        when(pdfService.toPdf(any(), anyString())).thenThrow(new PdfUnavailableException(new IOException("down")));
        Long id = officeEmployee();
        String body = mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", id).contentType(MediaType.APPLICATION_JSON).content(onsite("2026-11-01", "2027-01-31")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pdfError").value(PdfUnavailableException.MESSAGE))
            .andReturn()
            .getResponse()
            .getContentAsString();
        Integer decisionId = JsonPath.read(body, "$.decision.id");
        reset(pdfService);
        when(pdfService.toPdf(any(), anyString())).thenReturn(PDF);
        mockMvc
            .perform(post("/api/assignment-decisions/{id}/documents/pdf", decisionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pdf.id").isNumber());
    }

    @Test
    void decisionNumberCannotBeTypedOnContract() throws Exception {
        Long id = officeEmployee();
        mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", id).contentType(MediaType.APPLICATION_JSON).content(onsite("2026-11-01", "2027-01-31")))
            .andExpect(status().isCreated());
        Long contractId = contractRepository.findByEmployeeId(id).getFirst().getId();
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/employment-contracts/{id}", contractId)
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + contractId + ",\"contractNo\":\"DOIT/" + year() + "/QĐCNS/1\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.numbertaken"));
    }

    @Test
    void officeTypeRejected() throws Exception {
        Long id = officeEmployee();
        mockMvc
            .perform(
                post("/api/employees/{id}/assignment-decisions", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"VAN_PHONG\",\"unit\":\"Văn phòng\",\"fromDate\":\"2026-11-01\",\"task\":\"x\",\"benefits\":\"y\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.decisiontype"));
    }

    @Test
    void officeSiteRejectedAsDecisionTarget() throws Exception {
        Long id = officeEmployee();
        Long siteId = siteRepository
            .save(new vn.doit.hrm.domain.WorkSite().name("Văn phòng E2E").type(WorkLocationType.VAN_PHONG).active(true))
            .getId();
        try {
            mockMvc
                .perform(
                    post("/api/employees/{id}/assignment-decisions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"type\":\"KHACH_HANG\",\"siteId\":" + siteId + ",\"fromDate\":\"2026-11-01\",\"toDate\":\"2027-01-31\",\"task\":\"x\",\"benefits\":\"y\"}"
                        )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.decisiontype"));
            assertThat(decisionRepository.count()).isZero();
            assertThat(assignmentRepository.findByEmployeeId(id)).hasSize(1);
        } finally {
            siteRepository.deleteById(siteId);
        }
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantForbidden() throws Exception {
        mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", 1L).contentType(MediaType.APPLICATION_JSON).content(onsite("2026-11-01", "2027-01-31")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}/assignment-decisions", 1L)).andExpect(status().isForbidden());
    }

    @Test
    void detailMentionsOverlapInVietnamese() throws Exception {
        Long id = officeEmployee();
        mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", id).contentType(MediaType.APPLICATION_JSON).content(onsite("2026-11-01", "2027-01-31")))
            .andExpect(status().isCreated());
        mockMvc
            .perform(post("/api/employees/{id}/assignment-decisions", id).contentType(MediaType.APPLICATION_JSON).content(onsite("2027-01-15", "2027-02-15")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("Trùng thời gian")))
            .andExpect(jsonPath("$.detail").value(containsString("LP Bank")));
    }
}

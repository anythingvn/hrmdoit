package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.DocumentSequenceRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.GeneratedDocumentRepository;

/** Sinh hợp đồng đi qua dịch vụ PDF thật (image {@code ops/pdf}). */
@Tag("pdf")
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class ContractDocumentPdfIT {

    private static final GenericContainer<?> GOTENBERG = new GenericContainer<>(
        new ImageFromDockerfile("doit-hrm-pdf-test", false).withFileFromPath(".", Path.of("../ops/pdf"))
    )
        .withExposedPorts(3000)
        .waitingFor(Wait.forHttp("/health").forPort(3000).withStartupTimeout(Duration.ofMinutes(2)));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        GOTENBERG.start();
        Path storage = Files.createTempDirectory("hrm-contract-pdf-it");
        registry.add("application.storage.root", storage::toString);
        registry.add("application.pdf.url", () -> "http://" + GOTENBERG.getHost() + ":" + GOTENBERG.getMappedPort(3000));
        registry.add("application.company.representative-name", () -> "Trần Ngọc Nga");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void companyFromProperties() {
        // CSDL test dùng chung: tạo lại cài đặt công ty từ application.company.* của IT này (người đại diện).
        tx.executeWithoutResult(t -> jdbc.update("delete from company_setting"));
    }

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

    private Long employeeId;

    @AfterEach
    void cleanUp() {
        documentRepository.deleteAll();
        sequenceRepository.deleteAll();
        tx.executeWithoutResult(s -> {
            contractRepository.findByEmployeeId(employeeId).forEach(contractRepository::delete);
            employeeRepository.deleteById(employeeId);
        });
    }

    @Test
    void generatedPdfKeepsVietnameseText() throws Exception {
        Long contractId = tx.execute(s -> {
            Employee e = employeeRepository.save(
                new Employee()
                    .code("PDF-IT-1")
                    .personType(PersonType.NHAN_VIEN)
                    .status(EmploymentStatus.DANG_LAM_VIEC)
                    .dependants(0)
                    .fullName("Phạm Thị Ngọc Ánh")
                    .birthDate(LocalDate.of(1998, 1, 2))
                    .residence("Phường Dịch Vọng, Hà Nội")
                    .idNumber("001198000123")
                    .idIssueDate(LocalDate.of(2022, 3, 4))
                    .idIssuePlace("Cục Cảnh sát QLHC về TTXH")
                    .position("Kế toán")
            );
            employeeId = e.getId();
            return contractRepository
                .save(
                    new EmploymentContract()
                        .type(ContractType.HDLD_XDTH)
                        .termMonths(12)
                        .startDate(LocalDate.of(2026, 11, 1))
                        .endDate(LocalDate.of(2027, 10, 31))
                        .salary(15_500_000L)
                        .salaryBasis(SalaryBasis.NET)
                        .status(ContractStatus.NHAP)
                        .employee(e)
                )
                .getId();
        });

        String body = mockMvc
            .perform(post("/api/employment-contracts/{id}/documents", contractId))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat((Object) JsonPath.read(body, "$.pdfError")).isNull();

        byte[] pdf = mockMvc
            .perform(get("/api/generated-documents/{id}/download", (Object) JsonPath.read(body, "$.pdf.id")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM").contains("PHẠM THỊ NGỌC ÁNH").contains("15.500.000");
        }
    }
}

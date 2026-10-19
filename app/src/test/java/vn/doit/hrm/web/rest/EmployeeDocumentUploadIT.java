package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeDocumentRepository;
import vn.doit.hrm.repository.EmployeeRepository;

/** Hồ sơ scan: tải lên, tải về, xóa, kiểm tra loại file. */
@IntegrationTest
@AutoConfigureMockMvc
class EmployeeDocumentUploadIT {

    private static Path storageRoot;

    static {
        try {
            storageRoot = Files.createTempDirectory("hrm-doc-it");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("application.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeDocumentRepository documentRepository;

    private Employee employee;

    @BeforeEach
    void init() {
        employee = employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9101")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Lê Thu Hà")
                .searchText("nv9101 le thu ha")
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
        );
    }

    @AfterEach
    void cleanup() {
        documentRepository.deleteAll();
        employeeRepository.deleteById(employee.getId());
    }

    private static final byte[] PDF = "%PDF-1.4 noi dung".getBytes(StandardCharsets.UTF_8);

    private MockMultipartFile file(String name, byte[] content, String type) {
        return new MockMultipartFile("file", name, type, content);
    }

    @Test
    @WithMockUser(username = "hr1", authorities = "ROLE_HR")
    void uploadListDownloadDelete() throws Exception {
        String body = mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("Bằng đại học.pdf", PDF, "application/pdf")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fileName").value("Bằng đại học.pdf"))
            .andExpect(jsonPath("$.contentType").value("application/pdf"))
            .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
            .andExpect(jsonPath("$.uploadedBy").value("hr1"))
            .andExpect(jsonPath("$.storagePath").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        long docId = om.readTree(body).get("id").asLong();

        mockMvc
            .perform(get("/api/employees/{id}/documents", employee.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].storagePath").doesNotExist());

        byte[] downloaded = mockMvc
            .perform(get("/api/employee-documents/{id}/download", docId))
            .andExpect(status().isOk())
            .andExpect(
                header().string("Content-Disposition", "attachment; filename*=UTF-8''B%E1%BA%B1ng%20%C4%91%E1%BA%A1i%20h%E1%BB%8Dc.pdf")
            )
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);

        String stored = documentRepository.findById(docId).orElseThrow().getStoragePath();
        assertThat(Files.exists(storageRoot.resolve(stored))).isTrue();

        mockMvc.perform(delete("/api/employee-documents/{id}", docId)).andExpect(status().isNoContent());
        assertThat(documentRepository.findById(docId)).isEmpty();
        assertThat(Files.exists(storageRoot.resolve(stored))).isFalse();
        mockMvc.perform(get("/api/employee-documents/{id}/download", docId)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void downloadWhenFileMissingOnDiskIs404() throws Exception {
        String body = mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("Hợp đồng.pdf", PDF, "application/pdf")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        long docId = om.readTree(body).get("id").asLong();
        Files.delete(storageRoot.resolve(documentRepository.findById(docId).orElseThrow().getStoragePath()));

        mockMvc
            .perform(get("/api/employee-documents/{id}/download", docId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Không tìm thấy file trên máy chủ"))
            .andExpect(jsonPath("$.message").value("error.filemissing"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void maliciousNameCannotEscape() throws Exception {
        mockMvc
            .perform(
                multipart("/api/employees/{id}/documents", employee.getId()).file(file("../../etc/passwd.pdf", PDF, "application/pdf"))
            )
            .andExpect(status().isCreated());
        String stored = documentRepository.findAll().get(0).getStoragePath();
        assertThat(stored).doesNotContain("..").doesNotContain("passwd");
        assertThat(storageRoot.resolve(stored).normalize()).startsWith(storageRoot);
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void renamedExeIsRejected() throws Exception {
        byte[] exe = "MZ\u0090\u0000 this is an exe".getBytes(StandardCharsets.ISO_8859_1);
        mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("virus.pdf", exe, "application/pdf")))
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("a.exe", PDF, "application/pdf")))
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("a.pdf", PDF, "application/x-msdownload")))
            .andExpect(status().isBadRequest());
        // đuôi png nhưng nội dung PDF
        mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("a.png", PDF, "image/png")))
            .andExpect(status().isBadRequest());
        assertThat(documentRepository.count()).isZero();
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void pngAndJpegAccepted() throws Exception {
        byte[] png = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2 };
        byte[] jpg = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2 };
        mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("cccd.PNG", png, "image/png")))
            .andExpect(status().isCreated());
        mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("anh.jpeg", jpg, "image/jpeg")))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void oversizeIsRejected() throws Exception {
        byte[] big = new byte[21 * 1024 * 1024];
        System.arraycopy(PDF, 0, big, 0, 5);
        int st = mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("big.pdf", big, "application/pdf")))
            .andReturn()
            .getResponse()
            .getStatus();
        assertThat(st).isIn(400, 413);
        assertThat(documentRepository.count()).isZero();
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantCannotUploadOrDeleteButCanList() throws Exception {
        mockMvc
            .perform(multipart("/api/employees/{id}/documents", employee.getId()).file(file("a.pdf", PDF, "application/pdf")))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/employee-documents/{id}", 1L)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}/documents", employee.getId())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void genericJsonWritesAreGone() throws Exception {
        mockMvc
            .perform(post("/api/employee-documents").contentType("application/json").content("{}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void uploadForMissingEmployeeIs404() throws Exception {
        mockMvc
            .perform(multipart("/api/employees/{id}/documents", 987654321L).file(file("a.pdf", PDF, "application/pdf")))
            .andExpect(status().isNotFound());
    }
}

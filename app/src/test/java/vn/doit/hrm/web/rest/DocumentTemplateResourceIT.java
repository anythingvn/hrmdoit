package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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
import vn.doit.hrm.domain.enumeration.TemplateKind;
import vn.doit.hrm.repository.DocumentTemplateRepository;
import vn.doit.hrm.service.DocumentTemplateService;

/** Kho mẫu văn bản: kiểm tra thẻ khi tải lên, phiên bản, quay lại mẫu gốc. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class DocumentTemplateResourceIT {

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static Path storageRoot;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        storageRoot = Files.createTempDirectory("hrm-template-it");
        registry.add("application.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentTemplateRepository repository;

    @Autowired
    private DocumentTemplateService service;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate tx;

    @BeforeEach
    @AfterEach
    void clean() {
        repository.deleteAll();
        tx.executeWithoutResult(t -> jdbc.update("delete from document_template_counter"));
    }

    @Test
    void brokenTagRejectedWithTagName() throws Exception {
        byte[] docx = docx(p -> {
            p.createRun().setText("Ông/bà: {{hoT");
            XWPFRun b = p.createRun();
            b.setBold(true);
            b.setText("en}}");
        });
        mockMvc
            .perform(multipart("/api/document-templates/HDLD").file(file("HDLD moi.docx", docx)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.templatebrokentags"))
            .andExpect(jsonPath("$.detail").value(containsString("hoTen")));
        assertThat(repository.count()).isZero();
    }

    @Test
    void unknownTagRejected() throws Exception {
        mockMvc
            .perform(multipart("/api/document-templates/HDLD").file(file("HDLD.docx", docx(p -> p.createRun().setText("{{hoTen}} {{abc}}")))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.templateunknowntags"))
            .andExpect(jsonPath("$.detail").value(containsString("abc")));
    }

    @Test
    void contractTagNotAllowedInDecisionTemplate() throws Exception {
        mockMvc
            .perform(multipart("/api/document-templates/QD_CU_NHAN_SU").file(file("QD.docx", docx(p -> p.createRun().setText("{{mucLuong}}")))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.templateunknowntags"))
            .andExpect(jsonPath("$.detail").value(containsString("mucLuong")));
    }

    @Test
    void validUploadBecomesActiveVersion() throws Exception {
        byte[] v1 = docx(p -> p.createRun().setText("Hợp đồng số {{soHopDong}} với {{hoTen}}"));
        mockMvc
            .perform(multipart("/api/document-templates/HDLD").file(file("HĐLĐ 2026.docx", v1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("HDLD"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.fileName").value("HĐLĐ 2026.docx"))
            .andExpect(jsonPath("$.uploadedBy").value("user"))
            .andExpect(jsonPath("$.storagePath").doesNotExist());
        assertThat(service.activeTemplate(TemplateKind.HDLD)).contains(v1);
        assertThat(service.active(TemplateKind.HDLD).orElseThrow().version()).isEqualTo(1);

        byte[] v2 = docx(p -> p.createRun().setText("Bản sửa {{hoTen}}"));
        mockMvc.perform(multipart("/api/document-templates/HDLD").file(file("v2.docx", v2))).andExpect(jsonPath("$.version").value(2));
        assertThat(service.activeTemplate(TemplateKind.HDLD)).contains(v2);

        mockMvc
            .perform(get("/api/document-templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].kind").value("HDLD"))
            .andExpect(jsonPath("$[0].label").value("Hợp đồng lao động"))
            .andExpect(jsonPath("$[0].activeVersion").value(2))
            .andExpect(jsonPath("$[0].hasDefault").value(true))
            .andExpect(jsonPath("$[0].versions.length()").value(2))
            .andExpect(jsonPath("$[0].versions[0].version").value(2));

        mockMvc
            .perform(get("/api/document-templates/HDLD/download").param("version", "1"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(v1));
    }

    @Test
    void activateOldVersionAndDefault() throws Exception {
        byte[] v1 = docx(p -> p.createRun().setText("{{hoTen}}"));
        byte[] v2 = docx(p -> p.createRun().setText("{{viTri}}"));
        mockMvc.perform(multipart("/api/document-templates/HDLD").file(file("a.docx", v1))).andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/document-templates/HDLD").file(file("b.docx", v2))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/document-templates/HDLD/versions/1/activate")).andExpect(status().isOk());
        assertThat(service.activeTemplate(TemplateKind.HDLD)).contains(v1);

        mockMvc.perform(post("/api/document-templates/HDLD/versions/0/activate")).andExpect(status().isOk());
        assertThat(service.activeTemplate(TemplateKind.HDLD)).contains(defaultTemplate("/templates/DOIT_HDLD_template.docx"));
        assertThat(service.active(TemplateKind.HDLD).orElseThrow().version()).isZero();
        mockMvc.perform(get("/api/document-templates")).andExpect(jsonPath("$[0].activeVersion").value(0));

        mockMvc.perform(post("/api/document-templates/HDLD/versions/9/activate")).andExpect(status().isNotFound());
    }

    @Test
    void defaultTemplatesWhenNothingUploaded() throws Exception {
        assertThat(service.activeTemplate(TemplateKind.HDLD)).contains(defaultTemplate("/templates/DOIT_HDLD_template.docx"));
        assertThat(service.activeTemplate(TemplateKind.QD_CU_NHAN_SU)).contains(defaultTemplate("/templates/DOIT_QD_CuNhanSu_template.docx"));
        assertThat(service.activeTemplate(TemplateKind.HDTV)).contains(defaultTemplate("/templates/DOIT_HDTV_template.docx"));
        assertThat(service.activeTemplate(TemplateKind.HDDT)).contains(defaultTemplate("/templates/DOIT_HDDT_template.docx"));
        assertThat(service.activeTemplate(TemplateKind.HDDV)).contains(defaultTemplate("/templates/DOIT_HDDV_template.docx"));
        mockMvc
            .perform(get("/api/document-templates"))
            .andExpect(jsonPath("$[1].kind").value("HDTV"))
            .andExpect(jsonPath("$[1].activeVersion").value(0))
            .andExpect(jsonPath("$[1].hasDefault").value(true));
        mockMvc
            .perform(get("/api/document-templates/HDLD/download").param("version", "0"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(defaultTemplate("/templates/DOIT_HDLD_template.docx")));
        mockMvc
            .perform(get("/api/document-templates/HDTV/download").param("version", "0"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(defaultTemplate("/templates/DOIT_HDTV_template.docx")));
    }

    @Test
    void notDocxRejected() throws Exception {
        mockMvc
            .perform(multipart("/api/document-templates/HDLD").file(file("mau.doc", docx(p -> p.createRun().setText("{{hoTen}}")))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.templatenotdocx"));
        mockMvc
            .perform(multipart("/api/document-templates/HDLD").file(file("gia.docx", "không phải Word".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.templatenotdocx"));
        mockMvc
            .perform(multipart("/api/document-templates/HDLD").file(file("zip.docx", zipWithout())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.templatenotdocx"));
    }

    @Test
    void tooLargeRejected() throws Exception {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        big[0] = 'P';
        big[1] = 'K';
        mockMvc
            .perform(multipart("/api/document-templates/HDLD").file(file("to.docx", big)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.templatetoolarge"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void boardForbidden() throws Exception {
        mockMvc.perform(get("/api/document-templates")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/document-templates/HDLD/download").param("version", "0")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/document-templates/HDLD/versions/0/activate")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantManagesPayslipTemplateOnly() throws Exception {
        mockMvc
            .perform(get("/api/document-templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].kind").value("PHIEU_LUONG"))
            .andExpect(jsonPath("$[0].hasDefault").value(true));
        mockMvc
            .perform(multipart("/api/document-templates/PHIEU_LUONG").file(file("phieu.docx", docx(p -> p.createRun().setText("{{hoTen}} {{q}}")))))
            .andExpect(status().isCreated());
        mockMvc
            .perform(multipart("/api/document-templates/HDLD").file(file("mau.docx", docx(p -> p.createRun().setText("{{hoTen}}")))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/document-templates/HDLD/download").param("version", "0")).andExpect(status().isForbidden());
    }

    @Test
    void hrDoesNotSeePayslipTemplate() throws Exception {
        mockMvc.perform(get("/api/document-templates")).andExpect(jsonPath("$[?(@.kind=='PHIEU_LUONG')]").isEmpty());
        mockMvc
            .perform(multipart("/api/document-templates/PHIEU_LUONG").file(file("phieu.docx", docx(p -> p.createRun().setText("{{q}}")))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/document-templates/PHIEU_LUONG/download").param("version", "0")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/document-templates/PHIEU_LUONG/versions/0/activate")).andExpect(status().isForbidden());
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, DOCX, content);
    }

    private static byte[] docx(Consumer<XWPFParagraph> fill) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(doc.createParagraph());
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] zipWithout() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("hello.txt"));
            zip.write("xin chào".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] defaultTemplate(String path) throws IOException {
        try (InputStream in = DocumentTemplateResourceIT.class.getResourceAsStream(path)) {
            return in.readAllBytes();
        }
    }
}

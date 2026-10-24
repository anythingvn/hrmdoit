package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.GeneratedDocument;
import vn.doit.hrm.domain.enumeration.DocumentOwnerType;
import vn.doit.hrm.domain.enumeration.GeneratedFileKind;
import vn.doit.hrm.domain.enumeration.TemplateKind;
import vn.doit.hrm.repository.DocumentTemplateRepository;
import vn.doit.hrm.repository.GeneratedDocumentRepository;
import vn.doit.hrm.service.DocumentFiles;

/** Mẫu văn bản: tối đa 5 mẫu mỗi loại, xóa mẫu không đụng tới văn bản đã sinh, số phiên bản không dùng lại. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class DocumentTemplateLimitIT {

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static Path storageRoot;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        storageRoot = Files.createTempDirectory("hrm-template-limit-it");
        registry.add("application.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentTemplateRepository repository;

    @Autowired
    private GeneratedDocumentRepository generatedRepository;

    @Autowired
    private DocumentFiles documentFiles;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @BeforeEach
    @AfterEach
    void clean() {
        repository.deleteAll();
        tx.executeWithoutResult(t -> jdbc.update("delete from document_template_counter"));
    }

    @Test
    void sixthUploadRejected() throws Exception {
        for (int i = 1; i <= 5; i++) {
            upload("HDLD", "m" + i + ".docx").andExpect(status().isCreated());
        }
        upload("HDLD", "m6.docx")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.templatelimit"))
            .andExpect(jsonPath("$.detail").value("Đã có 5 mẫu, hãy xoá bớt mẫu không cần"));
        assertThat(repository.countByKind(TemplateKind.HDLD)).isEqualTo(5);
        // Giới hạn tính riêng từng loại.
        upload("HDTV", "tv.docx").andExpect(status().isCreated());
    }

    @Test
    void deleteInactiveRemovesRowAndFile() throws Exception {
        upload("HDLD", "v1.docx").andExpect(status().isCreated());
        upload("HDLD", "v2.docx").andExpect(status().isCreated());
        Path file = storageRoot.resolve(repository.findByKindAndVersion(TemplateKind.HDLD, 1).orElseThrow().getStoragePath());
        assertThat(file).exists();

        mockMvc.perform(delete("/api/document-templates/HDLD/versions/1")).andExpect(status().isNoContent());

        assertThat(repository.findByKindAndVersion(TemplateKind.HDLD, 1)).isEmpty();
        assertThat(file).doesNotExist();
        assertThat(repository.findByKindAndVersion(TemplateKind.HDLD, 2).orElseThrow().isActive()).isTrue();
        mockMvc.perform(delete("/api/document-templates/HDLD/versions/1")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/document-templates/HDLD/versions/0")).andExpect(status().isNotFound());
    }

    @Test
    void deleteActiveRejected() throws Exception {
        upload("HDLD", "v1.docx").andExpect(status().isCreated());
        Path file = storageRoot.resolve(repository.findByKindAndVersion(TemplateKind.HDLD, 1).orElseThrow().getStoragePath());

        mockMvc
            .perform(delete("/api/document-templates/HDLD/versions/1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.templateactive"));

        assertThat(repository.findByKindAndVersion(TemplateKind.HDLD, 1)).isPresent();
        assertThat(file).exists();
    }

    @Test
    void generatedDocsUnaffected() throws Exception {
        byte[] rendered = "văn bản đã sinh".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        upload("HDLD", "v1.docx").andExpect(status().isCreated());
        upload("HDLD", "v2.docx").andExpect(status().isCreated());
        GeneratedDocument doc = documentFiles.save(
            DocumentOwnerType.CONTRACT,
            987654L,
            GeneratedFileKind.DOCX,
            "HDLD-1.docx",
            rendered,
            TemplateKind.HDLD,
            1,
            null
        );
        try {
            mockMvc.perform(delete("/api/document-templates/HDLD/versions/1")).andExpect(status().isNoContent());

            mockMvc.perform(get("/api/generated-documents/{id}/download", doc.getId())).andExpect(status().isOk()).andExpect(content().bytes(rendered));
            GeneratedDocument after = generatedRepository.findById(doc.getId()).orElseThrow();
            assertThat(after.getTemplateKind()).isEqualTo(TemplateKind.HDLD);
            assertThat(after.getTemplateVersion()).isEqualTo(1);
        } finally {
            generatedRepository.deleteById(doc.getId());
        }
    }

    @Test
    void afterDeleteCanUploadAgain() throws Exception {
        for (int i = 1; i <= 5; i++) {
            upload("HDLD", "m" + i + ".docx").andExpect(status().isCreated());
        }
        mockMvc.perform(delete("/api/document-templates/HDLD/versions/2")).andExpect(status().isNoContent());
        upload("HDLD", "m6.docx").andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(6)).andExpect(jsonPath("$.active").value(true));
        assertThat(repository.countByKind(TemplateKind.HDLD)).isEqualTo(5);
    }

    @Test
    void versionNumberNeverReused() throws Exception {
        upload("HDLD", "v1.docx").andExpect(status().isCreated());
        upload("HDLD", "v2.docx").andExpect(status().isCreated());
        mockMvc.perform(post("/api/document-templates/HDLD/versions/1/activate")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/document-templates/HDLD/versions/2")).andExpect(status().isNoContent());
        upload("HDLD", "v3.docx").andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(3));
    }

    private org.springframework.test.web.servlet.ResultActions upload(String kind, String name) throws Exception {
        return mockMvc.perform(multipart("/api/document-templates/" + kind).file(new MockMultipartFile("file", name, DOCX, docx(name))));
    }

    /** Mỗi file có nội dung riêng. */
    private static byte[] docx(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("{{hoTen}} " + text);
            doc.write(out);
            return out.toByteArray();
        }
    }
}

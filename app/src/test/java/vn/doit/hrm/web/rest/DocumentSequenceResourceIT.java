package vn.doit.hrm.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.repository.DocumentSequenceRepository;
import vn.doit.hrm.service.DocumentNumberService;

/** Dãy số văn bản: HR xem, chỉ Quản trị đặt số bắt đầu. */
@IntegrationTest
@AutoConfigureMockMvc
class DocumentSequenceResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentSequenceRepository sequenceRepository;

    @Autowired
    private DocumentNumberService numberService;

    @BeforeEach
    @AfterEach
    void clean() {
        sequenceRepository.deleteAll();
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void hrListsSequencesOfYear() throws Exception {
        numberService.next(DocumentType.HDLD, 2026);
        mockMvc
            .perform(get("/api/admin/document-sequences?year=2026"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(DocumentType.values().length))
            .andExpect(jsonPath("$[0].type").value("HDLD"))
            .andExpect(jsonPath("$[0].code").value("HĐLĐ"))
            .andExpect(jsonPath("$[0].year").value(2026))
            .andExpect(jsonPath("$[0].nextValue").value(2))
            .andExpect(jsonPath("$[0].lastIssued").value(1))
            .andExpect(jsonPath("$[0].nextNumber").value("DOIT/2026/HĐLĐ/2"))
            .andExpect(jsonPath("$[1].type").value("HDTV"))
            .andExpect(jsonPath("$[1].nextValue").value(1))
            .andExpect(jsonPath("$[1].lastIssued").value(0));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminSetsStart() throws Exception {
        mockMvc
            .perform(put("/api/admin/document-sequences/HDLD/2026").contentType(MediaType.APPLICATION_JSON).content("{\"nextValue\":112}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextValue").value(112))
            .andExpect(jsonPath("$.nextNumber").value("DOIT/2026/HĐLĐ/112"));
        numberService.next(DocumentType.HDLD, 2026);
        mockMvc
            .perform(put("/api/admin/document-sequences/HDLD/2026").contentType(MediaType.APPLICATION_JSON).content("{\"nextValue\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.sequencebelowused"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void rejectsNonPositiveStart() throws Exception {
        mockMvc
            .perform(put("/api/admin/document-sequences/HDLD/2026").contentType(MediaType.APPLICATION_JSON).content("{\"nextValue\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void hrCannotSetStart() throws Exception {
        mockMvc
            .perform(put("/api/admin/document-sequences/HDLD/2026").contentType(MediaType.APPLICATION_JSON).content("{\"nextValue\":112}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = { "ROLE_KE_TOAN", "ROLE_BGD" })
    void otherRolesCannotList() throws Exception {
        mockMvc.perform(get("/api/admin/document-sequences?year=2026")).andExpect(status().isForbidden());
    }
}

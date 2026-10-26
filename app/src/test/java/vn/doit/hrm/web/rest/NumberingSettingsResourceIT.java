package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.DefaultPasswordService;

/** API cấu hình số văn bản và mã nhân viên. */
@IntegrationTest
@AutoConfigureMockMvc
class NumberingSettingsResourceIT {

    private static final String URL = "/api/company-setting/numbering";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DefaultPasswordService defaultPasswords;

    @AfterEach
    void reset() {
        defaultPasswords.update(null);
        jdbc.update(
            "update company_setting set document_prefix = null, document_code_hdld = null, document_code_hdtv = null, " +
                "document_code_hddt = null, document_code_hddv = null, document_code_qdcns = null, staff_code_prefix = null, " +
                "intern_code_prefix = null, service_code_prefix = null"
        );
    }

    private void save(String json) throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getDefaults() throws Exception {
        mockMvc
            .perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentPrefix").value("DOIT"))
            .andExpect(jsonPath("$.documentCodes.HDLD").value("HĐLĐ"))
            .andExpect(jsonPath("$.staffCodePrefix").value("DOIT"))
            .andExpect(jsonPath("$.defaults.serviceCodePrefix").value("HDDV"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminSavesAndNullResets() throws Exception {
        save("{\"documentPrefix\":\"ABC\",\"staffCodePrefix\":\"NV\"}");
        mockMvc.perform(get(URL)).andExpect(jsonPath("$.documentPrefix").value("ABC")).andExpect(jsonPath("$.staffCodePrefix").value("NV"));
        save("{\"documentPrefix\":null,\"documentCodes\":null,\"staffCodePrefix\":null}");
        mockMvc
            .perform(get(URL))
            .andExpect(jsonPath("$.documentPrefix").value("DOIT"))
            .andExpect(jsonPath("$.staffCodePrefix").value("DOIT"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void hrReadsButCannotWrite() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isOk());
        mockMvc
            .perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"documentPrefix\":\"ABC\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_NHAN_VIEN")
    void nhanVienForbidden() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void invalidReturns400() throws Exception {
        mockMvc
            .perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"documentPrefix\":\"A/B\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.numberingdocinvalid"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void auditRow() throws Exception {
        save("{\"documentPrefix\":\"ABC\"}");
        Integer n = jdbc.queryForObject("select count(*) from company_setting_aud where document_prefix = 'ABC'", Integer.class);
        assertThat(n).isGreaterThanOrEqualTo(1);
    }
}

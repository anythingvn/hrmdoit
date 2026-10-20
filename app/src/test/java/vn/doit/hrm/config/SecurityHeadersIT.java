package vn.doit.hrm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.doit.hrm.IntegrationTest;

/** Header bảo mật: CSP chặt cho script, HSTS trên https. */
@IntegrationTest
@AutoConfigureMockMvc
class SecurityHeadersIT {

    @Autowired
    private MockMvc mockMvc;

    private String csp() throws Exception {
        return mockMvc.perform(get("/api/authenticate")).andReturn().getResponse().getHeader("Content-Security-Policy");
    }

    private static String directive(String csp, String name) {
        for (String part : csp.split(";")) {
            String p = part.trim();
            if (p.startsWith(name + " ")) {
                return p;
            }
        }
        return null;
    }

    @Test
    void cspAllowsOnlySameOriginScripts() throws Exception {
        String csp = csp();
        assertThat(csp).isNotNull();
        assertThat(directive(csp, "script-src")).isEqualTo("script-src 'self'");
        assertThat(csp).doesNotContain("unsafe-eval").doesNotContain("storage.googleapis.com");
        assertThat(directive(csp, "default-src")).isEqualTo("default-src 'self'");
        assertThat(directive(csp, "object-src")).isEqualTo("object-src 'none'");
        assertThat(directive(csp, "base-uri")).isEqualTo("base-uri 'self'");
    }

    @Test
    void servedPolicyIsTheProductionPolicy() throws Exception {
        // Test dùng config/application.yml riêng; bảo đảm CSP ở đó trùng với cấu hình chạy thật.
        Object policy = null;
        for (Object doc : new org.yaml.snakeyaml.Yaml().loadAll(
            java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/config/application.yml"))
        )) {
            if (doc instanceof java.util.Map<?, ?> map && map.get("jhipster") instanceof java.util.Map<?, ?> jhipster) {
                if (jhipster.get("security") instanceof java.util.Map<?, ?> security && security.get("content-security-policy") != null) {
                    policy = security.get("content-security-policy");
                }
            }
        }
        assertThat(policy).isNotNull();
        assertThat(csp()).isEqualTo(policy);
    }

    @Test
    void hstsOnHttps() throws Exception {
        mockMvc
            .perform(get("/api/authenticate").secure(true))
            .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void noHstsOnPlainHttp() throws Exception {
        mockMvc.perform(get("/api/authenticate")).andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}

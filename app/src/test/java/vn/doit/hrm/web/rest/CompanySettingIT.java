package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.doit.hrm.security.SecurityUtils.AUTHORITIES_CLAIM;
import static vn.doit.hrm.security.SecurityUtils.JWT_ALGORITHM;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.CompanySetting;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.CompanySettingRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.AssignmentDecisionService;
import vn.doit.hrm.service.CompanySettingService;
import vn.doit.hrm.service.dashboard.HrReportService;

/** Cài đặt công ty: một dòng trong CSDL thay cho application.company.*; branding công khai (tên, logo) cho màn đăng nhập. */
@IntegrationTest
@AutoConfigureMockMvc
class CompanySettingIT {

    private static final RequestPostProcessor ADMIN = user("admin-cs").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor HR = user("hr-cs").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor NHAN_VIEN = user("nv-cs").authorities(() -> "ROLE_NHAN_VIEN");

    private static final String URL = "/api/company-setting";
    private static final String BRANDING = "/api/public/branding";
    private static final byte[] PNG = Base64.getDecoder()
        .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HrReportService hrReportService;

    @Autowired
    private AssignmentDecisionService assignmentDecisionService;

    @Autowired
    private CompanySettingService companySettingService;

    @Autowired
    private CompanySettingRepository companySettingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    @AfterEach
    void fromProperties() {
        // Xóa dòng (kết nối auto-commit=false nên cần giao dịch): lần đọc sau tạo lại từ application.company.* (CSDL test dùng chung giữa các IT).
        tx.executeWithoutResult(t -> jdbc.update("delete from company_setting"));
    }

    private static String body(String name, String rep) {
        return (
            "{\"companyName\":\"" +
            name +
            "\",\"shortName\":\"DOIT\",\"tagline\":\"Làm được thì làm\",\"representativeName\":\"" +
            rep +
            "\",\"representativeTitle\":\"Giám đốc\"}"
        );
    }

    private long logoVersion() throws Exception {
        String json = mockMvc.perform(get(BRANDING)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get("logoVersion").asLong();
    }

    private MvcResult upload(byte[] bytes, String type) throws Exception {
        return mockMvc.perform(multipart(URL + "/logo").file(new MockMultipartFile("file", "logo", type, bytes)).with(ADMIN)).andReturn();
    }

    @Test
    void seededFromApplicationProperties() throws Exception {
        mockMvc
            .perform(get(BRANDING))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.companyName").value("CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT"))
            .andExpect(jsonPath("$.shortName").value("DOIT HRM"))
            .andExpect(jsonPath("$.tagline").value("Do it if you can"))
            .andExpect(jsonPath("$.logoVersion").exists());
    }

    @Test
    void adminUpdates() throws Exception {
        mockMvc
            .perform(put(URL).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body("CÔNG TY MỚI", "Trần Ngọc Nga")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.companyName").value("CÔNG TY MỚI"));
        mockMvc
            .perform(get(BRANDING))
            .andExpect(jsonPath("$.companyName").value("CÔNG TY MỚI"))
            .andExpect(jsonPath("$.shortName").value("DOIT"))
            .andExpect(jsonPath("$.tagline").value("Làm được thì làm"));

        mockMvc
            .perform(put(URL).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body("   ", "Trần Ngọc Nga")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.companynamerequired"));
        mockMvc
            .perform(put(URL).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body("A".repeat(256), "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.companysettinginvalid"));
        mockMvc
            .perform(put(URL).with(HR).contentType(MediaType.APPLICATION_JSON).content(body("CÔNG TY HR", "")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(BRANDING)).andExpect(jsonPath("$.companyName").value("CÔNG TY MỚI"));
    }

    @Test
    void representativeNotPublic() throws Exception {
        mockMvc.perform(put(URL).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body("CÔNG TY A", "Trần Ngọc Nga")));
        mockMvc
            .perform(get(BRANDING))
            .andExpect(jsonPath("$.representativeName").doesNotExist())
            .andExpect(jsonPath("$.representativeTitle").doesNotExist());
        mockMvc.perform(get(URL).with(HR)).andExpect(status().isOk()).andExpect(jsonPath("$.representativeName").value("Trần Ngọc Nga"));
        mockMvc.perform(get(URL).with(NHAN_VIEN)).andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoUploadAndServe() throws Exception {
        long before = logoVersion();
        assertThat(upload(PNG, "image/png").getResponse().getStatus()).isEqualTo(200);
        long after = logoVersion();
        assertThat(after).isNotEqualTo(before);

        MvcResult logo = mockMvc
            .perform(get(BRANDING + "/logo"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("ETag", "\"" + after + "\""))
            .andExpect(header().string("Cache-Control", "max-age=300, public"))
            .andReturn();
        assertThat(logo.getResponse().getContentAsByteArray()).isEqualTo(PNG);

        mockMvc.perform(get(BRANDING + "/logo").header("If-None-Match", "\"" + after + "\"")).andExpect(status().isNotModified());

        // HR không đổi được logo; Quản trị trở lại logo mặc định thì phiên bản đổi tiếp.
        assertThat(
            mockMvc
                .perform(multipart(URL + "/logo").file(new MockMultipartFile("file", "logo", "image/png", PNG)).with(HR))
                .andReturn()
                .getResponse()
                .getStatus()
        ).isEqualTo(403);
        mockMvc.perform(delete(URL + "/logo").with(ADMIN)).andExpect(status().isOk());
        assertThat(logoVersion()).isNotEqualTo(after);
        assertThat(mockMvc.perform(get(BRANDING + "/logo")).andReturn().getResponse().getContentAsByteArray()).isEqualTo(defaultLogo());
    }

    @Test
    void logoTooLarge() throws Exception {
        byte[] big = Arrays.copyOf(PNG, 1_048_577);
        MvcResult r = upload(big, "image/png");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("error.logotoolarge");
    }

    @Test
    void logoMagicBytesChecked() throws Exception {
        MvcResult r = upload("%PDF-1.4\n".getBytes(StandardCharsets.UTF_8), "image/png");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("error.logoinvalidtype");

        MvcResult gif = upload("GIF89a....".getBytes(StandardCharsets.UTF_8), "image/gif");
        assertThat(gif.getResponse().getContentAsString()).contains("error.logoinvalidtype");
    }

    @Test
    void svgLogoServedSandboxed() throws Exception {
        byte[] svg = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(
            StandardCharsets.UTF_8
        );
        assertThat(upload(svg, "image/svg+xml").getResponse().getStatus()).isEqualTo(200);
        MvcResult r = mockMvc
            .perform(get(BRANDING + "/logo"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/svg+xml"))
            .andExpect(header().string("Content-Security-Policy", "sandbox"))
            .andReturn();
        assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(svg);
    }

    @Test
    void defaultLogoWhenNone() throws Exception {
        mockMvc
            .perform(get(BRANDING + "/logo"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().exists("ETag"))
            .andExpect(content().bytes(defaultLogo()));
    }

    @Test
    void documentsUseCompanySetting() throws Exception {
        mockMvc
            .perform(put(URL).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body("CÔNG TY TEST", "Lê Văn Ký")))
            .andExpect(status().isOk());

        byte[] xlsx = hrReportService.excel(YearMonth.of(2026, 1), YearMonth.of(2026, 1), HrReportService.GroupBy.DEPARTMENT, false);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).contains("CÔNG TY TEST");
        }
        assertThat(assignmentDecisionService.defaults().signerName()).isEqualTo("Lê Văn Ký");
        assertThat(assignmentDecisionService.defaults().signerTitle()).isEqualTo("Giám đốc");
    }

    @Test
    void firstReadInsideReadOnlyTransactionUsesProperties() throws Exception {
        // Xuất báo cáo chạy trong giao dịch chỉ đọc: chưa có dòng thì dùng application.company.*, không lỗi, không cần kết nối thứ hai.
        byte[] xlsx = hrReportService.excel(YearMonth.of(2026, 1), YearMonth.of(2026, 1), HrReportService.GroupBy.DEPARTMENT, false);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).contains("CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT");
        }
    }

    @Test
    void concurrentFirstReadsCreateOneRow() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> names = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                names.add(pool.submit(companySettingService::companyName));
            }
            for (Future<String> f : names) {
                assertThat(f.get()).isEqualTo("CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT");
            }
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.queryForObject("select count(*) from company_setting", Integer.class)).isEqualTo(1);
    }

    @Test
    void mustChangePasswordUserStillGetsBranding() throws Exception {
        // Tài khoản đang dùng mật khẩu được cấp: màn đổi mật khẩu vẫn cần tên và logo công ty.
        User u = new User();
        u.setLogin("mcp-cs-" + UUID.randomUUID().toString().substring(0, 8));
        u.setActivated(true);
        u.setLangKey("vi");
        u.setPassword("x".repeat(60));
        u.setMustChangePassword(true);
        u.setAuthorities(Set.of(authorityRepository.findById("ROLE_NHAN_VIEN").orElseThrow()));
        User saved = userRepository.saveAndFlush(u);
        try {
            Instant issued = Instant.now().minusSeconds(5);
            JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(issued)
                .expiresAt(issued.plusSeconds(3600))
                .subject(saved.getLogin())
                .claim(AUTHORITIES_CLAIM, "ROLE_NHAN_VIEN")
                .build();
            String bearer =
                "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(JWT_ALGORITHM).build(), claims)).getTokenValue();

            // Cờ phải đổi mật khẩu đang có hiệu lực: API thường bị chặn.
            mockMvc
                .perform(get(URL).header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("error.mustchangepassword"));
            mockMvc
                .perform(get(BRANDING).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT"));
            mockMvc
                .perform(get(BRANDING + "/logo").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(content().bytes(defaultLogo()));
        } finally {
            userRepository.delete(saved);
        }
    }

    @Test
    void savingNameDoesNotOverwriteLogoChangedMeanwhile() {
        // Lưu tên (entity nạp trước) và đổi logo (câu lệnh SQL riêng) chen nhau: chỉ ghi cột đã đổi, không ghi đè logo mới.
        companySettingService.current();
        tx.executeWithoutResult(t -> {
            CompanySetting loaded = companySettingRepository.findById(CompanySetting.ID).orElseThrow();
            jdbc.update(
                "update company_setting set logo_content_type = ?, logo_updated_at = ? where id = ?",
                "image/png",
                Timestamp.from(Instant.parse("2026-10-23T03:00:00Z")),
                CompanySetting.ID
            );
            loaded.setCompanyName("Công ty đổi tên");
            companySettingRepository.flush();
        });
        assertThat(jdbc.queryForObject("select company_name from company_setting", String.class)).isEqualTo("Công ty đổi tên");
        assertThat(jdbc.queryForObject("select logo_content_type from company_setting", String.class)).isEqualTo("image/png");
        assertThat(jdbc.queryForObject("select logo_updated_at is not null from company_setting", Boolean.class)).isTrue();
    }

    private static byte[] defaultLogo() throws Exception {
        try (InputStream in = CompanySettingIT.class.getResourceAsStream("/branding/default-logo.png")) {
            assertThat(in).as("logo mặc định trong classpath").isNotNull();
            return in.readAllBytes();
        }
    }
}

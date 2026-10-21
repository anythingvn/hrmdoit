package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.doit.hrm.web.rest.PayrollFlow.ADMIN;
import static vn.doit.hrm.web.rest.PayrollFlow.KE_TOAN;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.payroll.PayrollTestData;
import vn.doit.hrm.service.payslip.PayslipSendService;
import vn.doit.hrm.service.pdf.PdfService;

/** Gửi phiếu lương: mỗi người một email kèm PDF, không số tiền trong thân, lỗi một người không chặn người khác, không gửi trùng. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(PayrollTestData.class)
class PayslipSendIT {

    private static final String BASE = "/api/payroll/periods/2026-06/payslips";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("hrm-payslip-send-it");
        registry.add("application.storage.root", storage::toString);
        registry.add("spring.mail.host", () -> "smtp.test");
    }

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private PdfService pdfService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayrollTestData data;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private PayslipSendService sendService;

    /** email → (thân thư, tên tệp đính kèm) đã gửi thành công. */
    private final List<String[]> sent = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Set<String> failing = java.util.Collections.synchronizedSet(new HashSet<>());
    private Map<String, Long> ids;

    @BeforeEach
    void seed() throws Exception {
        data.clean();
        tx.executeWithoutResult(s -> jdbc.update("delete from payslip_delivery"));
        byte[] pdf;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            pdf = out.toByteArray();
        }
        when(pdfService.toPdf(any(), anyString())).thenReturn(pdf);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        doAnswer(inv -> {
            MimeMessage m = inv.getArgument(0);
            String to = Arrays.stream(m.getAllRecipients()).map(Address::toString).collect(Collectors.joining(","));
            if (failing.contains(to)) {
                throw new MailSendException("Hộp thư từ chối " + to);
            }
            m.saveChanges();
            String body = "";
            String file = "";
            Multipart mp = (Multipart) m.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                String[] bf = textAndFile(part);
                body += bf[0];
                if (!bf[1].isEmpty()) file = bf[1];
            }
            sent.add(new String[] { to, body, file });
            return null;
        })
            .when(mailSender)
            .send(any(MimeMessage.class));

        ids = new HashMap<>(data.seedProfiles());
        ids.put("TT2601", data.seedService("TT2601", "Mai Văn Phúc"));
        ids.put("HDDV9001", data.seedService("HDDV9001", "Tạ Văn Quý"));
        ids.put("HDDV9002", data.seedService("HDDV9002", "Hồ Thị Sen"));
        tx.executeWithoutResult(s ->
            ids.forEach((code, id) ->
                jdbc.update(
                    "update employee set email = ?, id_number = ? where id = ?",
                    code.toLowerCase() + "@doit.test",
                    "0019990" + "%05d".formatted(id % 100000),
                    id
                )
            )
        );
        Integer draft = PayrollFlow.readyDraft(mockMvc, "2026-06", ids);
        mockMvc.perform(post("/api/payroll/imports/{id}/confirm", draft).with(KE_TOAN)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(ADMIN)).andExpect(status().isOk());
    }

    @AfterEach
    void clean() {
        failing.clear();
        sent.clear();
        tx.executeWithoutResult(s -> jdbc.update("delete from payslip_delivery"));
        data.clean();
    }

    private static String[] textAndFile(Part part) throws Exception {
        if (part.getContent() instanceof Multipart inner) {
            String text = "";
            String file = "";
            for (int i = 0; i < inner.getCount(); i++) {
                String[] r = textAndFile(inner.getBodyPart(i));
                text += r[0];
                if (!r[1].isEmpty()) file = r[1];
            }
            return new String[] { text, file };
        }
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
            assertThat(part.getContentType()).startsWith("application/pdf");
            return new String[] { "", jakarta.mail.internet.MimeUtility.decodeText(part.getFileName()) };
        }
        return new String[] { String.valueOf(part.getContent()), "" };
    }

    private String send(String body) throws Exception {
        return mockMvc
            .perform(post(BASE + "/send").with(KE_TOAN).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private String awaitDone() throws Exception {
        for (int i = 0; i < 100; i++) {
            String st = mockMvc.perform(get(BASE).with(KE_TOAN)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Object> pending = JsonPath.read(st, "$[?(@.status=='PENDING')]");
            if (pending.isEmpty()) {
                return st;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Gửi phiếu lương chưa xong sau 10 giây");
    }

    private long count(String email) {
        return sent.stream().filter(s -> s[0].equals(email)).count();
    }

    @Test
    void sendsOneMailPerPersonWithPdfNoAmounts() throws Exception {
        send("{\"employeeIds\":null}");
        String st = awaitDone();
        assertThat((List<Object>) JsonPath.read(st, "$[?(@.status=='SENT')]")).hasSize(ids.size());
        for (String code : ids.keySet()) {
            assertThat(count(code.toLowerCase() + "@doit.test")).as(code).isEqualTo(1);
        }
        String[] an = sent.stream().filter(s -> s[0].equals("doit2401@doit.test")).findFirst().orElseThrow();
        assertThat(an[2]).isEqualTo("PhieuLuong_06-2026_DOIT2401.pdf");
        assertThat(an[1]).contains("Phạm Văn An").contains("06/2026").contains("6 số cuối").doesNotContainPattern("\\d{1,3}(\\.\\d{3}){2,}");
    }

    @Test
    void missingEmailOrIdFails() throws Exception {
        tx.executeWithoutResult(s -> {
            jdbc.update("update employee set email = null where id = ?", ids.get("DOIT2402"));
            jdbc.update("update employee set id_number = '12345' where id = ?", ids.get("DOIT2403"));
        });
        send("{}");
        String st = awaitDone();
        assertThat((List<String>) JsonPath.read(st, "$[?(@.employeeCode=='DOIT2402')].error")).containsExactly("Chưa có email trên hồ sơ");
        assertThat((List<String>) JsonPath.read(st, "$[?(@.employeeCode=='DOIT2403')].error")).containsExactly("CCCD chưa đủ 6 số");
        assertThat(count("doit2403@doit.test")).isZero();
        assertThat(sent).hasSize(ids.size() - 2);
    }

    @Test
    void failedRecipientRetriedOthersNot() throws Exception {
        failing.add("doit2401@doit.test");
        send("{}");
        String st = awaitDone();
        assertThat((List<String>) JsonPath.read(st, "$[?(@.employeeCode=='DOIT2401')].status")).containsExactly("FAILED");
        assertThat((List<String>) JsonPath.read(st, "$[?(@.employeeCode=='DOIT2402')].status")).containsExactly("SENT");

        failing.clear();
        sent.clear();
        send("{}");
        awaitDone();
        assertThat(sent).extracting(s -> s[0]).containsExactly("doit2401@doit.test");

        sent.clear();
        send("{\"employeeIds\":[" + ids.get("DOIT2402") + "]}");
        awaitDone();
        assertThat(sent).extracting(s -> s[0]).containsExactly("doit2402@doit.test");
    }

    @Test
    void secondSendSkipsPending() throws Exception {
        send("{}");
        send("{}");
        awaitDone();
        for (String code : ids.keySet()) {
            assertThat(count(code.toLowerCase() + "@doit.test")).as(code).isEqualTo(1);
        }
    }

    @Test
    void reopenDuringSendStopsRemaining() throws Exception {
        Long periodId = jdbc.queryForObject("select id from payroll_period where period = '2026-06'", Long.class);
        tx.executeWithoutResult(s ->
            jdbc.update(
                "insert into payslip_delivery (id, period_id, version, employee_id, status, requested_by, requested_at) " +
                "values (nextval('sequence_generator'), ?, 1, ?, 'PENDING', 'kt', now())",
                periodId,
                ids.get("DOIT2401")
            )
        );
        mockMvc
            .perform(post("/api/payroll/periods/2026-06/reopen").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sửa phụ cấp\"}"))
            .andExpect(status().isOk());
        sendService.sendPending(periodId);
        assertThat(jdbc.queryForObject("select error from payslip_delivery", String.class)).isEqualTo("Kỳ đã mở lại, chưa gửi");
        assertThat(sent).isEmpty();
    }

    @Test
    void pendingAfterRestartBecomesFailed() {
        Long periodId = jdbc.queryForObject("select id from payroll_period where period = '2026-06'", Long.class);
        tx.executeWithoutResult(s ->
            jdbc.update(
                "insert into payslip_delivery (id, period_id, version, employee_id, status, requested_by, requested_at) " +
                "values (nextval('sequence_generator'), ?, 1, ?, 'PENDING', 'kt', ?)",
                periodId,
                ids.get("DOIT2401"),
                java.sql.Timestamp.from(Instant.now())
            )
        );
        sendService.failInterrupted();
        assertThat(jdbc.queryForObject("select status from payslip_delivery", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select error from payslip_delivery", String.class)).isEqualTo("Gửi bị gián đoạn, hãy gửi lại");
    }

    @Test
    void newVersionMarksStale() throws Exception {
        send("{}");
        awaitDone();
        mockMvc
            .perform(post("/api/payroll/periods/2026-06/reopen").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sửa phụ cấp\"}"))
            .andExpect(status().isOk());
        Integer draft = PayrollFlow.readyDraft(mockMvc, "2026-06", ids);
        mockMvc.perform(post("/api/payroll/imports/{id}/confirm", draft).with(KE_TOAN)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/payroll/periods/2026-06/approve").with(ADMIN)).andExpect(status().isOk());
        mockMvc
            .perform(get(BASE).with(KE_TOAN))
            .andExpect(jsonPath("$[?(@.employeeCode=='DOIT2401')].staleVersion").value(org.hamcrest.Matchers.hasItem(true)))
            .andExpect(jsonPath("$[?(@.employeeCode=='DOIT2401')].version").value(org.hamcrest.Matchers.hasItem(1)));
    }

    @Test
    void notApprovedAndRoles() throws Exception {
        mockMvc
            .perform(post("/api/payroll/periods/2026-06/reopen").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sửa\"}"))
            .andExpect(status().isOk());
        mockMvc
            .perform(post(BASE + "/send").with(KE_TOAN).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.payslipnotapproved"));
        for (String role : new String[] { "ROLE_HR", "ROLE_BGD", "ROLE_NHAN_VIEN" }) {
            mockMvc
                .perform(post(BASE + "/send").with(user("x").authorities(() -> role)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
            mockMvc.perform(get(BASE).with(user("x").authorities(() -> role))).andExpect(status().isForbidden());
        }
    }
}

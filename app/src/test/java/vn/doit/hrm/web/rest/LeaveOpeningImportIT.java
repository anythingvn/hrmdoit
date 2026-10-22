package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;

/** Nhập số dư đầu kỳ từ Excel: tải mẫu, kiểm tra, lưu tất cả hoặc không, mỗi người một lần. Hôm nay = 22/10/2026. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "ROLE_HR")
@Import(LeaveOpeningImportIT.FixedClockConfig.class)
class LeaveOpeningImportIT {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-02-10T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveLedgerEntryRepository entries;

    private Employee a;
    private Employee b;

    @BeforeEach
    void init() {
        a = LeaveFixtures.employee(employeeRepository, "NV9901", PersonType.NHAN_VIEN, LocalDate.of(2023, 1, 3));
        b = LeaveFixtures.employee(employeeRepository, "NV9902", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 3));
    }

    private byte[] template() throws Exception {
        return mockMvc
            .perform(get("/api/leave/opening/template"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    }

    /** Mỗi dòng: mã NV, phép năm còn lại, trong đó phép năm trước, giờ nghỉ bù còn lại. */
    private MockMultipartFile file(Object[]... rows) throws Exception {
        try (
            XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template()));
            ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
            Sheet sheet = wb.getSheetAt(0);
            int r = 1;
            for (Object[] values : rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < values.length; c++) {
                    Object v = values[c];
                    if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else if (v != null) {
                        row.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "so-du-dau-ky.xlsx", XLSX, out.toByteArray());
        }
    }

    private List<LeaveLedgerEntry> opening(Employee e) {
        return entries
            .findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId())
            .stream()
            .filter(r -> r.getReason() == LedgerReason.DAU_KY)
            .toList();
    }

    @Test
    void twoValidRowsAreSaved() throws Exception {
        MockMultipartFile f = file(new Object[] { "nv9901", 5.5, 2, 12 }, new Object[] { "NV9902", "3,25", null, null });

        mockMvc
            .perform(multipart("/api/leave/opening/check").file(f))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canSave").value(true))
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.rows[0].fullName").value(a.getFullName()));
        assertThat(opening(a)).isEmpty();

        mockMvc
            .perform(multipart("/api/leave/opening").file(f))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.savedCount").value(2));

        List<LeaveLedgerEntry> ra = opening(a);
        assertThat(ra)
            .extracting(LeaveLedgerEntry::getPool, LeaveLedgerEntry::getBucket, e -> e.getAmount().stripTrailingZeros().toPlainString())
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(Pool.PHEP_NAM, Bucket.NAM_TRUOC, "2"),
                org.assertj.core.groups.Tuple.tuple(Pool.PHEP_NAM, Bucket.NAM_NAY, "3.5"),
                org.assertj.core.groups.Tuple.tuple(Pool.NGHI_BU, Bucket.NAM_NAY, "12")
            );
        List<LeaveLedgerEntry> rb = opening(b);
        assertThat(rb).hasSize(1);
        assertThat(rb.get(0).getAmount()).isEqualByComparingTo("3.25");
    }

    @Test
    void unknownCodeSavesNothing() throws Exception {
        MockMultipartFile f = file(new Object[] { "NV9901", 4, 0, 0 }, new Object[] { "KHONGCO99", 2, 0, 0 });

        mockMvc
            .perform(multipart("/api/leave/opening/check").file(f))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canSave").value(false))
            .andExpect(jsonPath("$.issues[0].excelRow").value(3));

        mockMvc
            .perform(multipart("/api/leave/opening").file(f))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leaveopeninginvalid"))
            .andExpect(jsonPath("$.check.issues[0].excelRow").value(3));
        assertThat(opening(a)).isEmpty();
    }

    @Test
    void secondImportForSamePersonIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/leave/opening").file(file(new Object[] { "NV9901", 4, 0, 0 }))).andExpect(status().isCreated());

        mockMvc
            .perform(multipart("/api/leave/opening/check").file(file(new Object[] { "NV9901", 6, 0, 0 })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canSave").value(false))
            .andExpect(jsonPath("$.issues[0].excelRow").value(2));
        mockMvc.perform(multipart("/api/leave/opening").file(file(new Object[] { "NV9901", 6, 0, 0 }))).andExpect(status().isBadRequest());
        assertThat(opening(a)).hasSize(1);
    }

    @Test
    void invalidValuesAreRowErrors() throws Exception {
        Employee tt = LeaveFixtures.employee(employeeRepository, "TT9901", PersonType.THUC_TAP, LocalDate.of(2026, 1, 5));
        mockMvc
            .perform(
                multipart("/api/leave/opening/check").file(
                    file(
                        new Object[] { "NV9901", 2, 3, 0 }, // phần năm trước lớn hơn tổng
                        new Object[] { "NV9902", "abc", 0, 0 },
                        new Object[] { tt.getCode(), 1, 0, 0 },
                        new Object[] { "NV9901", 1, 0, 0 } // trùng mã trong file
                    )
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canSave").value(false))
            .andExpect(jsonPath("$.errorCount").value(4));
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void onlyHrAndAdmin() throws Exception {
        mockMvc.perform(get("/api/leave/opening/template")).andExpect(status().isForbidden());
    }
}

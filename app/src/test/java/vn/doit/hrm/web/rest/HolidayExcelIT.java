package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.service.reminder.WorkCalendarService;
import vn.doit.hrm.service.reminder.WorkCalendarService.HolidayDTO;

/** Ngày lễ: xuất/nhập Excel và khóa tháng đã chốt công hoặc đã duyệt lương. */
@IntegrationTest
@AutoConfigureMockMvc
class HolidayExcelIT {

    private static final RequestPostProcessor HR = user("hr-le").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor NV = user("nv-le").authorities(() -> "ROLE_NHAN_VIEN");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkCalendarService service;

    @Autowired
    private AttendanceMonthRepository monthRepository;

    @Autowired
    private PayrollPeriodRepository periodRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @AfterEach
    void reset() {
        tx.executeWithoutResult(s -> {
            jdbc.update("delete from holiday");
            jdbc.update("delete from attendance_month where month in ('2026-11', '2026-12')");
            jdbc.update("delete from payroll_period where period in ('2026-11', '2026-12')");
        });
    }

    private void add(String day, String name) {
        service.addHoliday(new HolidayDTO(null, LocalDate.parse(day), name));
    }

    /** Mỗi dòng {ngày, tên}; ngày dạng yyyy-MM-dd thì ghi ô kiểu ngày, dạng dd/MM/yyyy thì ghi chuỗi. */
    private static MockMultipartFile file(String[]... rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Ngày lễ");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("Ngày");
            head.createCell(1).setCellValue("Tên ngày lễ");
            int r = 1;
            for (String[] row : rows) {
                Row x = sheet.createRow(r++);
                Cell a = x.createCell(0);
                if (row[0].matches("\\d{4}-\\d{2}-\\d{2}")) {
                    a.setCellValue(LocalDate.parse(row[0]));
                    a.setCellStyle(dateStyle);
                } else {
                    a.setCellValue(row[0]);
                }
                x.createCell(1).setCellValue(row[1]);
            }
            wb.write(out);
            return new MockMultipartFile("file", "ngay-le.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private void lockCongMonth(String month) {
        AttendanceMonth m = new AttendanceMonth();
        m.setMonth(month);
        m.setStatus(AttendanceMonthStatus.DA_CHOT);
        m.setClosedBy("hr-le");
        m.setClosedAt(Instant.parse("2026-12-05T03:00:00Z"));
        monthRepository.saveAndFlush(m);
    }

    private void approvePayroll(String period) {
        PayrollPeriod p = new PayrollPeriod();
        p.setPeriod(period);
        p.setStatus(PayrollPeriodStatus.DA_DUYET);
        p.setApprovedBy("qt-le");
        p.setApprovedAt(Instant.parse("2026-12-05T03:00:00Z"));
        periodRepository.saveAndFlush(p);
    }

    @Test
    void exportRoundTrip() throws Exception {
        add("2027-01-01", "Tết Dương lịch");
        byte[] bytes = mockMvc
            .perform(get("/api/holidays/export.xlsx").param("year", "2027").with(HR))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Ngày");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Tên ngày lễ");
            assertThat(sheet.getRow(1).getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Tết Dương lịch");
        }
        // File xuất ra nhập lại được: không có gì đổi.
        mockMvc
            .perform(multipart("/api/holidays/import/preview").file(new MockMultipartFile("file", "x.xlsx", "application/octet-stream", bytes)).with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unchanged.length()").value(1))
            .andExpect(jsonPath("$.added.length()").value(0));
    }

    @Test
    void previewCategorises() throws Exception {
        add("2027-01-01", "Tết Dương lịch");
        add("2027-04-30", "Giải phóng");
        add("2027-09-02", "Quốc khánh");
        MockMultipartFile f = file(
            new String[] { "2027-01-01", "Tết Dương lịch" },
            new String[] { "30/04/2027", "Ngày Giải phóng miền Nam" },
            new String[] { "2027-05-01", "Quốc tế Lao động" }
        );
        mockMvc
            .perform(multipart("/api/holidays/import/preview").file(f).with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.years[0]").value(2027))
            .andExpect(jsonPath("$.added.length()").value(1))
            .andExpect(jsonPath("$.added[0].day").value("2027-05-01"))
            .andExpect(jsonPath("$.renamed.length()").value(1))
            .andExpect(jsonPath("$.renamed[0].oldName").value("Giải phóng"))
            .andExpect(jsonPath("$.renamed[0].name").value("Ngày Giải phóng miền Nam"))
            .andExpect(jsonPath("$.unchanged.length()").value(1))
            .andExpect(jsonPath("$.removed.length()").value(1))
            .andExpect(jsonPath("$.removed[0].name").value("Quốc khánh"))
            .andExpect(jsonPath("$.errors.length()").value(0));
        // Xem trước không ghi gì.
        assertThat(service.holidays(2027)).hasSize(3);
    }

    @Test
    void importOnlyTouchesYearsInFile() throws Exception {
        add("2026-09-02", "Quốc khánh");
        add("2027-09-02", "Quốc khánh");
        MockMultipartFile f = file(new String[] { "2027-01-01", "Tết Dương lịch" });
        mockMvc.perform(multipart("/api/holidays/import/apply").file(f).with(HR)).andExpect(status().isOk());
        assertThat(service.holidays(2026)).extracting(HolidayDTO::name).containsExactly("Quốc khánh");
        assertThat(service.holidays(2027)).extracting(HolidayDTO::name).containsExactly("Tết Dương lịch");
    }

    @Test
    void duplicateDateInFileIsError() throws Exception {
        MockMultipartFile f = file(new String[] { "2027-01-01", "Tết Dương lịch" }, new String[] { "01/01/2027", "Tết" });
        mockMvc
            .perform(multipart("/api/holidays/import/preview").file(f).with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors.length()").value(1))
            .andExpect(jsonPath("$.errors[0].row").value(3));
        mockMvc
            .perform(multipart("/api/holidays/import/apply").file(f).with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.holidayimporterrors"));
        assertThat(service.holidays(2027)).isEmpty();
    }

    @Test
    void importRejectsLockedMonthRow() throws Exception {
        add("2026-09-02", "Quốc khánh");
        lockCongMonth("2026-11");
        MockMultipartFile f = file(new String[] { "2026-09-02", "Quốc khánh" }, new String[] { "20/11/2026", "Ngày Nhà giáo" });
        mockMvc
            .perform(multipart("/api/holidays/import/preview").file(f).with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors.length()").value(1))
            .andExpect(jsonPath("$.errors[0].row").value(3))
            .andExpect(jsonPath("$.errors[0].key").value("holidaymonthlocked"))
            .andExpect(jsonPath("$.errors[0].message").value("Tháng 11/2026 đã chốt công hoặc đã duyệt lương – không sửa ngày lễ"));
        mockMvc
            .perform(multipart("/api/holidays/import/apply").file(f).with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.holidayimporterrors"));
        assertThat(service.holidays(2026)).extracting(HolidayDTO::name).containsExactly("Quốc khánh");
    }

    @Test
    void singleEditLockedMonthRejected() throws Exception {
        add("2026-12-25", "Giáng sinh");
        approvePayroll("2026-12");
        lockCongMonth("2026-11");
        Long id = service.holidays(2026).get(0).id();
        mockMvc
            .perform(post("/api/holidays").with(HR).contentType(MediaType.APPLICATION_JSON).content("{\"day\":\"2026-12-01\",\"name\":\"Thử\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.holidaymonthlocked"))
            .andExpect(jsonPath("$.detail").value("Tháng 12/2026 đã chốt công hoặc đã duyệt lương – không sửa ngày lễ"));
        mockMvc
            .perform(delete("/api/holidays/{id}", id).with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.holidaymonthlocked"));
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/api/holidays/{id}", id)
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"day\":\"2026-10-01\",\"name\":\"Giáng sinh\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.holidaymonthlocked"));
        assertThat(service.holidays(2026)).hasSize(1);
        // Chép năm: ngày rơi vào tháng đã chốt thì từ chối.
        add("2025-11-20", "Nhà giáo");
        mockMvc
            .perform(post("/api/holidays/copy").param("fromYear", "2025").param("toYear", "2026").with(HR))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.holidaymonthlocked"));
        assertThat(service.holidays(2026)).hasSize(1);
    }

    @Test
    void nhanVienCannotImport() throws Exception {
        MockMultipartFile f = file(new String[] { "2027-01-01", "Tết Dương lịch" });
        mockMvc.perform(multipart("/api/holidays/import/preview").file(f).with(NV)).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/holidays/import/apply").file(f).with(NV)).andExpect(status().isForbidden());
    }
}

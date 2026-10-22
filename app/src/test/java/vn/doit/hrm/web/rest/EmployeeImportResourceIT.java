package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.doit.hr.employee.EmployeeField.*;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hr.employee.EmployeeField;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.service.employee.EmployeeImportFiles;

/** Nhập danh sách nhân viên: kiểm tra không ghi gì, lưu trong một giao dịch, báo cáo lỗi, phân quyền. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class EmployeeImportResourceIT {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String API = "/api/employees/import";
    static final LocalDate JOIN = LocalDate.of(2026, 3, 1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    private Set<Long> employeesBefore;
    private Set<Long> departmentsBefore;

    @BeforeEach
    void baseline() {
        employeesBefore = new HashSet<>(jdbc.queryForList("select id from employee", Long.class));
        departmentsBefore = new HashSet<>(jdbc.queryForList("select id from department", Long.class));
    }

    /** Id hồ sơ (hoặc phòng ban) tạo ra trong lúc chạy test. */
    private List<Long> added(String table, Set<Long> before) {
        return jdbc.queryForList("select id from " + table, Long.class).stream().filter(id -> !before.contains(id)).toList();
    }

    /** Kết nối không tự commit (auto-commit: false) nên phải dọn trong giao dịch. */
    @AfterEach
    void clean() {
        tx.executeWithoutResult(status -> {
            List<Long> employees = added("employee", employeesBefore);
            employees.forEach(id -> jdbc.update("update employee set manager_id = null where id = ?", id));
            employees.forEach(id -> jdbc.update("delete from employee where id = ?", id));
            added("department", departmentsBefore).forEach(id -> jdbc.update("delete from department where id = ?", id));
        });
    }

    static Map<EmployeeField, Object> row(Object... kv) {
        Map<EmployeeField, Object> m = new EnumMap<>(EmployeeField.class);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((EmployeeField) kv[i], kv[i + 1]);
        }
        return m;
    }

    @SafeVarargs
    static MockMultipartFile file(Map<EmployeeField, Object>... rows) {
        return new MockMultipartFile("file", "nhan-vien.xlsx", XLSX, EmployeeImportFiles.xlsx(List.of(rows)));
    }

    /** Ba dòng hợp lệ, không cảnh báo. */
    static MockMultipartFile threeValid() {
        return file(
            row(FULL_NAME, "Nguyễn Văn Thử", ID_NUMBER, "099100000001", JOIN_DATE, JOIN, TAX_CODE, "8000000001", BANK_ACCOUNT, "0123400001", SOCIAL_INSURANCE_SALARY, 9_000_000),
            row(FULL_NAME, "Trần Thị Mẫu", ID_NUMBER, "099100000002", JOIN_DATE, JOIN),
            row(FULL_NAME, "Lê Văn Giả", ID_NUMBER, "099100000003", JOIN_DATE, JOIN)
        );
    }

    ResultActions save(MockMultipartFile f, boolean ack) throws Exception {
        return mockMvc.perform(multipart(API).file(f).param("warningsAcknowledged", String.valueOf(ack)));
    }

    @Test
    void templateDownloads() throws Exception {
        mockMvc
            .perform(get(API + "/template.xlsx"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(XLSX))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("mau-nhap-nhan-vien.xlsx")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void forbiddenForAccountant() throws Exception {
        expectForbiddenEverywhere();
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void forbiddenForEmployee() throws Exception {
        expectForbiddenEverywhere();
    }

    private void expectForbiddenEverywhere() throws Exception {
        mockMvc.perform(get(API + "/template.xlsx")).andExpect(status().isForbidden());
        mockMvc.perform(multipart(API + "/check").file(threeValid())).andExpect(status().isForbidden());
        mockMvc.perform(multipart(API + "/issues.xlsx").file(threeValid())).andExpect(status().isForbidden());
        save(threeValid(), true).andExpect(status().isForbidden());
        assertThat(added("employee", employeesBefore)).isEmpty();
    }

    @Test
    void checkWritesNothing() throws Exception {
        long employees = employeeRepository.count();
        long departments = departmentRepository.count();
        String body = mockMvc
            .perform(multipart(API + "/check").file(threeValid()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rowCount").value(3))
            .andExpect(jsonPath("$.canSave").value(true))
            .andExpect(jsonPath("$.errorCount").value(0))
            .andExpect(jsonPath("$.rows[0].excelRow").value(2))
            .andExpect(jsonPath("$.rows[0].code").value(org.hamcrest.Matchers.matchesPattern("DOIT26\\d+")))
            .andExpect(jsonPath("$.rows[0].codeProvisional").value(true))
            .andExpect(jsonPath("$.rows[0].personType").value("NHAN_VIEN"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("taxCode", "bankAccount", "socialInsuranceSalary", "8000000001", "0123400001");
        assertThat(employeeRepository.count()).isEqualTo(employees);
        assertThat(departmentRepository.count()).isEqualTo(departments);
    }

    @Test
    void notXlsxRejected() throws Exception {
        byte[] good = EmployeeImportFiles.xlsx(List.of(row(FULL_NAME, "Nguyễn Văn Thử", ID_NUMBER, "099100000001")));
        mockMvc
            .perform(multipart(API + "/check").file(new MockMultipartFile("file", "nhan-vien.csv", "text/csv", good)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeeimportnotxlsx"))
            .andExpect(jsonPath("$.detail").value("Chỉ nhận file Excel .xlsx (Excel 2007 trở lên), lưu từ mẫu nhập nhân viên"));
        mockMvc
            .perform(
                multipart(API + "/check").file(new MockMultipartFile("file", "gia.xlsx", XLSX, "không phải Excel".getBytes(StandardCharsets.UTF_8)))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeeimportnotxlsx"));
        save(new MockMultipartFile("file", "gia.xlsx", XLSX, "PKxx-hong".getBytes(StandardCharsets.UTF_8)), true)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.employeeimportnotxlsx"));
    }

    @Test
    void saveCreatesEmployeesDepartmentsAndManagers() throws Exception {
        MockMultipartFile f = file(
            row(FULL_NAME, "Nguyễn Văn Thử", ID_NUMBER, "099100000011", JOIN_DATE, JOIN, DEPARTMENT, "Kinh doanh", MANAGER, "099100000013"),
            row(FULL_NAME, "Trần Thị Mẫu", ID_NUMBER, "099100000012", JOIN_DATE, JOIN, PERSON_TYPE, "Thực tập"),
            row(FULL_NAME, "Lê Văn Giả", ID_NUMBER, "099100000013", JOIN_DATE, JOIN, CODE, "doit2690")
        );
        String body = save(f, true)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdCount").value(3))
            .andExpect(jsonPath("$.createdDepartments.length()").value(1))
            .andExpect(jsonPath("$.createdDepartments[0]").value("Kinh doanh"))
            .andExpect(jsonPath("$.created[2].code").value("DOIT2690"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        Employee first = byIdNumber("099100000011");
        Employee intern = byIdNumber("099100000012");
        Employee boss = byIdNumber("099100000013");
        for (Employee e : List.of(first, intern, boss)) {
            assertThat(e.getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
            assertThat(e.getUser()).isNull();
            assertThat(e.getDependants()).isZero();
        }
        assertThat(first.getManager().getId()).isEqualTo(boss.getId());
        assertThat(first.getDepartment().getName()).isEqualTo("Kinh doanh");
        assertThat(first.getDepartment().getActive()).isTrue();
        assertThat(intern.getPersonType()).isEqualTo(PersonType.THUC_TAP);
        assertThat(intern.getCode()).matches("TT26\\d+");
        assertThat(boss.getCode()).isEqualTo("DOIT2690");
        assertThat((String) JsonPath.read(body, "$.created[0].code")).isEqualTo(first.getCode());
    }

    @Test
    void saveMapsEveryField() throws Exception {
        LocalDate birth = LocalDate.of(1992, 5, 17);
        LocalDate issue = LocalDate.of(2021, 8, 15);
        LocalDate bhxhStart = LocalDate.of(2020, 7, 1);
        MockMultipartFile f = file(
            row(
                FULL_NAME, "Nguyễn Văn Thử", PERSON_TYPE, "Nhân viên", GENDER, "Nữ", BIRTH_DATE, birth, ID_NUMBER, "099100000041",
                ID_ISSUE_DATE, issue, ID_ISSUE_PLACE, "Cục Giả", RESIDENCE, "12 Đường Thử, Quận Giả", PHONE, "0900000041",
                EMAIL, "thu41@example.com", EDUCATION, "Đại học", TAX_CODE, "8000000041", BANK_ACCOUNT, "0123400041",
                BANK_NAME, "Ngân hàng Giả", SOCIAL_INSURANCE_NO, "7900000041", SOCIAL_INSURANCE_START, bhxhStart,
                SOCIAL_INSURANCE_SALARY, 12_345_000, INSURANCE_EXEMPTION, "Khác", INSURANCE_EXEMPTION_NOTE, "Thỏa thuận riêng",
                DEPENDANTS, 3, DEPARTMENT, "Phòng Thử Nghiệm", POSITION, "Chuyên viên", JOIN_DATE, JOIN, NOTE, "Ghi chú thử"
            ),
            row(FULL_NAME, "Trần Thị Mẫu", PERSON_TYPE, "HĐ dịch vụ", GENDER, "Nam", ID_NUMBER, "099100000042", JOIN_DATE, JOIN)
        );
        save(f, true).andExpect(status().isCreated());

        Employee e = byIdNumber("099100000041");
        assertThat(e.getCode()).matches("DOIT26\\d+");
        assertThat(e.getPersonType()).isEqualTo(PersonType.NHAN_VIEN);
        assertThat(e.getFullName()).isEqualTo("Nguyễn Văn Thử");
        assertThat(e.getGender()).isEqualTo(vn.doit.hrm.domain.enumeration.Gender.NU);
        assertThat(e.getBirthDate()).isEqualTo(birth);
        assertThat(e.getIdIssueDate()).isEqualTo(issue);
        assertThat(e.getIdIssuePlace()).isEqualTo("Cục Giả");
        assertThat(e.getResidence()).isEqualTo("12 Đường Thử, Quận Giả");
        assertThat(e.getPhone()).isEqualTo("0900000041");
        assertThat(e.getEmail()).isEqualTo("thu41@example.com");
        assertThat(e.getEducation()).isEqualTo("Đại học");
        assertThat(e.getTaxCode()).isEqualTo("8000000041");
        assertThat(e.getBankAccount()).isEqualTo("0123400041");
        assertThat(e.getBankName()).isEqualTo("Ngân hàng Giả");
        assertThat(e.getSocialInsuranceNo()).isEqualTo("7900000041");
        assertThat(e.getSocialInsuranceStart()).isEqualTo(bhxhStart);
        assertThat(e.getSocialInsuranceSalary()).isEqualTo(12_345_000L);
        assertThat(e.getInsuranceExemption()).isEqualTo(vn.doit.hrm.domain.enumeration.InsuranceExemption.KHAC);
        assertThat(e.getInsuranceExemptionNote()).isEqualTo("Thỏa thuận riêng");
        assertThat(e.getDependants()).isEqualTo(3);
        assertThat(e.getDepartment().getName()).isEqualTo("Phòng Thử Nghiệm");
        assertThat(e.getPosition()).isEqualTo("Chuyên viên");
        assertThat(e.getJoinDate()).isEqualTo(JOIN);
        assertThat(e.getNote()).isEqualTo("Ghi chú thử");

        Employee service = byIdNumber("099100000042");
        assertThat(service.getPersonType()).isEqualTo(PersonType.DICH_VU);
        assertThat(service.getCode()).startsWith("HDDV");
        assertThat(service.getGender()).isEqualTo(vn.doit.hrm.domain.enumeration.Gender.NAM);
        assertThat(service.getJoinDate()).isEqualTo(JOIN);
    }

    @Test
    void orphanExemptionNoteIsDropped() throws Exception {
        save(file(row(FULL_NAME, "Nguyễn Văn Thử", ID_NUMBER, "099100000051", JOIN_DATE, JOIN, INSURANCE_EXEMPTION_NOTE, "Lạc đề")), true)
            .andExpect(status().isCreated());
        assertThat(byIdNumber("099100000051").getInsuranceExemptionNote()).isNull();
    }

    @Test
    void checkFlagsOversizedDependants() throws Exception {
        mockMvc
            .perform(multipart(API + "/check").file(file(row(FULL_NAME, "Nguyễn Văn Thử", ID_NUMBER, "099100000061", JOIN_DATE, JOIN, DEPENDANTS, 3_000_000_000L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canSave").value(false))
            .andExpect(jsonPath("$.issues[0].field").value("DEPENDANTS"))
            .andExpect(jsonPath("$.issues[0].message").value("Số người phụ thuộc quá lớn"));
    }

    private Employee byIdNumber(String idNumber) {
        List<Employee> found = employeeRepository.findAllByTrimmedIdNumber(idNumber);
        assertThat(found).hasSize(1);
        return employeeRepository.findOneWithEagerRelationships(found.get(0).getId()).orElseThrow();
    }

    @Test
    void saveUsesSamePlanAsCheck() throws Exception {
        MockMultipartFile f = file(
            row(FULL_NAME, "Nguyễn Văn Thử", ID_NUMBER, "099100000021", JOIN_DATE, JOIN),
            row(FULL_NAME, "Trần Thị Mẫu", ID_NUMBER, "099100000022", JOIN_DATE, JOIN, PERSON_TYPE, "Thực tập"),
            row(FULL_NAME, "Lê Văn Giả", ID_NUMBER, "099100000023", JOIN_DATE, JOIN, CODE, "DOIT2695"),
            row(FULL_NAME, "Phạm Thị Thử", ID_NUMBER, "099100000024", JOIN_DATE, JOIN)
        );
        String check = mockMvc
            .perform(multipart(API + "/check").file(f))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        String saved = save(f, true).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Map<Integer, String> planned = new HashMap<>();
        List<Map<String, Object>> rows = JsonPath.read(check, "$.rows");
        rows.forEach(r -> planned.put((Integer) r.get("excelRow"), (String) r.get("code")));
        Map<Integer, String> real = new HashMap<>();
        List<Map<String, Object>> created = JsonPath.read(saved, "$.created");
        created.forEach(r -> real.put((Integer) r.get("excelRow"), (String) r.get("code")));
        assertThat(real).isEqualTo(planned);
        assertThat(real.get(2)).isNotEqualTo("DOIT2695");
        assertThat(real.get(5)).isNotEqualTo("DOIT2695");
    }

    @Test
    void errorsBlockSave() throws Exception {
        save(file(row(FULL_NAME, "Nguyễn Văn Thử", JOIN_DATE, JOIN), row(FULL_NAME, "Trần Thị Mẫu", ID_NUMBER, "099100000032", JOIN_DATE, JOIN)), true)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.importinvalid"))
            .andExpect(jsonPath("$.detail").value("File còn lỗi hoặc cảnh báo chưa xác nhận; xem lại kết quả kiểm tra"))
            .andExpect(jsonPath("$.check.errorCount").value(1))
            .andExpect(jsonPath("$.check.canSave").value(false));
        assertThat(added("employee", employeesBefore)).isEmpty();
    }

    @Test
    void warningsNeedAcknowledgement() throws Exception {
        MockMultipartFile f = file(row(FULL_NAME, "Nguyễn Văn Thử", ID_NUMBER, "099100000041", JOIN_DATE, JOIN, DEPARTMENT, "Phòng Thử Nghiệm Nhập"));
        save(f, false)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.importinvalid"))
            .andExpect(jsonPath("$.check.warningCount").value(1))
            .andExpect(jsonPath("$.check.newDepartments[0].name").value("Phòng Thử Nghiệm Nhập"))
            .andExpect(jsonPath("$.check.newDepartments[0].excelRows[0]").value(2));
        assertThat(added("department", departmentsBefore)).isEmpty();
        save(f, true).andExpect(status().isCreated()).andExpect(jsonPath("$.createdCount").value(1));
    }

    @Test
    void conflictAfterCheckRollsBack() throws Exception {
        MockMultipartFile f = file(
            row(FULL_NAME, "Nguyễn Văn Thử", ID_NUMBER, "099100000051", JOIN_DATE, JOIN, DEPARTMENT, "Phòng Xung Đột Nhập"),
            row(FULL_NAME, "Trần Thị Mẫu", ID_NUMBER, "099100000052", JOIN_DATE, JOIN)
        );
        mockMvc.perform(multipart(API + "/check").file(f)).andExpect(status().isOk()).andExpect(jsonPath("$.canSave").value(true));

        Employee other = new Employee();
        other.setCode("ITNHAP01");
        other.setPersonType(PersonType.NHAN_VIEN);
        other.setFullName("Người Chen Giữa");
        other.setIdNumber("099100000052");
        other.setDependants(0);
        other.setStatus(EmploymentStatus.DANG_LAM_VIEC);
        employeeRepository.saveAndFlush(other);
        long employees = employeeRepository.count();
        long departments = departmentRepository.count();

        save(f, true)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.importinvalid"))
            .andExpect(jsonPath("$.check.errorCount").value(1));
        assertThat(employeeRepository.count()).isEqualTo(employees);
        assertThat(employeeRepository.findAllByTrimmedIdNumber("099100000051")).isEmpty();
        assertThat(departmentRepository.count()).isEqualTo(departments);
    }

    @Test
    void issueReport() throws Exception {
        byte[] report = mockMvc
            .perform(multipart(API + "/issues.xlsx").file(file(row(FULL_NAME, "Nguyễn Văn Thử", JOIN_DATE, JOIN))))
            .andExpect(status().isOk())
            .andExpect(content().contentType(XLSX))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("loi-nhap-nhan-vien.xlsx")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(report))) {
            Sheet sh = wb.getSheet("Lỗi và cảnh báo");
            assertThat(sh).isNotNull();
            Row h = sh.getRow(0);
            assertThat(List.of(h.getCell(0).getStringCellValue(), h.getCell(1).getStringCellValue(), h.getCell(2).getStringCellValue(), h.getCell(3).getStringCellValue()))
                .containsExactly("Dòng", "Họ tên", "Mức độ", "Nội dung");
            Row first = sh.getRow(1);
            assertThat(first.getCell(0).getNumericCellValue()).isEqualTo(2);
            assertThat(first.getCell(1).getStringCellValue()).isEqualTo("Nguyễn Văn Thử");
            assertThat(first.getCell(2).getStringCellValue()).isEqualTo("Lỗi");
            assertThat(first.getCell(3).getStringCellValue()).contains("Thiếu Số CCCD");
        }
    }
}

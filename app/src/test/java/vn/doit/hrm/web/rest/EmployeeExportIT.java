package vn.doit.hrm.web.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.*;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;

/** Xuất danh sách nhân viên ra Excel. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class EmployeeExportIT {

    private static final List<String> BASE_HEADERS = List.of(
        "Mã NV",
        "Họ và tên",
        "Giới tính",
        "Ngày sinh",
        "Email",
        "SĐT",
        "Phòng ban",
        "Chức danh",
        "Nơi làm việc hiện tại",
        "Loại HĐ hiện tại",
        "Tình trạng",
        "Ngày vào công ty"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @BeforeEach
    void init() {
        Employee a = employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9101")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Trần Minh Quân")
                .searchText("nv9101 tran minh quan")
                .gender(Gender.NAM)
                .birthDate(LocalDate.of(1990, 5, 17))
                .email("quan@doit.vn")
                .phone("0900000001")
                .position("Kỹ sư")
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
                .joinDate(LocalDate.of(2023, 1, 9))
                .idNumber("079123456789")
                .taxCode("8001234567")
                .bankAccount("111222333")
        );
        employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9102")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Lê Thu Hà")
                .searchText("nv9102 le thu ha")
                .gender(Gender.NU)
                .dependants(0)
                .status(EmploymentStatus.DA_NGHI)
                .joinDate(LocalDate.of(2022, 3, 1))
        );
        contractRepository.saveAndFlush(
            new EmploymentContract()
                .type(ContractType.HDLD_XDTH)
                .termMonths(12)
                .startDate(LocalDate.of(2023, 1, 9))
                .endDate(LocalDate.of(2099, 1, 8))
                .salary(15_000_000L)
                .salaryBasis(SalaryBasis.GROSS)
                .status(ContractStatus.DA_KY)
                .employee(a)
        );
    }

    private Sheet sheetOf(byte[] body) throws Exception {
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(body));
        Sheet sheet = wb.getSheet("Nhân viên");
        assertNotNull(sheet, "phải có sheet 'Nhân viên'");
        return sheet;
    }

    private List<String> headers(Sheet sheet) {
        List<String> out = new ArrayList<>();
        for (Cell c : sheet.getRow(0)) out.add(c.getStringCellValue());
        return out;
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void exportsFilteredRowsWithTypedCells() throws Exception {
        byte[] body = mockMvc
            .perform(get("/api/employees/export.xlsx").param("status.equals", "DANG_LAM_VIEC").param("code.contains", "NV910"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"nhan-vien.xlsx\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        Sheet sheet = sheetOf(body);
        List<String> expected = new ArrayList<>(BASE_HEADERS);
        expected.addAll(List.of("Lương hiện tại", "Net/Gross", "Lương Gross"));
        assertEquals(expected, headers(sheet));
        assertEquals(1, sheet.getLastRowNum(), "đúng 1 dòng dữ liệu");

        Row r = sheet.getRow(1);
        assertEquals("NV9101", r.getCell(0).getStringCellValue());
        assertEquals("Nam", r.getCell(2).getStringCellValue());
        Cell birth = r.getCell(3);
        assertEquals(CellType.NUMERIC, birth.getCellType());
        assertTrue(DateUtil.isCellDateFormatted(birth));
        assertEquals("dd/mm/yyyy", birth.getCellStyle().getDataFormatString());
        assertEquals(LocalDate.of(1990, 5, 17), birth.getLocalDateTimeCellValue().toLocalDate());
        assertEquals("HĐLĐ xác định thời hạn", r.getCell(9).getStringCellValue());
        assertEquals("Đang làm việc", r.getCell(10).getStringCellValue());
        Cell join = r.getCell(11);
        assertTrue(DateUtil.isCellDateFormatted(join));
        assertEquals("dd/mm/yyyy", join.getCellStyle().getDataFormatString());
        Cell salary = r.getCell(12);
        assertEquals(CellType.NUMERIC, salary.getCellType());
        assertEquals(15_000_000d, salary.getNumericCellValue());
        assertEquals("#,##0", salary.getCellStyle().getDataFormatString());
        assertEquals("GROSS", r.getCell(13).getStringCellValue());
        assertEquals(15_000_000d, r.getCell(14).getNumericCellValue());
        // không xuất trường nhạy cảm
        for (Row row : sheet) for (Cell c : row) {
            String s = c.getCellType() == CellType.STRING ? c.getStringCellValue() : "";
            assertFalse(s.contains("079123456789") || s.contains("8001234567") || s.contains("111222333"));
        }
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR")
    void leavingEmployeeHasLabelAndNoSalary() throws Exception {
        byte[] body = mockMvc
            .perform(get("/api/employees/export.xlsx").param("code.equals", "NV9102"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        Row r = sheetOf(body).getRow(1);
        assertEquals("Nữ", r.getCell(2).getStringCellValue());
        assertEquals("Đã nghỉ", r.getCell(10).getStringCellValue());
        assertTrue(r.getCell(9) == null || r.getCell(9).getCellType() == CellType.BLANK);
        assertTrue(r.getCell(12) == null || r.getCell(12).getCellType() == CellType.BLANK);
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantHasNoSalaryColumns() throws Exception {
        byte[] body = mockMvc
            .perform(get("/api/employees/export.xlsx"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        List<String> h = headers(sheetOf(body));
        assertEquals(BASE_HEADERS, h);
        assertFalse(h.contains("Lương hiện tại"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void bgdHasSalaryColumns() throws Exception {
        byte[] body = mockMvc.perform(get("/api/employees/export.xlsx")).andReturn().getResponse().getContentAsByteArray();
        assertTrue(headers(sheetOf(body)).contains("Lương hiện tại"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void sensitiveFilterAndSortAreRejected() throws Exception {
        mockMvc.perform(get("/api/employees/export.xlsx").param("idNumber.equals", "079123456789")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/export.xlsx").param("sort", "taxCode,asc")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void plainUserForbidden() throws Exception {
        mockMvc.perform(get("/api/employees/export.xlsx")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/employees/export.xlsx")).andExpect(status().isUnauthorized());
    }
}

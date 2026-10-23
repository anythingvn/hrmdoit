package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.*;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.security.AuthoritiesConstants;

/**
 * Quản trị ({@code ROLE_ADMIN}, không kèm vai trò nào khác) làm được mọi việc của mọi vai trò.
 *
 * <p>Phần 1 quét <b>mọi</b> endpoint trong {@code vn.doit.hrm.web.rest} (đọc từ {@link RequestMappingHandlerMapping}, nên
 * endpoint mới tự được kiểm) và khẳng định ADMIN không bao giờ bị 403 (404/400… vì dữ liệu giả là được). Phần 2 kiểm
 * các việc quan trọng trả 2xx thật với dữ liệu thật: dữ liệu nhạy cảm đầy đủ, xuất Excel có cột lương, lịch sử,
 * backup-status, tài khoản, ghi hồ sơ/HĐ/lương.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AdminFullAccessIT {

    private static final RequestPostProcessor ADMIN = user("admin-full-access").authorities(
        new org.springframework.security.core.authority.SimpleGrantedAuthority(AuthoritiesConstants.ADMIN)
    );
    private static final RequestPostProcessor NHAN_VIEN = user("nv-full-access").authorities(
        new org.springframework.security.core.authority.SimpleGrantedAuthority(AuthoritiesConstants.NHAN_VIEN)
    );

    private static final Pattern PATH_VAR = Pattern.compile("\\{([^}:]+)(:[^}]*)?}");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    private Employee employee;

    @BeforeEach
    void init() {
        employee = employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9701")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Phạm Quản Trị")
                .searchText("nv9701 pham quan tri")
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
                .joinDate(LocalDate.of(2024, 1, 2))
                .idNumber("079555666777")
                .idIssueDate(LocalDate.of(2021, 3, 4))
                .idIssuePlace("Cục CSQLHC")
                .taxCode("8009998887")
                .bankAccount("444555666")
                .bankName("ACB")
                .socialInsuranceNo("7900445566")
        );
        contractRepository.saveAndFlush(
            new EmploymentContract()
                .type(ContractType.HDLD_KXDTH)
                .startDate(LocalDate.of(2024, 1, 2))
                .salary(21_000_000L)
                .salaryBasis(SalaryBasis.GROSS)
                .status(ContractStatus.DA_KY)
                .employee(employee)
        );
    }

    /**
     * Ngoại lệ duy nhất có chủ ý (spec lưu trữ §6.1): xóa hẳn nhân viên và nhật ký xóa chỉ dành cho login {@code admin}; mọi
     * tài khoản Quản trị khác nhận 403 {@code deleterequiressystemadmin}. Test {@link #systemAdminOnlyEndpointsRefuseOtherAdmins}
     * khẳng định đúng mã lỗi đó, để danh sách này không thành chỗ giấu 403 khác.
     */
    private static final Set<String> SYSTEM_ADMIN_ONLY = Set.of(
        "EmployeeDeletionResource#deleteEmployee",
        "EmployeeDeletionResource#deletions"
    );

    /** Một lời gọi cụ thể tới một endpoint. */
    private record Call(HttpMethod method, String path, String handler) {
        @Override
        public String toString() {
            return method + " " + path + " (" + handler + ")";
        }
    }

    /** Mọi endpoint của vn.doit.hrm.web.rest, biến đường dẫn thay bằng giá trị không tồn tại. */
    private List<Call> allRestCalls() {
        List<Call> calls = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            if (!hm.getBeanType().getPackageName().equals("vn.doit.hrm.web.rest")) {
                continue;
            }
            Set<RequestMethod> methods = e.getKey().getMethodsCondition().getMethods();
            Set<RequestMethod> effective = methods.isEmpty() ? Set.of(RequestMethod.GET) : methods;
            for (String pattern : e.getKey().getPatternValues()) {
                for (RequestMethod m : effective) {
                    calls.add(new Call(m.asHttpMethod(), fill(pattern), hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName()));
                }
            }
        }
        return calls;
    }

    private static String fill(String pattern) {
        Matcher m = PATH_VAR.matcher(pattern);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1).toLowerCase();
            m.appendReplacement(sb, name.contains("login") ? "khong-ton-tai-admin-it" : "987654321");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private int statusOf(Call call, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder req = request(call.method(), call.path()).with(who);
        if (call.method() == HttpMethod.POST || call.method() == HttpMethod.PUT || call.method() == HttpMethod.PATCH) {
            req = req.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return mockMvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    void adminIsNeverForbiddenOnAnyRestEndpoint() throws Exception {
        List<Call> calls = allRestCalls();
        // Lưới an toàn: phải tìm thấy đủ endpoint (hiện ~95), tránh test "xanh" vì không quét gì.
        assertThat(calls).hasSizeGreaterThan(80);
        Set<String> forbidden = new TreeSet<>();
        for (Call call : calls) {
            if (SYSTEM_ADMIN_ONLY.contains(call.handler())) {
                continue;
            }
            if (statusOf(call, ADMIN) == 403) {
                forbidden.add(call.toString());
            }
        }
        assertThat(forbidden).as("ADMIN bị 403 ở các endpoint").isEmpty();
    }

    /**
     * Lớp phương thức: tham số thiếu hoặc body không hợp lệ bị trả 400 <i>trước</i> khi {@code @PreAuthorize} chạy, nên
     * phần quét HTTP chỉ chứng minh lớp URL. Ở đây gọi thẳng từng phương thức controller qua proxy bảo mật (tham số
     * null/0) với ADMIN: lỗi gì cũng được (NPE, 404…), trừ bị từ chối quyền.
     */
    @Test
    void adminPassesEveryMethodSecurityCheck() {
        Set<String> denied = new TreeSet<>();
        int checked = 0;
        for (HandlerMethod hm : restHandlerMethods()) {
            checked++;
            if (SYSTEM_ADMIN_ONLY.contains(hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName())) {
                continue;
            }
            if (deniedByMethodSecurity(hm, AuthoritiesConstants.ADMIN)) {
                denied.add(hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName());
            }
        }
        assertThat(checked).isGreaterThan(80);
        assertThat(denied).as("ADMIN bị @PreAuthorize từ chối ở").isEmpty();
    }

    @Test
    void systemAdminOnlyEndpointsRefuseOtherAdmins() throws Exception {
        List<Call> calls = allRestCalls()
            .stream()
            .filter(c -> SYSTEM_ADMIN_ONLY.contains(c.handler()))
            .toList();
        assertThat(calls).hasSize(SYSTEM_ADMIN_ONLY.size());
        for (Call call : calls) {
            var req = MockMvcRequestBuilders.request(call.method(), call.path()).with(ADMIN);
            if (call.method() == HttpMethod.DELETE) {
                req = req.contentType(MediaType.APPLICATION_JSON).content("{\"confirmCode\":\"x\",\"reason\":\"y\"}");
            }
            mockMvc
                .perform(req)
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(
                    MockMvcResultMatchers.jsonPath("$.message").value(
                        "error.deleterequiressystemadmin"
                    )
                );
        }
    }

    @Test
    void methodSecurityCheckDetectsDenial() {
        // Đối chứng: nhân viên thường bị từ chối ở mọi phương thức quản trị tài khoản.
        List<HandlerMethod> userAdmin = restHandlerMethods()
            .stream()
            .filter(hm -> hm.getBeanType().getSimpleName().equals("UserResource"))
            .toList();
        assertThat(userAdmin).isNotEmpty();
        for (HandlerMethod hm : userAdmin) {
            assertThat(deniedByMethodSecurity(hm, AuthoritiesConstants.NHAN_VIEN)).as(hm.getMethod().getName()).isTrue();
        }
    }

    private List<HandlerMethod> restHandlerMethods() {
        return handlerMapping
            .getHandlerMethods()
            .values()
            .stream()
            .filter(hm -> hm.getBeanType().getPackageName().equals("vn.doit.hrm.web.rest"))
            .distinct()
            .toList();
    }

    private boolean deniedByMethodSecurity(HandlerMethod hm, String authority) {
        Object bean = applicationContext.getBean((String) hm.getBean());
        java.lang.reflect.Method method = hm.getMethod();
        Object[] args = new Object[method.getParameterCount()];
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            args[i] = defaultValue(types[i]);
        }
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "method-check",
                "n/a",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(authority))
            )
        );
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            method.invoke(bean, args);
            return false;
        } catch (java.lang.reflect.InvocationTargetException e) {
            return e.getCause() instanceof org.springframework.security.access.AccessDeniedException;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        return '\0';
    }

    @Test
    void sweepReallyGoesThroughSecurity() throws Exception {
        // Đối chứng: cùng cách quét, nhân viên thường phải bị 403 ở mọi endpoint /api/admin/**.
        List<Call> adminOnly = allRestCalls()
            .stream()
            .filter(c -> c.path().startsWith("/api/admin/"))
            .toList();
        assertThat(adminOnly).isNotEmpty();
        for (Call call : adminOnly) {
            assertThat(statusOf(call, NHAN_VIEN)).as(call.toString()).isEqualTo(403);
        }
    }

    @Test
    void adminSeesFullSensitiveData() throws Exception {
        mockMvc
            .perform(get("/api/employees/{id}/sensitive", employee.getId()).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idNumber").value("079555666777"))
            .andExpect(jsonPath("$.idIssueDate").value("2021-03-04"))
            .andExpect(jsonPath("$.idIssuePlace").value("Cục CSQLHC"))
            .andExpect(jsonPath("$.taxCode").value("8009998887"))
            .andExpect(jsonPath("$.bankAccount").value("444555666"))
            .andExpect(jsonPath("$.bankName").value("ACB"))
            .andExpect(jsonPath("$.socialInsuranceNo").value("7900445566"));
        // Lọc theo trường nhạy cảm chỉ dành cho ADMIN/HR
        mockMvc
            .perform(get("/api/employees").param("idNumber.equals", "079555666777").with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].code").value(hasItem("NV9701")));
    }

    @Test
    void adminExportHasSalaryColumns() throws Exception {
        byte[] body = mockMvc
            .perform(get("/api/employees/export.xlsx").param("code.equals", "NV9701").with(ADMIN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = wb.getSheet("Nhân viên");
            List<String> headers = new ArrayList<>();
            for (Cell c : sheet.getRow(0)) headers.add(c.getStringCellValue());
            assertThat(headers).contains("Lương hiện tại", "Net/Gross", "Lương Gross");
            Row row = sheet.getRow(1);
            assertThat(row.getCell(headers.indexOf("Lương hiện tại")).getNumericCellValue()).isEqualTo(21_000_000d);
        }
    }

    @Test
    void adminReadsHistoryRecordsAndAdminPages() throws Exception {
        Long id = employee.getId();
        mockMvc.perform(get("/api/employees/{id}/history", id).with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}/contracts", id).with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}/salary-changes", id).with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}/allowances", id).with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}/assignments/timeline", id).with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}/documents", id).with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}/contract-advice", id).with(ADMIN)).andExpect(status().isOk());
        mockMvc
            .perform(get("/api/employees/{id}/termination/blockers", id).param("lastWorkingDay", "2026-12-31").with(ADMIN))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}/account", id).with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/account-candidates").with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/count").with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/salary-changes").with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employment-contracts").with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/work-assignments").with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/employee-documents").with(ADMIN)).andExpect(status().isOk());
        mockMvc
            .perform(get("/api/salary-tools/gross-suggestion").param("net", "26215000").param("month", "2026-10").with(ADMIN))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/backup-status").with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/authorities").with(ADMIN)).andExpect(status().isOk());
    }

    @Test
    void adminWritesEverything() throws Exception {
        Long id = employee.getId();
        mockMvc
            .perform(
                post("/api/employees")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"NV9702\",\"personType\":\"NHAN_VIEN\",\"fullName\":\"Lê Quản Trị\",\"idNumber\":\"0792\"}")
            )
            .andExpect(status().isCreated());
        mockMvc
            .perform(
                post("/api/salary-changes")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"effectiveDate\":\"2026-10-01\",\"amount\":23000000,\"basis\":\"GROSS\",\"employee\":{\"id\":" + id + "}}")
            )
            .andExpect(status().isCreated());
        mockMvc
            .perform(
                post("/api/employees/{id}/account", id)
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"authorities\":[\"ROLE_HR\"]}")
            )
            .andExpect(status().isCreated())
            // Tên đăng nhập = số CCCD của hồ sơ.
            .andExpect(jsonPath("$.login").value("079555666777"));
        mockMvc.perform(put("/api/admin/users/{login}/password/default", "079555666777").with(ADMIN)).andExpect(status().isNoContent());
        mockMvc
            .perform(
                put("/api/admin/users/{login}/password", "079555666777")
                    .with(ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newPassword\":\"matkhau-moi-123\"}")
            )
            .andExpect(status().is2xxSuccessful());
    }
}

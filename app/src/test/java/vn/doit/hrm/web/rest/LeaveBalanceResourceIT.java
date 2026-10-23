package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.DayPart;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.leave.LeaveLedgerService;

/** API số dư, sổ phép, điều chỉnh, cài đặt và trưởng phòng. Hôm nay = 22/10/2026. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(LeaveBalanceResourceIT.FixedClockConfig.class)
class LeaveBalanceResourceIT {

    private static final RequestPostProcessor HR = user("hr-leave").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor ADMIN = user("admin-leave").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor BGD = user("bgd-leave").authorities(() -> "ROLE_BGD");

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveLedgerService ledger;

    private Department sales;
    private Department tech;
    private Employee me;
    private Employee head;
    private Employee other;

    private static RequestPostProcessor as(String login) {
        return user(login).authorities(() -> "ROLE_NHAN_VIEN");
    }

    private Employee withLogin(Employee e, String login) {
        e.setUser(LeaveFixtures.user(userRepository, login));
        return employeeRepository.saveAndFlush(e);
    }

    @BeforeEach
    void init() {
        sales = LeaveFixtures.department(departmentRepository, "Kinh doanh thử phép");
        tech = LeaveFixtures.department(departmentRepository, "Kỹ thuật thử phép");
        head = LeaveFixtures.employee(employeeRepository, "NV9601", PersonType.NHAN_VIEN, LocalDate.of(2020, 1, 6));
        head.setDepartment(sales);
        withLogin(head, "truongphong-leave");
        sales.setHead(head);
        departmentRepository.saveAndFlush(sales);

        me = LeaveFixtures.employee(employeeRepository, "NV9602", PersonType.NHAN_VIEN, LocalDate.of(2025, 2, 3));
        me.setDepartment(sales);
        me.setPriorSocialInsuranceYears(14);
        me.setSocialInsuranceStart(LocalDate.of(2025, 2, 1));
        withLogin(me, "nv-leave");

        other = LeaveFixtures.employee(employeeRepository, "NV9603", PersonType.NHAN_VIEN, LocalDate.of(2025, 2, 3));
        other.setDepartment(tech);
        employeeRepository.saveAndFlush(other);

        ledger.post(
            me.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal("4.75"))),
            LedgerReason.DAU_KY,
            "OPENING",
            null,
            "Số dư đầu kỳ"
        );
        ledger.post(
            me.getId(),
            Pool.NGHI_BU,
            List.of(new Entry(Pool.NGHI_BU, Bucket.NAM_NAY, new BigDecimal("6"))),
            LedgerReason.DAU_KY,
            "OPENING",
            null,
            "Số dư đầu kỳ"
        );
    }

    @Test
    void myBalanceForEmployee() throws Exception {
        mockMvc
            .perform(get("/api/leave/balances/me").with(as("nv-leave")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.employeeId").value(me.getId()))
            .andExpect(jsonPath("$.annual.previousYear").value(0))
            .andExpect(jsonPath("$.annual.currentYear").value(4.75))
            .andExpect(jsonPath("$.annual.total").value(4.75))
            .andExpect(jsonPath("$.annual.requestable").value(5.5))
            .andExpect(jsonPath("$.compensatory.total").value(6))
            .andExpect(jsonPath("$.sick.usedDays").value(0))
            // 14 năm trước khi vào + 1 năm ở DO IT = 15 năm → 40 ngày.
            .andExpect(jsonPath("$.sick.limitDays").value(40))
            .andExpect(jsonPath("$.overtime.monthHours").value(0))
            .andExpect(jsonPath("$.overtime.monthCap").value(40));
    }

    @Test
    void internHasNoLedger() throws Exception {
        Employee tt = LeaveFixtures.employee(employeeRepository, "TT9601", PersonType.THUC_TAP, LocalDate.of(2026, 9, 1));
        withLogin(tt, "tt-leave");

        mockMvc
            .perform(get("/api/leave/balances/me").with(as("tt-leave")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.leavenotapplicable"));
        mockMvc
            .perform(get("/api/leave/ledger/{id}", tt.getId()).with(as("tt-leave")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.leavenotapplicable"));
    }

    @Test
    void accountWithoutProfileHasNoLedger() throws Exception {
        mockMvc
            .perform(get("/api/leave/balances/me").with(HR))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("error.leavenoprofile"));
    }

    @Test
    void adjustmentNeedsNoteAndHrOrAdmin() throws Exception {
        String noNote = "{\"employeeId\":" + me.getId() + ",\"pool\":\"PHEP_NAM\",\"amount\":1}";
        mockMvc
            .perform(post("/api/leave/adjustments").with(HR).contentType(MediaType.APPLICATION_JSON).content(noNote))
            .andExpect(status().isBadRequest());
        String blankNote = "{\"employeeId\":" + me.getId() + ",\"pool\":\"PHEP_NAM\",\"amount\":1,\"note\":\"  \"}";
        mockMvc
            .perform(post("/api/leave/adjustments").with(HR).contentType(MediaType.APPLICATION_JSON).content(blankNote))
            .andExpect(status().isBadRequest());
        String zero = "{\"employeeId\":" + me.getId() + ",\"pool\":\"PHEP_NAM\",\"amount\":0,\"note\":\"Sai\"}";
        mockMvc
            .perform(post("/api/leave/adjustments").with(HR).contentType(MediaType.APPLICATION_JSON).content(zero))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leaveamountinvalid"));

        String ok =
            "{\"employeeId\":" + me.getId() + ",\"pool\":\"PHEP_NAM\",\"amount\":-1.25,\"note\":\"Trừ ngày nghỉ trước khi dùng hệ thống\"}";
        mockMvc
            .perform(post("/api/leave/adjustments").with(BGD).contentType(MediaType.APPLICATION_JSON).content(ok))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/leave/adjustments").with(as("nv-leave")).contentType(MediaType.APPLICATION_JSON).content(ok))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/leave/adjustments").with(HR).contentType(MediaType.APPLICATION_JSON).content(ok))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].reason").value("DIEU_CHINH"))
            .andExpect(jsonPath("$[0].amount").value(-1.25))
            .andExpect(jsonPath("$[0].createdBy").value("hr-leave"));
        assertThat(ledger.balance(me.getId(), Pool.PHEP_NAM).total()).isEqualByComparingTo("3.5");
    }

    private ResultActions adjust(RequestPostProcessor who, Long employeeId, String pool, String amount)
        throws Exception {
        String json = "{\"employeeId\":" + employeeId + ",\"pool\":\"" + pool + "\",\"amount\":" + amount + ",\"note\":\"Điều chỉnh thử\"}";
        return mockMvc.perform(post("/api/leave/adjustments").with(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** I4: HR không tự điều chỉnh sổ của mình (kể cả viết hoa khác), không điều chỉnh người đã nghỉ việc, nghỉ bù không xuống dưới 0. */
    @Test
    void adjustmentGuards() throws Exception {
        Employee hrSelf = LeaveFixtures.employee(employeeRepository, "NV9605", PersonType.NHAN_VIEN, LocalDate.of(2020, 1, 6));
        withLogin(hrSelf, "hr-leave");
        adjust(HR, hrSelf.getId(), "PHEP_NAM", "2")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.leaveselfadjust"));
        adjust(user("HR-LEAVE").authorities(() -> "ROLE_HR"), hrSelf.getId(), "NGHI_BU", "8")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.leaveselfadjust"));
        assertThat(ledger.entries(hrSelf.getId())).isEmpty();

        other.setStatus(EmploymentStatus.DA_NGHI);
        employeeRepository.saveAndFlush(other);
        adjust(HR, other.getId(), "PHEP_NAM", "1")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leaveemployeeterminated"));

        adjust(ADMIN, me.getId(), "NGHI_BU", "-6.5")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
        adjust(ADMIN, me.getId(), "NGHI_BU", "-6").andExpect(status().isCreated());
        // Phép năm vẫn được trừ xuống âm (thu hồi phép ứng).
        adjust(HR, me.getId(), "PHEP_NAM", "-6").andExpect(status().isCreated());
    }

    @Test
    void ledgerVisibleToSelfHrManagerAndDepartmentHead() throws Exception {
        mockMvc
            .perform(get("/api/leave/ledger/{id}", me.getId()).with(as("nv-leave")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].reason").value("DAU_KY"));
        mockMvc.perform(get("/api/leave/ledger/{id}", me.getId()).with(HR)).andExpect(status().isOk());
        mockMvc.perform(get("/api/leave/ledger/{id}", me.getId()).with(as("truongphong-leave"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/leave/ledger/{id}", other.getId()).with(as("truongphong-leave"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leave/ledger/{id}", other.getId()).with(as("nv-leave"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leave/ledger/{id}", me.getId()).with(BGD)).andExpect(status().isForbidden());

        other.setManager(me);
        employeeRepository.saveAndFlush(other);
        mockMvc.perform(get("/api/leave/ledger/{id}", other.getId()).with(as("nv-leave"))).andExpect(status().isOk());
    }

    @Test
    void companyBalancesForHrAndAdmin() throws Exception {
        mockMvc
            .perform(get("/api/leave/balances").param("departmentId", String.valueOf(sales.getId())).with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.employeeId == " + me.getId() + ")].annual.total").value(4.75));
        mockMvc.perform(get("/api/leave/balances").with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/leave/balances").with(BGD)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leave/balances").with(as("nv-leave"))).andExpect(status().isForbidden());
    }

    @Test
    void settingsReadByAllEditedByAdmin() throws Exception {
        mockMvc
            .perform(get("/api/leave/settings").with(as("nv-leave")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.advanceDays").value(1))
            .andExpect(jsonPath("$.directorThresholdDays").value(3))
            .andExpect(jsonPath("$.otMonthCap").value(40))
            .andExpect(jsonPath("$.otYearCap").value(200))
            .andExpect(jsonPath("$.carryExpiryMonth").value(3))
            .andExpect(jsonPath("$.carryExpiryDay").value(31))
            .andExpect(jsonPath("$.pastRequestDays").value(30))
            .andExpect(jsonPath("$.otConfirmDay").value(5));
        String body =
            "{\"advanceDays\":1,\"directorThresholdDays\":5,\"otMonthCap\":40,\"otYearCap\":200,\"carryExpiryMonth\":3,\"carryExpiryDay\":31,\"pastRequestDays\":30,\"otConfirmDay\":5}";
        mockMvc
            .perform(put("/api/leave/settings").with(HR).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(put("/api/leave/settings").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.directorThresholdDays").value(5));
        String badDate = body
            .replace("\"carryExpiryDay\":31", "\"carryExpiryDay\":31")
            .replace("\"carryExpiryMonth\":3", "\"carryExpiryMonth\":4");
        mockMvc
            .perform(put("/api/leave/settings").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(badDate))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leavesettinginvalid"));
    }

    @Test
    void expiryDateCannotBeMovedBeforeToday() throws Exception {
        String body =
            "{\"advanceDays\":1,\"directorThresholdDays\":3,\"otMonthCap\":40,\"otYearCap\":200,\"carryExpiryMonth\":6,\"carryExpiryDay\":30,\"pastRequestDays\":30,\"otConfirmDay\":5}";
        // 30/06/2026 đã qua so với hôm nay 22/10/2026.
        mockMvc
            .perform(put("/api/leave/settings").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leaveexpiryinpast"));
        mockMvc.perform(get("/api/leave/settings").with(ADMIN)).andExpect(jsonPath("$.carryExpiryMonth").value(3));
        // Hôm nay vẫn được.
        String today = body.replace("\"carryExpiryMonth\":6", "\"carryExpiryMonth\":10").replace("\"carryExpiryDay\":30", "\"carryExpiryDay\":22");
        mockMvc
            .perform(put("/api/leave/settings").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(today))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.carryExpiryMonth").value(10))
            .andExpect(jsonPath("$.carryExpiryDay").value(22));
    }

    @Test
    void previousYearBucketAdjustmentRejectedAfterExpiry() throws Exception {
        String prev =
            "{\"employeeId\":" + me.getId() + ",\"pool\":\"PHEP_NAM\",\"bucket\":\"NAM_TRUOC\",\"amount\":2,\"note\":\"Bù phép năm trước\"}";
        mockMvc
            .perform(post("/api/leave/adjustments").with(HR).contentType(MediaType.APPLICATION_JSON).content(prev))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leavebucketexpired"));
        String comp = prev.replace("PHEP_NAM", "NGHI_BU");
        mockMvc
            .perform(post("/api/leave/adjustments").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(comp))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.leavebucketexpired"));
        assertThat(ledger.entries(me.getId())).hasSize(2);
        String current = prev.replace("NAM_TRUOC", "NAM_NAY");
        mockMvc
            .perform(post("/api/leave/adjustments").with(HR).contentType(MediaType.APPLICATION_JSON).content(current))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].bucket").value("NAM_NAY"));
    }

    @Test
    void departmentHeadMustBeActiveEmployee() throws Exception {
        Employee left = LeaveFixtures.employee(employeeRepository, "NV9604", PersonType.NHAN_VIEN, LocalDate.of(2020, 1, 6));
        left.setStatus(EmploymentStatus.DA_NGHI);
        left.setLastWorkingDay(LocalDate.of(2026, 6, 30));
        employeeRepository.saveAndFlush(left);
        String body = "{\"id\":" + tech.getId() + ",\"name\":\"Kỹ thuật thử phép\",\"active\":true,\"headId\":" + left.getId() + "}";
        mockMvc
            .perform(put("/api/departments/{id}", tech.getId()).with(HR).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.departmentheadinactive"));

        Employee tt = LeaveFixtures.employee(employeeRepository, "TT9602", PersonType.THUC_TAP, LocalDate.of(2026, 1, 6));
        String intern = "{\"id\":" + tech.getId() + ",\"name\":\"Kỹ thuật thử phép\",\"active\":true,\"headId\":" + tt.getId() + "}";
        mockMvc
            .perform(put("/api/departments/{id}", tech.getId()).with(HR).contentType(MediaType.APPLICATION_JSON).content(intern))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.departmentheadinactive"));

        String ok = "{\"id\":" + tech.getId() + ",\"name\":\"Kỹ thuật thử phép\",\"active\":true,\"headId\":" + other.getId() + "}";
        mockMvc
            .perform(put("/api/departments/{id}", tech.getId()).with(HR).contentType(MediaType.APPLICATION_JSON).content(ok))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headId").value(other.getId()))
            .andExpect(jsonPath("$.headName").value(other.getFullName()));
        mockMvc
            .perform(get("/api/departments/{id}", tech.getId()).with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headId").value(other.getId()));
    }

    @Test
    void employeeLeaveFieldsAreValidatedAndKept() throws Exception {
        String base =
            "{\"id\":" +
            other.getId() +
            ",\"code\":\"NV9603\",\"personType\":\"NHAN_VIEN\",\"fullName\":\"" +
            other.getFullName() +
            "\",\"dependants\":0,\"joinDate\":\"2025-02-03\"";
        mockMvc
            .perform(
                put("/api/employees/{id}", other.getId())
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(base + ",\"annualLeaveDays\":13}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.annualleavedaysinvalid"));
        mockMvc
            .perform(
                put("/api/employees/{id}", other.getId())
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(base + ",\"annualLeaveDays\":14,\"priorSocialInsuranceYears\":7}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.annualLeaveDays").value(14))
            .andExpect(jsonPath("$.priorSocialInsuranceYears").value(7));
        // Bỏ trống thì giữ nguyên.
        mockMvc
            .perform(
                put("/api/employees/{id}", other.getId())
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(base + "}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.annualLeaveDays").value(14))
            .andExpect(jsonPath("$.priorSocialInsuranceYears").value(7));
        mockMvc
            .perform(
                put("/api/employees/{id}", other.getId())
                    .with(HR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(base + ",\"priorSocialInsuranceYears\":-1}")
            )
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ tab "Nghỉ phép" trên hồ sơ nhân viên

    @Autowired
    private LeaveRequestRepository requestRepository;

    private void privateRequest() {
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(me);
        r.setType(LeaveRequestType.PHEP_NAM);
        r.setFromDate(LocalDate.of(2026, 11, 2));
        r.setToDate(LocalDate.of(2026, 11, 6));
        r.setFirstPart(DayPart.CA_NGAY);
        r.setLastPart(DayPart.CA_NGAY);
        r.setDays(new BigDecimal("5"));
        r.setReasonText("LY DO RIENG TU");
        r.setStatus(LeaveRequestStatus.CHO_DUYET);
        r.setApproverKind(ApproverKind.BGD);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("nv-leave");
        requestRepository.saveAndFlush(r);
    }

    private ResultActions overview(Employee e, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(get("/api/leave/employees/{id}", e.getId()).with(who));
    }

    @Test
    void employeeTabForHrAndAdmin() throws Exception {
        privateRequest();
        for (RequestPostProcessor who : new RequestPostProcessor[] { HR, ADMIN }) {
            overview(me, who)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.annual.total").value(4.75))
                .andExpect(jsonPath("$.balance.compensatory.total").value(6.0))
                .andExpect(jsonPath("$.ledger.length()").value(2))
                .andExpect(jsonPath("$.requests.length()").value(1))
                .andExpect(jsonPath("$.requests[0].reasonText").value("LY DO RIENG TU"));
        }
    }

    @Test
    void employeeTabForDepartmentHeadAndTeamLeadWithoutReason() throws Exception {
        privateRequest();
        overview(me, as("truongphong-leave"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requests[0].reasonText").doesNotExist());
        Employee lead = LeaveFixtures.employee(employeeRepository, "NV9604", PersonType.NHAN_VIEN, LocalDate.of(2020, 1, 6));
        withLogin(lead, "truongnhom-leave");
        me.setManager(lead);
        employeeRepository.saveAndFlush(me);
        overview(me, as("truongnhom-leave")).andExpect(status().isOk());
    }

    @Test
    void employeeTabHiddenFromOthers() throws Exception {
        overview(me, BGD).andExpect(status().isForbidden());
        overview(me, as("nv-leave")).andExpect(status().isForbidden());
        overview(other, as("truongphong-leave")).andExpect(status().isForbidden());
        // Trưởng phòng đã nghỉ việc không còn quyền theo quan hệ (R25).
        head.setStatus(EmploymentStatus.DA_NGHI);
        employeeRepository.saveAndFlush(head);
        overview(me, as("truongphong-leave")).andExpect(status().isForbidden());
    }

    @Test
    void employeeTabUnknownIdIs403ForNonStaff() throws Exception {
        // Kiểm tra quyền trước "không tìm thấy": không dò được id nào tồn tại.
        mockMvc.perform(get("/api/leave/employees/{id}", 987654321L).with(as("truongphong-leave"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leave/employees/{id}", 987654321L).with(BGD)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leave/employees/{id}", 987654321L).with(HR)).andExpect(status().isBadRequest());
    }

    @Test
    void employeeTabNotForInterns() throws Exception {
        Employee intern = LeaveFixtures.employee(employeeRepository, "TT9601", PersonType.THUC_TAP, LocalDate.of(2026, 1, 5));
        overview(intern, HR).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("error.leavenotapplicable"));
    }
}

package vn.doit.hrm.web.rest;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.DayPart;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.Holiday;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.HolidayRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;

/** Lịch nghỉ tháng 10/2026 theo quyền xem. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class LeaveCalendarResourceIT {

    private static final RequestPostProcessor HR = user("hr-cal").authorities(() -> "ROLE_HR");
    private static final RequestPostProcessor BGD = user("bgd-cal").authorities(() -> "ROLE_BGD");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveRequestRepository requestRepository;

    @Autowired
    private WorkAssignmentRepository assignmentRepository;

    @Autowired
    private TerminationRecordRepository terminationRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    private Department sales;
    private Department tech;
    private Employee salesHead; // trưởng phòng Kinh doanh
    private Employee lead; // trưởng nhóm của a và b
    private Employee a;
    private Employee b;
    private Employee c; // phòng Kỹ thuật, không liên quan
    private Employee accountant;
    private Employee intern;

    private static RequestPostProcessor as(String login, String role) {
        return user(login).authorities(() -> role);
    }

    private Employee person(String code, Department dept, String login) {
        Employee e = LeaveFixtures.employee(employeeRepository, code, PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        e.setDepartment(dept);
        if (login != null) {
            e.setUser(LeaveFixtures.user(userRepository, login));
        }
        return employeeRepository.saveAndFlush(e);
    }

    private LeaveRequest request(Employee e, LeaveRequestType type, LeaveRequestStatus st, String from, String to) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(e);
        r.setType(type);
        r.setFromDate(LocalDate.parse(from));
        r.setToDate(LocalDate.parse(to));
        r.setFirstPart(DayPart.CA_NGAY);
        r.setLastPart(DayPart.CHIEU);
        r.setDays(BigDecimal.ONE);
        r.setReasonText("LY DO RIENG TU");
        r.setStatus(st);
        r.setApproverKind(ApproverKind.BGD);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        return requestRepository.saveAndFlush(r);
    }

    @BeforeEach
    void init() {
        sales = LeaveFixtures.department(departmentRepository, "Kinh doanh lịch");
        tech = LeaveFixtures.department(departmentRepository, "Kỹ thuật lịch");
        salesHead = person("NV9701", sales, "truongphong-cal");
        sales.setHead(salesHead);
        departmentRepository.saveAndFlush(sales);
        lead = person("NV9702", tech, "truongnhom-cal");
        a = person("NV9703", sales, "a-cal");
        b = person("NV9704", tech, "b-cal");
        a.setManager(lead);
        b.setManager(lead);
        employeeRepository.saveAllAndFlush(java.util.List.of(a, b));
        c = person("NV9705", tech, "c-cal");
        accountant = person("NV9706", tech, "ketoan-cal");
        intern = LeaveFixtures.employee(employeeRepository, "TT9701", PersonType.THUC_TAP, LocalDate.of(2026, 1, 5));
        intern.setDepartment(sales);
        employeeRepository.saveAndFlush(intern);

        request(a, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_DUYET, "2026-10-05", "2026-10-06");
        request(b, LeaveRequestType.OM, LeaveRequestStatus.CHO_DUYET, "2026-10-12", "2026-10-12");
        request(c, LeaveRequestType.KHONG_LUONG, LeaveRequestStatus.CHO_HUY, "2026-10-13", "2026-10-13");
        request(c, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.TU_CHOI, "2026-10-14", "2026-10-14");
        request(c, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_RUT, "2026-10-15", "2026-10-15");
        request(c, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_HUY, "2026-10-16", "2026-10-16");
        request(a, LeaveRequestType.NGHI_BU, LeaveRequestStatus.DA_DUYET, "2026-09-30", "2026-10-01"); // bắc từ tháng trước
        request(a, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_DUYET, "2026-11-02", "2026-11-02"); // tháng sau
    }

    private ResultActions calendar(RequestPostProcessor who, String query) throws Exception {
        return mockMvc.perform(get("/api/leave/calendar?month=2026-10" + query).with(who));
    }

    private static final String NAMES = "$.items[*].employeeName";

    private String n(Employee e) {
        return e.getFullName();
    }

    @Test
    void employeeSeesOnlyOwn() throws Exception {
        calendar(as("a-cal", "ROLE_NHAN_VIEN"), "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath(NAMES, containsInAnyOrder(n(a), n(a))))
            .andExpect(jsonPath("$.days", hasSize(31)));
    }

    @Test
    void teamLeadSeesTeamOnly() throws Exception {
        calendar(as("truongnhom-cal", "ROLE_NHAN_VIEN"), "")
            .andExpect(jsonPath(NAMES, containsInAnyOrder(n(a), n(a), n(b))));
    }

    @Test
    void departmentHeadSeesWholeDepartment() throws Exception {
        calendar(as("truongphong-cal", "ROLE_NHAN_VIEN"), "")
            .andExpect(jsonPath(NAMES, containsInAnyOrder(n(a), n(a))));
    }

    @Test
    void bothTeamLeadAndDepartmentHeadSeeUnion() throws Exception {
        sales.setHead(lead);
        departmentRepository.saveAndFlush(sales);
        // lead: nhóm {a, b} + phòng Kinh doanh {salesHead, a, lead không thuộc}; không có c
        request(salesHead, LeaveRequestType.VIEC_RIENG, LeaveRequestStatus.DA_DUYET, "2026-10-20", "2026-10-20");
        calendar(as("truongnhom-cal", "ROLE_NHAN_VIEN"), "")
            .andExpect(jsonPath(NAMES, containsInAnyOrder(n(a), n(a), n(b), n(salesHead))));
    }

    @Test
    void bgdAndHrSeeEveryone() throws Exception {
        for (RequestPostProcessor who : new RequestPostProcessor[] { BGD, HR }) {
            calendar(who, "")
                .andExpect(jsonPath(NAMES, containsInAnyOrder(n(a), n(a), n(b), n(c))));
        }
    }

    @Test
    void accountantSeesOnlyOwn() throws Exception {
        request(accountant, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_DUYET, "2026-10-21", "2026-10-21");
        calendar(as("ketoan-cal", "ROLE_KE_TOAN"), "")
            .andExpect(jsonPath(NAMES, containsInAnyOrder(n(accountant))));
    }

    @Test
    void pendingFlagAndHiddenStatuses() throws Exception {
        calendar(BGD, "&type=OM").andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].pending").value(true));
        calendar(BGD, "&type=KHONG_LUONG")
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].pending").value(false));
        calendar(BGD, "&type=PHEP_NAM")
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].from").value("2026-10-05"));
    }

    @Test
    void noReasonOrNotesInJson() throws Exception {
        String body = calendar(HR, "").andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
            .doesNotContain("LY DO RIENG TU")
            .doesNotContainIgnoringCase("reason")
            .doesNotContainIgnoringCase("sick")
            .doesNotContainIgnoringCase("note");
    }

    @Test
    void businessTripFromAssignmentHistory() throws Exception {
        WorkAssignment w = new WorkAssignment();
        w.setEmployee(c);
        w.setType(WorkLocationType.CONG_TAC);
        w.setFromDate(LocalDate.of(2026, 10, 26));
        w.setToDate(LocalDate.of(2026, 11, 3));
        assignmentRepository.saveAndFlush(w);
        WorkAssignment office = new WorkAssignment();
        office.setEmployee(c);
        office.setType(WorkLocationType.VAN_PHONG);
        office.setFromDate(LocalDate.of(2026, 10, 1));
        assignmentRepository.saveAndFlush(office);
        calendar(HR, "&type=CONG_TAC")
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].employeeId").value(c.getId()))
            .andExpect(jsonPath("$.items[0].type").value("CONG_TAC"))
            .andExpect(jsonPath("$.items[0].from").value("2026-10-26"));
    }

    @Test
    void scheduledTerminationShownFromNextDay() throws Exception {
        TerminationRecord t = new TerminationRecord();
        t.setEmployeeId(b.getId());
        t.setLastWorkingDay(LocalDate.of(2026, 10, 28));
        t.setTerminationDate(LocalDate.of(2026, 10, 29));
        t.setTerminationType(TerminationType.TU_NGHI);
        t.setStatus(TerminationRecordStatus.SCHEDULED);
        terminationRepository.saveAndFlush(t);
        calendar(HR, "&type=NGHI_VIEC")
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].employeeId").value(b.getId()))
            .andExpect(jsonPath("$.items[0].from").value("2026-10-29"))
            .andExpect(jsonPath("$.items[0].to").value("2026-10-31"));
    }

    @Test
    void holidayHasName() throws Exception {
        Holiday h = new Holiday();
        h.setDay(LocalDate.of(2026, 10, 27));
        h.setName("Lễ thử lịch");
        if (!holidayRepository.existsByDay(h.getDay())) {
            holidayRepository.saveAndFlush(h);
        }
        calendar(HR, "")
            .andExpect(jsonPath("$.days[26].holidayName").value(org.hamcrest.Matchers.notNullValue()))
            .andExpect(jsonPath("$.days[0].holidayName").doesNotExist())
            .andExpect(jsonPath("$.days[3].weekend").value(true)); // 04/10/2026 là Chủ nhật
    }

    @Test
    void departmentFilterOutsideVisibleSetReturnsIntersection() throws Exception {
        // trưởng nhóm thấy {a (Kinh doanh), b (Kỹ thuật)}; lọc theo Kỹ thuật → chỉ b; lọc theo phòng khác → rỗng
        calendar(as("truongnhom-cal", "ROLE_NHAN_VIEN"), "&departmentId=" + tech.getId())
            .andExpect(jsonPath(NAMES, containsInAnyOrder(n(b))));
        Department other = LeaveFixtures.department(departmentRepository, "Phòng khác lịch");
        calendar(as("truongnhom-cal", "ROLE_NHAN_VIEN"), "&departmentId=" + other.getId()).andExpect(jsonPath("$.items", empty()));
        // nhân viên lọc phòng Kỹ thuật (không phải phòng mình) → rỗng
        calendar(as("a-cal", "ROLE_NHAN_VIEN"), "&departmentId=" + tech.getId()).andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void internNeverAppears() throws Exception {
        request(intern, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_DUYET, "2026-10-08", "2026-10-08");
        calendar(HR, "").andExpect(jsonPath(NAMES, org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(n(intern)))));
    }

    @Test
    void badMonthIs400() throws Exception {
        mockMvc.perform(get("/api/leave/calendar?month=abc").with(HR)).andExpect(status().isBadRequest());
    }

    private void leave(Employee e, String lastDay) {
        e.setStatus(vn.doit.hrm.domain.enumeration.EmploymentStatus.DA_NGHI);
        e.setLastWorkingDay(LocalDate.parse(lastDay));
        employeeRepository.saveAndFlush(e);
    }

    private ResultActions calendarOf(RequestPostProcessor who, String month, String query) throws Exception {
        return mockMvc.perform(get("/api/leave/calendar?month=" + month + query).with(who));
    }

    @Test
    void leaverShownOnlyInLeavingMonth() throws Exception {
        TerminationRecord t = new TerminationRecord();
        t.setEmployeeId(b.getId());
        t.setLastWorkingDay(LocalDate.of(2026, 10, 28));
        t.setTerminationDate(LocalDate.of(2026, 10, 29));
        t.setTerminationType(TerminationType.TU_NGHI);
        t.setStatus(TerminationRecordStatus.APPLIED);
        terminationRepository.saveAndFlush(t);
        leave(b, "2026-10-28");
        calendarOf(HR, "2026-10", "&type=NGHI_VIEC").andExpect(jsonPath("$.items", hasSize(1)));
        calendarOf(HR, "2026-11", "&type=NGHI_VIEC").andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void leaverOldLeaveInLaterMonthNotShown() throws Exception {
        request(b, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_DUYET, "2026-11-10", "2026-11-10");
        leave(b, "2026-10-28");
        calendarOf(HR, "2026-11", "").andExpect(jsonPath(NAMES, org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(n(b)))));
        calendarOf(HR, "2026-10", "").andExpect(jsonPath(NAMES, org.hamcrest.Matchers.hasItem(n(b))));
    }

    @Test
    void terminatedTeamLeadSeesNothing() throws Exception {
        leave(lead, "2026-09-30");
        calendar(as("truongnhom-cal", "ROLE_NHAN_VIEN"), "").andExpect(jsonPath("$.items", empty()));
    }
}

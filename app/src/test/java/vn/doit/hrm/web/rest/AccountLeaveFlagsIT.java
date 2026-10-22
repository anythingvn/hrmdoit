package vn.doit.hrm.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.DayPart;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;

/** Cờ {@code leaveApplicable} và {@code isApprover} trên {@code GET /api/account} (hiện mục "Nghỉ phép", "Duyệt đơn"). */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AccountLeaveFlagsIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveRequestRepository leaveRepository;

    @Autowired
    private OvertimeRequestRepository overtimeRepository;

    private Employee person(String code, PersonType type, String login) {
        Employee e = LeaveFixtures.employee(employeeRepository, code, type, LocalDate.of(2024, 1, 2));
        e.setUser(LeaveFixtures.user(userRepository, login));
        return employeeRepository.saveAndFlush(e);
    }

    private ResultActions account(String login, String role) throws Exception {
        return mockMvc.perform(get("/api/account").with(user(login).authorities(() -> role))).andExpect(status().isOk());
    }

    @Test
    void nhanVienKhongQuanHeThiNghiPhepNhungKhongDuyet() throws Exception {
        person("NV9801", PersonType.NHAN_VIEN, "flag-nv");
        account("flag-nv", "ROLE_NHAN_VIEN")
            .andExpect(jsonPath("$.leaveApplicable").value(true))
            .andExpect(jsonPath("$.isApprover").value(false))
            .andExpect(jsonPath("$.seesLeaveCalendar").value(false));
    }

    @Test
    void thucTapVaDichVuKhongNghiPhep() throws Exception {
        person("TT9801", PersonType.THUC_TAP, "flag-tt");
        person("DV9801", PersonType.DICH_VU, "flag-dv");
        account("flag-tt", "ROLE_NHAN_VIEN").andExpect(jsonPath("$.leaveApplicable").value(false));
        account("flag-dv", "ROLE_NHAN_VIEN").andExpect(jsonPath("$.leaveApplicable").value(false));
    }

    @Test
    void daNghiViecKhongNghiPhepKhongDuyet() throws Exception {
        Employee lead = person("NV9802", PersonType.NHAN_VIEN, "flag-left");
        Employee sub = person("NV9803", PersonType.NHAN_VIEN, "flag-sub0");
        sub.setManager(lead);
        lead.setStatus(EmploymentStatus.DA_NGHI);
        employeeRepository.saveAllAndFlush(java.util.List.of(lead, sub));
        account("flag-left", "ROLE_NHAN_VIEN")
            .andExpect(jsonPath("$.leaveApplicable").value(false))
            .andExpect(jsonPath("$.isApprover").value(false))
            .andExpect(jsonPath("$.seesLeaveCalendar").value(false));
    }

    @Test
    void truongNhomLaNguoiDuyet() throws Exception {
        Employee lead = person("NV9804", PersonType.NHAN_VIEN, "flag-lead");
        Employee sub = person("NV9805", PersonType.NHAN_VIEN, "flag-sub");
        sub.setManager(lead);
        employeeRepository.saveAndFlush(sub);
        account("flag-lead", "ROLE_NHAN_VIEN").andExpect(jsonPath("$.isApprover").value(true)).andExpect(jsonPath("$.seesLeaveCalendar").value(true));
        account("flag-sub", "ROLE_NHAN_VIEN").andExpect(jsonPath("$.isApprover").value(false)).andExpect(jsonPath("$.seesLeaveCalendar").value(false));
    }

    @Test
    void truongPhongLaNguoiDuyet() throws Exception {
        Employee head = person("NV9806", PersonType.NHAN_VIEN, "flag-head");
        Department d = LeaveFixtures.department(departmentRepository, "Phòng cờ");
        d.setHead(head);
        departmentRepository.saveAndFlush(d);
        account("flag-head", "ROLE_NHAN_VIEN").andExpect(jsonPath("$.isApprover").value(true)).andExpect(jsonPath("$.seesLeaveCalendar").value(true));
    }

    @Test
    void coDonNghiChoMinhDuyetLaNguoiDuyet() throws Exception {
        Employee approver = person("NV9807", PersonType.NHAN_VIEN, "flag-appr");
        Employee requester = person("NV9808", PersonType.NHAN_VIEN, "flag-req");
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(requester);
        r.setType(LeaveRequestType.PHEP_NAM);
        r.setFromDate(LocalDate.of(2026, 10, 26));
        r.setToDate(LocalDate.of(2026, 10, 26));
        r.setFirstPart(DayPart.CA_NGAY);
        r.setLastPart(DayPart.CA_NGAY);
        r.setDays(BigDecimal.ONE);
        r.setStatus(LeaveRequestStatus.CHO_DUYET);
        r.setApproverKind(ApproverKind.TRUONG_PHONG);
        r.setApproverEmployee(approver);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("flag-req");
        leaveRepository.saveAndFlush(r);
        // Chỉ là người duyệt của một đơn (vd đơn được chuyển lên): không phải trưởng nhóm hay trưởng phòng → không có "Lịch nghỉ".
        account("flag-appr", "ROLE_NHAN_VIEN").andExpect(jsonPath("$.isApprover").value(true)).andExpect(jsonPath("$.seesLeaveCalendar").value(false));
    }

    @Test
    void coDonLamThemChoMinhDuyetLaNguoiDuyet() throws Exception {
        Employee approver = person("NV9809", PersonType.NHAN_VIEN, "flag-appr-ot");
        Employee requester = person("NV9810", PersonType.NHAN_VIEN, "flag-req-ot");
        OvertimeRequest r = new OvertimeRequest();
        r.setEmployee(requester);
        r.setWorkDate(LocalDate.of(2026, 10, 24));
        r.setStartTime(LocalTime.of(8, 0));
        r.setEndTime(LocalTime.of(10, 0));
        r.setPlannedHours(new BigDecimal("2"));
        r.setRate(200);
        r.setNight(false);
        r.setStatus(OvertimeStatus.CHO_DUYET);
        r.setApproverKind(ApproverKind.TRUONG_NHOM);
        r.setApproverEmployee(approver);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("flag-req-ot");
        overtimeRepository.saveAndFlush(r);
        account("flag-appr-ot", "ROLE_NHAN_VIEN").andExpect(jsonPath("$.isApprover").value(true));
    }

    @Test
    void hrBgdQuanTriLaNguoiDuyetKhongCanHoSo() throws Exception {
        LeaveFixtures.user(userRepository, "flag-hr");
        LeaveFixtures.user(userRepository, "flag-bgd");
        LeaveFixtures.user(userRepository, "flag-kt");
        account("flag-hr", "ROLE_HR").andExpect(jsonPath("$.isApprover").value(true)).andExpect(jsonPath("$.leaveApplicable").value(false));
        account("flag-bgd", "ROLE_BGD").andExpect(jsonPath("$.isApprover").value(true));
        account("flag-kt", "ROLE_KE_TOAN").andExpect(jsonPath("$.isApprover").value(false));
        account("flag-hr", "ROLE_HR").andExpect(jsonPath("$.seesLeaveCalendar").value(true));
        account("flag-bgd", "ROLE_BGD").andExpect(jsonPath("$.seesLeaveCalendar").value(true));
        account("flag-kt", "ROLE_KE_TOAN").andExpect(jsonPath("$.seesLeaveCalendar").value(false));
    }
}

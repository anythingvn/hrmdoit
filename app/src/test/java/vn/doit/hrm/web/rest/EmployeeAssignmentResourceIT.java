package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.repository.WorkSiteRepository;

/** Lịch sử vị trí làm việc (qua lõi AssignmentHistory) và cho nghỉ việc. Hôm nay cố định = 27/10/2026. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "ROLE_HR")
@Import(EmployeeAssignmentResourceIT.FixedClockConfig.class)
class EmployeeAssignmentResourceIT {

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private WorkAssignmentRepository workAssignmentRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private WorkSiteRepository workSiteRepository;

    private Employee employee;

    @BeforeEach
    void init() {
        employee = employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9200")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Phạm Thị Vị Trí")
                .searchText("nv9200 pham thi vi tri")
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
        );
    }

    private String url(String suffix) {
        return "/api/employees/" + employee.getId() + suffix;
    }

    private static String json(String type, String unit, String from, String to) {
        return (
            "{\"type\":\"" +
            type +
            "\"" +
            (unit == null ? "" : ",\"unit\":\"" + unit + "\"") +
            ",\"fromDate\":\"" +
            from +
            "\"" +
            (to == null ? "" : ",\"toDate\":\"" + to + "\"") +
            "}"
        );
    }

    private ResultActions add(String body) throws Exception {
        return mockMvc.perform(post(url("/assignments")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<WorkAssignment> stored() {
        return workAssignmentRepository
            .findByEmployeeId(employee.getId())
            .stream()
            .sorted(Comparator.comparing(WorkAssignment::getFromDate))
            .toList();
    }

    @Test
    void onsiteWithEndCreatesReturn() throws Exception {
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isCreated());
        add(json("KHACH_HANG", "LP Bank", "2026-11-01", "2027-01-31"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.unit").value("LP Bank"))
            .andExpect(jsonPath("$.id").isNumber());

        mockMvc
            .perform(get(url("/assignments/timeline")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].from").value("2026-01-01"))
            .andExpect(jsonPath("$[0].to").value("2026-10-31"))
            .andExpect(jsonPath("$[0].type").value("VAN_PHONG"))
            .andExpect(jsonPath("$[1].from").value("2026-11-01"))
            .andExpect(jsonPath("$[1].to").value("2027-01-31"))
            .andExpect(jsonPath("$[1].type").value("KHACH_HANG"))
            .andExpect(jsonPath("$[1].unit").value("LP Bank"))
            .andExpect(jsonPath("$[1].label").value("Làm việc tại khách hàng/dự án – LP Bank"))
            .andExpect(jsonPath("$[2].from").value("2027-02-01"))
            .andExpect(jsonPath("$[2].type").value("VAN_PHONG"))
            .andExpect(jsonPath("$[2].label").value("Văn phòng công ty"));

        List<WorkAssignment> rows = stored();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getToDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(rows.get(2).getType()).isEqualTo(WorkLocationType.VAN_PHONG);
        assertThat(rows.get(2).getToDate()).isNull();
    }

    @Test
    void endEarlyPullsReturnForward() throws Exception {
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isCreated());
        String created = add(json("KHACH_HANG", "LP Bank", "2026-11-01", "2027-01-31"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long onsiteId = om.readTree(created).get("id").asLong();

        mockMvc
            .perform(
                patch(url("/assignments/" + onsiteId + "/end"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"toDate\":\"2026-12-20\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toDate").value("2026-12-20"));

        List<WorkAssignment> rows = stored();
        assertThat(rows.get(2).getFromDate()).isEqualTo(LocalDate.of(2026, 12, 21));
        mockMvc
            .perform(get(url("/assignments/gaps")).param("from", "2026-01-01").param("to", "2027-12-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void gapsReported() throws Exception {
        add(json("VAN_PHONG", null, "2026-03-01", null)).andExpect(status().isCreated());
        mockMvc
            .perform(get(url("/assignments/gaps")).param("from", "2026-01-01").param("to", "2026-12-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].from").value("2026-01-01"))
            .andExpect(jsonPath("$[0].to").value("2026-02-28"));
    }

    @Test
    void overlapRejected() throws Exception {
        add(json("CONG_TAC", "Đà Nẵng", "2026-12-10", "2026-12-12")).andExpect(status().isCreated());
        add(json("CONG_TAC", "Huế", "2026-12-12", "2026-12-14"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(not(emptyOrNullString())))
            .andExpect(jsonPath("$.detail").value(containsString("Trùng thời gian")));
        assertThat(stored()).hasSize(1);
    }

    @Test
    void businessTripWithoutEndRejected() throws Exception {
        add(json("CONG_TAC", "Huế", "2026-12-12", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("phải có ngày về")));
        assertThat(stored()).isEmpty();
    }

    @Test
    void currentReturnsTripDuringTrip() throws Exception {
        mockMvc.perform(get(url("/assignments/current"))).andExpect(status().isNoContent());
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isCreated());
        add(json("CONG_TAC", "Đà Nẵng", "2026-10-26", "2026-10-30")).andExpect(status().isCreated());
        mockMvc
            .perform(get(url("/assignments/current")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("CONG_TAC"))
            .andExpect(jsonPath("$.unit").value("Đà Nẵng"));
    }

    @Test
    void siteFillsTypeUnitAddress() throws Exception {
        WorkSite site = workSiteRepository.saveAndFlush(
            new WorkSite().name("LP Bank Hội sở").type(WorkLocationType.KHACH_HANG).address("210 Trần Quang Khải, Hà Nội").active(true)
        );
        add("{\"fromDate\":\"2026-11-01\",\"site\":{\"id\":" + site.getId() + "}}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("KHACH_HANG"))
            .andExpect(jsonPath("$.unit").value("LP Bank Hội sở"))
            .andExpect(jsonPath("$.address").value("210 Trần Quang Khải, Hà Nội"));
    }

    @Test
    void newCustomerSavedToCatalogAndReused() throws Exception {
        String body = add(
            "{\"type\":\"KHACH_HANG\",\"unit\":\"  Ngân hàng   Thử Nghiệm \",\"address\":\"1 Láng Hạ\",\"fromDate\":\"2026-11-01\",\"toDate\":\"2026-11-10\"}"
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.unit").value("Ngân hàng Thử Nghiệm"))
            .andExpect(jsonPath("$.site.id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long siteId = om.readTree(body).get("site").get("id").asLong();
        WorkSite saved = workSiteRepository.findById(siteId).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Ngân hàng Thử Nghiệm");
        assertThat(saved.getType()).isEqualTo(WorkLocationType.KHACH_HANG);
        assertThat(saved.getAddress()).isEqualTo("1 Láng Hạ");
        assertThat(saved.getActive()).isTrue();

        // Cùng tên (khác hoa thường): dùng lại, không tạo bản trùng; nơi đang ngưng dùng được bật lại.
        saved.setActive(false);
        workSiteRepository.saveAndFlush(saved);
        add(json("KHACH_HANG", "ngân HÀNG thử nghiệm", "2026-12-01", "2026-12-10"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.site.id").value(siteId))
            .andExpect(jsonPath("$.unit").value("Ngân hàng Thử Nghiệm"));
        assertThat(workSiteRepository.findAll().stream().filter(w -> w.getName().equalsIgnoreCase("Ngân hàng Thử Nghiệm")).count()).isEqualTo(1);
        assertThat(workSiteRepository.findById(siteId).orElseThrow().getActive()).isTrue();
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantCannotAdd() throws Exception {
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isForbidden());
        mockMvc
            .perform(patch(url("/assignments/1/end")).contentType(MediaType.APPLICATION_JSON).content("{\"toDate\":\"2026-12-20\"}"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content("{\"lastWorkingDay\":\"2026-10-31\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(url("/assignments/timeline"))).andExpect(status().isOk());
        mockMvc.perform(get(url("/assignments/current"))).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "ROLE_BGD")
    void bgdCanReadTimeline() throws Exception {
        mockMvc.perform(get(url("/assignments/timeline"))).andExpect(status().isOk());
        mockMvc.perform(get(url("/assignments/gaps")).param("from", "2026-01-01").param("to", "2026-01-31")).andExpect(status().isOk());
    }

    @Test
    void genericWriteEndpointsRemoved() throws Exception {
        mockMvc
            .perform(post("/api/work-assignments").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isMethodNotAllowed());
    }

    // ------------------------------------------------------------------ xóa giai đoạn (sửa lịch sử)

    private long addAndGetId(String body) throws Exception {
        String created = add(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return om.readTree(created).get("id").asLong();
    }

    @Test
    void timelineIncludesAssignmentId() throws Exception {
        long id = addAndGetId(json("VAN_PHONG", null, "2026-01-01", null));
        mockMvc.perform(get(url("/assignments/timeline"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void deleteOwnPeriod() throws Exception {
        addAndGetId(json("VAN_PHONG", null, "2026-01-01", null));
        long tripId = addAndGetId(json("CONG_TAC", "Đà Nẵng", "2026-12-10", "2026-12-12"));
        mockMvc.perform(delete(url("/assignments/" + tripId))).andExpect(status().isNoContent());
        assertThat(workAssignmentRepository.findById(tripId)).isEmpty();
        assertThat(stored()).hasSize(1);
    }

    @Test
    void deleteMiddlePrimaryLeavesGap() throws Exception {
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isCreated());
        add(json("KHACH_HANG", "LP Bank", "2026-03-01", "2026-05-31")).andExpect(status().isCreated());
        long middle = stored().get(1).getId();
        mockMvc.perform(delete(url("/assignments/" + middle))).andExpect(status().isNoContent());
        mockMvc
            .perform(get(url("/assignments/gaps")).param("from", "2026-01-01").param("to", "2026-10-27"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].from").value("2026-03-01"))
            .andExpect(jsonPath("$[0].to").value("2026-05-31"));
    }

    @Test
    void deleteOtherEmployeesPeriodRejected() throws Exception {
        Employee other = employeeRepository.saveAndFlush(
            new Employee()
                .code("NV9201")
                .personType(PersonType.NHAN_VIEN)
                .fullName("Người Khác")
                .searchText("nv9201 nguoi khac")
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
        );
        WorkAssignment foreign = workAssignmentRepository.saveAndFlush(
            new WorkAssignment().type(WorkLocationType.VAN_PHONG).fromDate(LocalDate.of(2026, 1, 1)).employee(other)
        );
        mockMvc
            .perform(delete(url("/assignments/" + foreign.getId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.assignmentnotfound"));
        assertThat(workAssignmentRepository.findById(foreign.getId())).isPresent();
    }

    @Test
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantCannotDelete() throws Exception {
        WorkAssignment row = workAssignmentRepository.saveAndFlush(
            new WorkAssignment().type(WorkLocationType.VAN_PHONG).fromDate(LocalDate.of(2026, 1, 1)).employee(employee)
        );
        mockMvc.perform(delete(url("/assignments/" + row.getId()))).andExpect(status().isForbidden());
        assertThat(workAssignmentRepository.findById(row.getId())).isPresent();
    }

    @Test
    void terminateSucceedsAfterDeletingBlockingPeriod() throws Exception {
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isCreated());
        long blocking = addAndGetId(json("KHACH_HANG", "LP Bank", "2026-10-01", null));
        String early = TERMINATE.replace("2026-10-26", "2026-09-15");
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(early))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.terminatebeforeassignment"));
        mockMvc.perform(delete(url("/assignments/" + blocking))).andExpect(status().isNoContent());
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(early))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_NGHI"));
        List<WorkAssignment> rows = stored();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getToDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    // ------------------------------------------------------------------ cho nghỉ việc

    private static final String TERMINATE =
        "{\"lastWorkingDay\":\"2026-10-26\",\"terminationType\":\"TU_NGHI\",\"terminationReason\":\"Chuyển việc\"}";

    @Test
    void terminateClosesOpenRecords() throws Exception {
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isCreated());
        EmploymentContract contract = contractRepository.saveAndFlush(
            new EmploymentContract()
                .type(ContractType.HDLD_XDTH)
                .termMonths(12)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .salary(20000000L)
                .salaryBasis(SalaryBasis.GROSS)
                .status(ContractStatus.DA_KY)
                .employee(employee)
        );

        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_NGHI"))
            .andExpect(jsonPath("$.lastWorkingDay").value("2026-10-26"))
            .andExpect(jsonPath("$.terminationType").value("TU_NGHI"))
            .andExpect(jsonPath("$.currentSalary").doesNotExist());

        assertThat(stored().get(0).getToDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        EmploymentContract c = contractRepository.findById(contract.getId()).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(ContractStatus.DA_KET_THUC);
        assertThat(c.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        mockMvc
            .perform(get(url("")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_NGHI"))
            .andExpect(jsonPath("$.currentSalary").doesNotExist());
    }

    @Test
    void terminateBlockedByFuturePlansDeletesNothing() throws Exception {
        // Hôm nay 27/10/2026; nghỉ 26/10/2026 giữa đợt onsite có kế hoạch quay về văn phòng 01/02/2027 và một đợt công tác tương lai:
        // cho nghỉ việc không còn xóa kế hoạch – bị chặn, HR tự xóa (có nhật ký) nếu đúng là bỏ.
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isCreated());
        add(json("KHACH_HANG", "LP Bank", "2026-10-01", "2027-01-31")).andExpect(status().isCreated());
        add(json("CONG_TAC", "Đà Nẵng", "2026-11-10", "2026-11-12")).andExpect(status().isCreated());
        List<String> before = stored().stream().map(w -> w.getId() + " " + w.getFromDate() + " " + w.getToDate()).toList();
        assertThat(before).hasSize(4);
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.terminatebeforeassignment"));
        assertThat(stored().stream().map(w -> w.getId() + " " + w.getFromDate() + " " + w.getToDate()).toList()).isEqualTo(before);
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
    }

    @Test
    void terminateBeforeStartedAssignmentRejected() throws Exception {
        // Gõ nhầm ngày làm việc cuối (15/09/2026) trước giai đoạn onsite đã bắt đầu 01/10/2026 → từ chối, không đổi gì.
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isCreated());
        add(json("KHACH_HANG", "LP Bank", "2026-10-01", null)).andExpect(status().isCreated());
        List<WorkAssignment> before = stored();
        List<String> snapshot = before.stream().map(w -> w.getId() + " " + w.getFromDate() + " " + w.getToDate()).toList();

        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE.replace("2026-10-26", "2026-09-15")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.terminatebeforeassignment"));

        assertThat(stored().stream().map(w -> w.getId() + " " + w.getFromDate() + " " + w.getToDate()).toList()).isEqualTo(snapshot);
        Employee e = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat(e.getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
        assertThat(e.getLastWorkingDay()).isNull();
    }

    @Test
    void terminateBeforeContractStartRejected() throws Exception {
        add(json("VAN_PHONG", null, "2026-01-01", null)).andExpect(status().isCreated());
        EmploymentContract contract = contractRepository.saveAndFlush(
            new EmploymentContract()
                .type(ContractType.HDLD_KXDTH)
                .startDate(LocalDate.of(2026, 11, 15))
                .salary(20000000L)
                .salaryBasis(SalaryBasis.GROSS)
                .status(ContractStatus.DA_KY)
                .employee(employee)
        );
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.terminatebeforecontract"));
        EmploymentContract c = contractRepository.findById(contract.getId()).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(ContractStatus.DA_KY);
        assertThat(c.getEndDate()).isNull();
        assertThat(stored().get(0).getToDate()).isNull();
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
    }

    @Test
    void terminateTwiceRejected() throws Exception {
        mockMvc.perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE)).andExpect(status().isOk());
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.alreadyterminated"));
    }

    // ------------------------------------------------------------------ ngày nghỉ việc (Excel cột AQ)

    @Test
    void terminationDateDefaultsToDayAfterLastWorkingDay() throws Exception {
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lastWorkingDay").value("2026-10-26"))
            .andExpect(jsonPath("$.terminationDate").value("2026-10-27"));
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getTerminationDate()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    void explicitTerminationDateKept() throws Exception {
        String body = TERMINATE.replace("}", ",\"terminationDate\":\"2026-11-15\"}");
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.terminationDate").value("2026-11-15"));
    }

    @Test
    void terminationDateSameAsLastWorkingDayAllowed() throws Exception {
        String body = TERMINATE.replace("}", ",\"terminationDate\":\"2026-10-26\"}");
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.terminationDate").value("2026-10-26"));
    }

    @Test
    void terminationDateBeforeLastWorkingDayRejected() throws Exception {
        String body = TERMINATE.replace("}", ",\"terminationDate\":\"2026-10-25\"}");
        mockMvc
            .perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.terminationdatebeforelastday"));
        Employee e = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat(e.getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
        assertThat(e.getTerminationDate()).isNull();
    }

    @Test
    void putAndPatchKeepTerminationDate() throws Exception {
        mockMvc.perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE)).andExpect(status().isOk());
        ObjectNode body = currentEmployeeJson();
        body.put("terminationDate", "2030-01-01");
        mockMvc
            .perform(put(url("")).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.terminationDate").value("2026-10-27"));
        mockMvc
            .perform(
                patch(url(""))
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + employee.getId() + ",\"terminationDate\":null,\"note\":\"x\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.terminationDate").value("2026-10-27"));
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getTerminationDate()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    private ObjectNode currentEmployeeJson() throws Exception {
        String body = mockMvc.perform(get(url(""))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return (ObjectNode) om.readTree(body);
    }

    @Test
    void putCannotSetDaNghi() throws Exception {
        ObjectNode body = currentEmployeeJson();
        body.put("status", "DA_NGHI");
        mockMvc
            .perform(put(url("")).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.useterminate"));
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getStatus()).isEqualTo(EmploymentStatus.DANG_LAM_VIEC);
    }

    @Test
    void putWithoutStatusKeepsDaNghi() throws Exception {
        mockMvc.perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE)).andExpect(status().isOk());
        ObjectNode body = currentEmployeeJson();
        body.remove("status");
        body.put("note", "Sửa ghi chú sau khi nghỉ");
        mockMvc
            .perform(put(url("")).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_NGHI"));
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getStatus()).isEqualTo(EmploymentStatus.DA_NGHI);
    }

    @Test
    void patchCannotReactivate() throws Exception {
        mockMvc.perform(post(url("/terminate")).contentType(MediaType.APPLICATION_JSON).content(TERMINATE)).andExpect(status().isOk());
        mockMvc
            .perform(
                patch(url(""))
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + employee.getId() + ",\"status\":\"DANG_LAM_VIEC\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.useterminate"));
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getStatus()).isEqualTo(EmploymentStatus.DA_NGHI);
    }
}

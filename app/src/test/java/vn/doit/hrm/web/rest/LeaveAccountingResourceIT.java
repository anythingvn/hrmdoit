package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.DayPart;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.Holiday;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.LeaveRequestType;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.HolidayRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;
import vn.doit.hrm.service.leave.LeaveLedgerService;
import vn.doit.hrm.service.leave.LeaveTerminationHook;

/** Tổng hợp nghỉ và làm thêm tháng 10/2026 cho kế toán (trang và Excel). */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(LeaveAccountingResourceIT.FixedClockConfig.class)
class LeaveAccountingResourceIT {

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager em;

    /** Hôm nay 22/10/2026 (ngày ghi dòng sổ). */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private PayrollPeriodRepository periodRepository;

    private static final RequestPostProcessor KT = user("kt-acc").authorities(() -> "ROLE_KE_TOAN");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveRequestRepository requestRepository;

    @Autowired
    private OvertimeRequestRepository overtimeRepository;

    @Autowired
    private TerminationRecordRepository terminationRepository;

    @Autowired
    private LeaveLedgerService ledger;

    private Employee a;

    @BeforeEach
    void init() {
        a = LeaveFixtures.employee(employeeRepository, "AC7001", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        leave(a, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_DUYET, "2026-10-12", "2026-10-13", DayPart.CA_NGAY, DayPart.CA_NGAY, "2");
        leave(a, LeaveRequestType.KHONG_LUONG, LeaveRequestStatus.DA_DUYET, "2026-10-14", "2026-10-14", DayPart.CA_NGAY, DayPart.CA_NGAY);
        // Không tính: đơn bị từ chối, đơn đang chờ.
        leave(a, LeaveRequestType.OM, LeaveRequestStatus.TU_CHOI, "2026-10-19", "2026-10-19", DayPart.CA_NGAY, DayPart.CA_NGAY);
        leave(a, LeaveRequestType.OM, LeaveRequestStatus.CHO_DUYET, "2026-10-20", "2026-10-20", DayPart.CA_NGAY, DayPart.CA_NGAY);
        overtime(a, "2026-10-15", 150, false, OvertimeStatus.HOAN_TAT, OvertimeCompensation.TRA_TIEN, "3");
        overtime(a, "2026-10-17", 200, false, OvertimeStatus.HOAN_TAT, OvertimeCompensation.NGHI_BU, "2");
        // Chưa hoàn tất: không vào tổng hợp.
        overtime(a, "2026-10-16", 150, false, OvertimeStatus.DA_DUYET, OvertimeCompensation.TRA_TIEN, "4");
    }

    private ResultActions summary(RequestPostProcessor who, String month) throws Exception {
        return mockMvc.perform(get("/api/leave/accounting?month=" + month).with(who));
    }

    private void leave(Employee e, LeaveRequestType type, LeaveRequestStatus st, String from, String to, DayPart first, DayPart last) {
        leave(e, type, st, from, to, first, last, "1");
    }

    /** {@code days} = số ngày lưu trên đơn (số sổ đã trừ lúc duyệt). */
    private void leave(
        Employee e,
        LeaveRequestType type,
        LeaveRequestStatus st,
        String from,
        String to,
        DayPart first,
        DayPart last,
        String days
    ) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(e);
        r.setType(type);
        r.setFromDate(LocalDate.parse(from));
        r.setToDate(LocalDate.parse(to));
        r.setFirstPart(first);
        r.setLastPart(last);
        r.setDays(new BigDecimal(days));
        r.setStatus(st);
        r.setApproverKind(ApproverKind.BGD);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        requestRepository.saveAndFlush(r);
    }

    private void overtime(Employee e, String day, int rate, boolean night, OvertimeStatus st, OvertimeCompensation comp, String hours) {
        OvertimeRequest r = new OvertimeRequest();
        r.setEmployee(e);
        r.setWorkDate(LocalDate.parse(day));
        r.setStartTime(LocalTime.of(18, 0));
        r.setEndTime(LocalTime.of(18 + Integer.parseInt(hours), 0));
        r.setPlannedHours(new BigDecimal(hours));
        r.setActualHours(st == OvertimeStatus.HOAN_TAT ? new BigDecimal(hours) : null);
        r.setRate(rate);
        r.setNight(night);
        r.setStatus(st);
        r.setCompensation(comp);
        r.setApproverKind(ApproverKind.TRUONG_NHOM);
        r.setCreatedAt(Instant.now());
        r.setSubmittedBy("test");
        overtimeRepository.saveAndFlush(r);
    }

    private TerminationRecord terminated(Employee e, LocalDate lastWorkingDay) {
        e.setStatus(EmploymentStatus.DA_NGHI);
        e.setLastWorkingDay(lastWorkingDay);
        employeeRepository.saveAndFlush(e);
        TerminationRecord t = new TerminationRecord();
        t.setEmployeeId(e.getId());
        t.setLastWorkingDay(lastWorkingDay);
        t.setTerminationDate(lastWorkingDay.plusDays(1));
        t.setTerminationType(TerminationType.TU_NGHI);
        t.setStatus(TerminationRecordStatus.APPLIED);
        return terminationRepository.saveAndFlush(t);
    }

    private void settle(Employee e, TerminationRecord t, String amount, LocalDate effective) {
        ledger.post(
            e.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal(amount))),
            LedgerReason.NGHI_VIEC,
            LeaveTerminationHook.REF_TERMINATION,
            t.getId(),
            "Chốt",
            effective
        );
    }

    @Test
    void motNguoiMotDongTheoThangVaLamThemTraTienTheoHeSo() throws Exception {
        String row = "$.rows[?(@.code == 'AC7001')]";
        summary(KT, "2026-10")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value("2026-10"))
            .andExpect(jsonPath(row + ".annualDays", contains(2.0)))
            .andExpect(jsonPath(row + ".unpaidDays", contains(1.0)))
            .andExpect(jsonPath(row + ".sickDays", contains(0.0)))
            .andExpect(jsonPath(row + ".ot150Hours", contains(3.0)))
            .andExpect(jsonPath(row + ".ot200Hours", contains(0.0)))
            .andExpect(jsonPath(row + ".ot300Hours", contains(0.0)))
            .andExpect(jsonPath(row + ".settlementDays", contains((Object) null)));
        // Tháng khác: dòng có nhưng toàn số 0.
        summary(KT, "2026-09").andExpect(jsonPath(row + ".annualDays", contains(0.0)));
    }

    @Test
    void donBacQuaThangChiTinhNgayLamViecTrongThangVaNuaNgay() throws Exception {
        Employee b = LeaveFixtures.employee(employeeRepository, "AC7002", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        // Thứ Sáu 30/10 (chiều) → Thứ Hai 02/11: tháng 10 có 0,5 ngày, tháng 11 có 1 ngày.
        leave(b, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.CHO_HUY, "2026-10-30", "2026-11-02", DayPart.CHIEU, DayPart.CA_NGAY, "1.5");
        leave(b, LeaveRequestType.NGHI_BU, LeaveRequestStatus.DA_DUYET, "2026-10-21", "2026-10-21", DayPart.SANG, DayPart.SANG, "0.5");
        overtime(b, "2026-10-25", 200, true, OvertimeStatus.HOAN_TAT, OvertimeCompensation.TRA_TIEN, "4");
        overtime(b, "2026-10-26", 300, true, OvertimeStatus.HOAN_TAT, OvertimeCompensation.TRA_TIEN, "2");
        String row = "$.rows[?(@.code == 'AC7002')]";
        summary(KT, "2026-10")
            .andExpect(jsonPath(row + ".annualDays", contains(0.5)))
            .andExpect(jsonPath(row + ".compensatoryDays", contains(0.5)))
            .andExpect(jsonPath(row + ".ot200Hours", contains(4.0)))
            .andExpect(jsonPath(row + ".ot300Hours", contains(2.0)))
            .andExpect(jsonPath(row + ".nightCount", contains(2)));
        summary(KT, "2026-11").andExpect(jsonPath(row + ".annualDays", contains(1.0)));
    }

    @Test
    void nguoiNghiViecTrongThangCoSoChot() throws Exception {
        Employee paid = LeaveFixtures.employee(employeeRepository, "AC7003", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        Employee owed = LeaveFixtures.employee(employeeRepository, "AC7004", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        TerminationRecord tp = terminated(paid, LocalDate.of(2026, 10, 20));
        TerminationRecord to = terminated(owed, LocalDate.of(2026, 10, 30));
        // Chốt đưa số dư về 0: còn 2,5 ngày → dòng −2,5 (trả tiền); ứng 1 ngày → dòng +1 (trừ lương). Ngày ghi là ngày áp dụng.
        settle(paid, tp, "-2.5", LocalDate.of(2026, 10, 21));
        settle(owed, to, "1", LocalDate.of(2026, 10, 31));
        summary(KT, "2026-10")
            .andExpect(jsonPath("$.rows[?(@.code == 'AC7003')].settlementDays", contains(2.5)))
            .andExpect(jsonPath("$.rows[?(@.code == 'AC7004')].settlementDays", contains(-1.0)));
        // Tháng sau: người đã nghỉ trước tháng không còn dòng.
        summary(KT, "2026-11").andExpect(jsonPath("$.rows[?(@.code == 'AC7003')]", empty()));
    }

    @Test
    void hoanTacNghiViecThiKhongConSoChot() throws Exception {
        Employee e = LeaveFixtures.employee(employeeRepository, "AC7005", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        TerminationRecord t = terminated(e, LocalDate.of(2026, 10, 20));
        settle(e, t, "-3", LocalDate.of(2026, 10, 21));
        ledger.post(
            e.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal("3"))),
            LedgerReason.HOAN_TAC,
            LeaveTerminationHook.REF_TERMINATION,
            t.getId(),
            "Hoàn tác",
            LocalDate.of(2026, 10, 21)
        );
        summary(KT, "2026-10").andExpect(jsonPath("$.rows[?(@.code == 'AC7005')].settlementDays", contains((Object) null)));
    }

    @Test
    void donTronThangDungSoNgayDaTruNgayLeThemSauKhongLamDoi() throws Exception {
        Employee e = LeaveFixtures.employee(employeeRepository, "AC7008", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        leave(e, LeaveRequestType.PHEP_NAM, LeaveRequestStatus.DA_DUYET, "2026-10-21", "2026-10-22", DayPart.CA_NGAY, DayPart.CA_NGAY, "2");
        LocalDate day = LocalDate.of(2026, 10, 22);
        if (!holidayRepository.existsByDay(day)) {
            Holiday h = new Holiday();
            h.setDay(day);
            h.setName("Lễ thêm sau khi duyệt");
            holidayRepository.saveAndFlush(h);
        }
        summary(KT, "2026-10").andExpect(jsonPath("$.rows[?(@.code == 'AC7008')].annualDays", contains(2.0)));
    }

    @Test
    void hoanTacSauKhiKyLuongDaDuyetThiGiuSoChotVaDaoONgThangHoanTac() throws Exception {
        Employee e = LeaveFixtures.employee(employeeRepository, "AC7007", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        TerminationRecord t = terminated(e, LocalDate.of(2026, 9, 18));
        settle(e, t, "-2.5", LocalDate.of(2026, 9, 19));
        // Dòng chốt ghi ngày 19/09, trước khi kỳ lương 09 được duyệt (đồng hồ cố định ghi 22/10; chốt ghi sau khi kỳ đã duyệt thì
        // hiện ở tháng ghi — I2, xem LeaveSettlementLockIT).
        em.flush();
        jdbc.update(
            "update leave_ledger_entry set created_at = ? where employee_id = ? and reason = 'NGHI_VIEC'",
            Timestamp.from(Instant.parse("2026-09-19T03:00:00Z")),
            e.getId()
        );
        em.clear();
        PayrollPeriod p = periodRepository.findByPeriod("2026-09").orElseGet(PayrollPeriod::new);
        p.setPeriod("2026-09");
        p.setStatus(PayrollPeriodStatus.DA_DUYET);
        p.setApprovedVersion(1);
        p.setApprovedBy("kt");
        p.setApprovedAt(Instant.parse("2026-10-01T02:00:00Z"));
        periodRepository.saveAndFlush(p);
        // Hoàn tác ngày 22/10 (đồng hồ cố định), sau khi kỳ lương 09/2026 đã duyệt.
        ledger.post(
            e.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal("2.5"))),
            LedgerReason.HOAN_TAC,
            LeaveTerminationHook.REF_TERMINATION,
            t.getId(),
            "Hoàn tác",
            LocalDate.of(2026, 9, 19)
        );
        e.setStatus(EmploymentStatus.DANG_LAM_VIEC);
        e.setLastWorkingDay(null);
        employeeRepository.saveAndFlush(e);
        String row = "$.rows[?(@.code == 'AC7007')]";
        summary(KT, "2026-09")
            .andExpect(jsonPath(row + ".settlementDays", contains(2.5)))
            .andExpect(jsonPath(row + ".settlementReversal", contains(false)));
        summary(KT, "2026-10")
            .andExpect(jsonPath(row + ".settlementDays", contains(-2.5)))
            .andExpect(jsonPath(row + ".settlementReversal", contains(true)));
    }

    @Test
    void thucTapKhongCoDong() throws Exception {
        Employee intern = LeaveFixtures.employee(employeeRepository, "AC7006", PersonType.THUC_TAP, LocalDate.of(2024, 1, 2));
        leave(intern, LeaveRequestType.KHONG_LUONG, LeaveRequestStatus.DA_DUYET, "2026-10-14", "2026-10-14", DayPart.CA_NGAY, DayPart.CA_NGAY);
        summary(KT, "2026-10").andExpect(jsonPath("$.rows[?(@.code == 'AC7006')]", empty()));
    }

    @Test
    void quyenKeToanHrQuanTriBgdBiChan() throws Exception {
        summary(user("hr-acc").authorities(() -> "ROLE_HR"), "2026-10").andExpect(status().isOk());
        summary(user("qt-acc").authorities(() -> "ROLE_ADMIN"), "2026-10").andExpect(status().isOk());
        summary(user("bgd-acc").authorities(() -> "ROLE_BGD"), "2026-10").andExpect(status().isForbidden());
        summary(user("nv-acc").authorities(() -> "ROLE_NHAN_VIEN"), "2026-10").andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/leave/accounting/export.xlsx?month=2026-10").with(user("bgd-acc").authorities(() -> "ROLE_BGD")))
            .andExpect(status().isForbidden());
        summary(KT, "2026-13").andExpect(status().isBadRequest());
    }

    @Test
    void excelCoTieuDeTiengViet() throws Exception {
        byte[] body = mockMvc
            .perform(get("/api/leave/accounting/export.xlsx?month=2026-10").with(KT))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = wb.getSheetAt(0);
            List<String> headers = new ArrayList<>();
            for (Cell c : sheet.getRow(0)) {
                headers.add(c.getStringCellValue());
            }
            assertThat(headers).containsExactly(
                "Mã NV",
                "Họ và tên",
                "Phòng ban",
                "Tình trạng",
                "Phép năm (ngày)",
                "Việc riêng có lương (ngày)",
                "Nghỉ ốm (ngày)",
                "Không lương (ngày)",
                "Nghỉ bù (ngày)",
                "Làm thêm 150% (giờ)",
                "Làm thêm 200% (giờ)",
                "Làm thêm 300% (giờ)",
                "Số lần có giờ đêm",
                "Phép năm chốt khi nghỉ việc (ngày)",
                "Xử lý phép khi nghỉ việc",
                "Giờ nghỉ bù chốt khi nghỉ việc"
            );
            Row found = null;
            for (Row r : sheet) {
                if (r.getRowNum() > 0 && "AC7001".equals(r.getCell(0).getStringCellValue())) {
                    found = r;
                }
            }
            assertThat(found).isNotNull();
            assertThat(found.getCell(4).getNumericCellValue()).isEqualTo(2.0);
            assertThat(found.getCell(9).getNumericCellValue()).isEqualTo(3.0);
        }
    }
}

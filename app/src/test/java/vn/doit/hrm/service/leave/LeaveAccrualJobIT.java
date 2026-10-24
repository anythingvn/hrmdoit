package vn.doit.hrm.service.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.LeaveSetting;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.repository.LeaveSettingRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.dto.LeaveSettingDTO;

/** Cộng phép hằng tháng (chạy mỗi ngày), chuyển năm 01/01, hết hạn 01/04, chạy lại không ghi trùng, chạy bù khi khởi động. */
@IntegrationTest
@Transactional
@Import(LeaveAccrualJobIT.MutableClockConfig.class)
class LeaveAccrualJobIT {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Đồng hồ đổi được giữa các bước của một test. */
    static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-03-01T00:00:00Z");

        void set(LocalDateTime local) {
            now = local.atZone(VN).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return VN;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MutableClock clock;

    @Autowired
    private LeaveAccrualJob job;

    @Autowired
    private LeaveLedgerService ledger;

    @Autowired
    private LeaveLedgerEntryRepository entries;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveSettingService settings;

    @Autowired
    private LeaveSettingRepository settingRepository;

    @BeforeEach
    void at() {
        clock.set(LocalDateTime.of(2026, 3, 1, 0, 10));
    }

    private List<LeaveLedgerEntry> rows(Employee e, LedgerReason reason) {
        return entries
            .findByEmployeeIdOrderByEffectiveDateAscIdAsc(e.getId())
            .stream()
            .filter(r -> r.getReason() == reason)
            .toList();
    }

    @Test
    void monthlyAccrualOnceEvenWhenRunTwice() {
        Employee nv = LeaveFixtures.employee(employeeRepository, "NV9801", PersonType.NHAN_VIEN, LocalDate.of(2026, 3, 1));

        job.runMonth(YearMonth.of(2026, 3));
        job.runMonth(YearMonth.of(2026, 3));

        List<LeaveLedgerEntry> accrued = rows(nv, LedgerReason.CONG_THANG);
        assertThat(accrued).hasSize(1);
        LeaveLedgerEntry r = accrued.get(0);
        assertThat(r.getAmount()).isEqualByComparingTo("1.00");
        assertThat(r.getPool()).isEqualTo(Pool.PHEP_NAM);
        assertThat(r.getBucket()).isEqualTo(Bucket.NAM_NAY);
        assertThat(r.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(r.getRefType()).isEqualTo("MONTH");
        assertThat(r.getRefId()).isEqualTo(202603L);
    }

    @Test
    void internsAndServiceContractorsHaveNoLedger() {
        Employee tt = LeaveFixtures.employee(employeeRepository, "TT9801", PersonType.THUC_TAP, LocalDate.of(2026, 3, 1));
        Employee dv = LeaveFixtures.employee(employeeRepository, "HDDV9801", PersonType.DICH_VU, LocalDate.of(2026, 3, 1));

        job.runMonth(YearMonth.of(2026, 3));

        assertThat(entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(tt.getId())).isEmpty();
        assertThat(entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(dv.getId())).isEmpty();
    }

    @Test
    void terminatedEmployeeGetsNothingForLaterMonths() {
        Employee left = LeaveFixtures.employee(employeeRepository, "NV9802", PersonType.NHAN_VIEN, LocalDate.of(2025, 1, 2));
        left.setStatus(EmploymentStatus.DA_NGHI);
        left.setLastWorkingDay(LocalDate.of(2026, 2, 27));
        employeeRepository.saveAndFlush(left);

        job.runMonth(YearMonth.of(2026, 3));

        assertThat(rows(left, LedgerReason.CONG_THANG)).isEmpty();
    }

    @Test
    void yearEndCarryThenAccrualThenExpiry() {
        Employee nv = LeaveFixtures.employee(employeeRepository, "NV9803", PersonType.NHAN_VIEN, LocalDate.of(2024, 6, 3));
        ledger.post(
            nv.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal("2.5"))),
            LedgerReason.DAU_KY,
            "OPENING",
            null,
            "Số dư đầu kỳ",
            LocalDate.of(2026, 12, 15)
        );

        clock.set(LocalDateTime.of(2027, 1, 1, 0, 10));
        // Trước khi việc tự động chạy, số dư hôm nay đã tính phần chuyển năm.
        Balance before = ledger.balance(nv.getId(), Pool.PHEP_NAM);
        assertThat(before.namTruoc()).isEqualByComparingTo("2.5");
        assertThat(before.namNay()).isEqualByComparingTo("0");

        job.runMonth(YearMonth.of(2027, 1));
        job.runMonth(YearMonth.of(2027, 1));

        List<LeaveLedgerEntry> carry = rows(nv, LedgerReason.CHUYEN_NAM);
        assertThat(carry).hasSize(2);
        assertThat(carry).allSatisfy(r -> {
            assertThat(r.getEffectiveDate()).isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(r.getRefType()).isEqualTo("YEAR");
            assertThat(r.getRefId()).isEqualTo(2027L);
        });
        assertThat(rows(nv, LedgerReason.CONG_THANG)).hasSize(1);
        Balance jan = ledger.balance(nv.getId(), Pool.PHEP_NAM);
        assertThat(jan.namTruoc()).isEqualByComparingTo("2.5");
        assertThat(jan.namNay()).isEqualByComparingTo("1");

        clock.set(LocalDateTime.of(2027, 4, 1, 0, 10));
        job.runMonth(YearMonth.of(2027, 4));
        job.runMonth(YearMonth.of(2027, 4));

        List<LeaveLedgerEntry> expired = rows(nv, LedgerReason.HET_HAN);
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getBucket()).isEqualTo(Bucket.NAM_TRUOC);
        assertThat(expired.get(0).getAmount()).isEqualByComparingTo("-2.5");
        assertThat(expired.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(expired.get(0).getRefId()).isEqualTo(2027L);
        Balance apr = ledger.balance(nv.getId(), Pool.PHEP_NAM);
        assertThat(apr.namTruoc()).isEqualByComparingTo("0");
        assertThat(apr.namNay()).isEqualByComparingTo("2");
    }

    @Test
    void negativeBalanceStaysInCurrentYearAtCarry() {
        Employee nv = LeaveFixtures.employee(employeeRepository, "NV9805", PersonType.NHAN_VIEN, LocalDate.of(2024, 6, 3));
        ledger.post(
            nv.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal("-1"))),
            LedgerReason.DIEU_CHINH,
            null,
            null,
            "Ứng 1 ngày",
            LocalDate.of(2026, 12, 20)
        );
        clock.set(LocalDateTime.of(2027, 1, 1, 0, 10));

        job.runMonth(YearMonth.of(2027, 1));

        assertThat(rows(nv, LedgerReason.CHUYEN_NAM)).isEmpty();
        Balance b = ledger.balance(nv.getId(), Pool.PHEP_NAM);
        assertThat(b.namTruoc()).isEqualByComparingTo("0");
        assertThat(b.namNay()).isEqualByComparingTo("0");
    }

    @Test
    void restartMidMonthCatchesUpCurrentMonthOnce() {
        Employee nv = LeaveFixtures.employee(employeeRepository, "NV9804", PersonType.NHAN_VIEN, LocalDate.of(2025, 5, 5));
        clock.set(LocalDateTime.of(2026, 10, 15, 9, 0));

        job.catchUp();
        job.catchUp();

        List<LeaveLedgerEntry> accrued = rows(nv, LedgerReason.CONG_THANG);
        assertThat(accrued).hasSize(1);
        assertThat(accrued.get(0).getRefId()).isEqualTo(202610L);
        assertThat(accrued.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void jobRunsDailyAndAccruesJoiningMonthForProfileCreatedAfterFirst() throws Exception {
        Scheduled cron = LeaveAccrualJob.class.getMethod("scheduled").getAnnotation(Scheduled.class);
        assertThat(cron.cron()).isEqualTo("0 10 0 * * *");
        assertThat(cron.zone()).isEqualTo("Asia/Ho_Chi_Minh");

        // 01/03: hồ sơ chưa có.
        job.scheduled();
        // 12/03: HR tạo hồ sơ, ngày vào 10/03 (đủ 50% ngày làm việc của tháng 3).
        clock.set(LocalDateTime.of(2026, 3, 12, 15, 0));
        Employee nv = LeaveFixtures.employee(employeeRepository, "NV9806", PersonType.NHAN_VIEN, LocalDate.of(2026, 3, 10));
        // Lần chạy hằng ngày kế tiếp.
        clock.set(LocalDateTime.of(2026, 3, 13, 0, 10));
        job.scheduled();
        clock.set(LocalDateTime.of(2026, 3, 14, 0, 10));
        job.scheduled();

        List<LeaveLedgerEntry> accrued = rows(nv, LedgerReason.CONG_THANG);
        assertThat(accrued).hasSize(1);
        assertThat(accrued.get(0).getRefId()).isEqualTo(202603L);
        assertThat(accrued.get(0).getAmount()).isEqualByComparingTo("1.00");
        assertThat(accrued.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void expiryMovedEarlierNeverTakesPreviousYearBelowZero() {
        Employee nv = LeaveFixtures.employee(employeeRepository, "NV9807", PersonType.NHAN_VIEN, LocalDate.of(2024, 6, 3));
        ledger.post(
            nv.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, new BigDecimal("3"))),
            LedgerReason.DAU_KY,
            "OPENING",
            null,
            "Số dư đầu kỳ",
            LocalDate.of(2025, 12, 15)
        );
        // 01/01: +3 sang NAM_TRUOC; hạn đặt là 30/06.
        clock.set(LocalDateTime.of(2026, 1, 1, 0, 10));
        job.scheduled();
        clock.set(LocalDateTime.of(2026, 1, 2, 9, 0));
        LeaveSettingDTO s = settings.get();
        settings.update(withExpiry(s, 6, 30));
        // 05/04: đơn được duyệt trừ 2 ngày vào NAM_TRUOC.
        clock.set(LocalDateTime.of(2026, 4, 5, 9, 0));
        ledger.post(
            nv.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_TRUOC, new BigDecimal("-2"))),
            LedgerReason.DUYET_DON,
            "LEAVE_REQUEST",
            9807L,
            "Nghỉ phép"
        );
        // 15/04: API không cho đưa hạn về 31/03 ...
        clock.set(LocalDateTime.of(2026, 4, 15, 9, 0));
        assertThatThrownBy(() -> settings.update(withExpiry(s, 3, 31)))
            .isInstanceOf(EmployeeRuleException.class)
            .hasMessage("Ngày hết hạn phép năm trước không được trước hôm nay");
        // ... nhưng dù hạn đã là 31/03 (ví dụ sửa thẳng dữ liệu), NAM_TRUOC chỉ hết hạn phần còn lại, không âm.
        LeaveSetting row = settingRepository.findById(1L).orElseThrow();
        row.setCarryExpiryMonth(3);
        row.setCarryExpiryDay(31);
        settingRepository.saveAndFlush(row);

        Balance before = ledger.balance(nv.getId(), Pool.PHEP_NAM);
        assertThat(before.namTruoc()).isEqualByComparingTo("0");

        clock.set(LocalDateTime.of(2026, 4, 16, 0, 10));
        job.scheduled();
        job.scheduled();

        List<LeaveLedgerEntry> expired = rows(nv, LedgerReason.HET_HAN);
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getAmount()).isEqualByComparingTo("-1");
        assertThat(expired.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        Balance after = ledger.balance(nv.getId(), Pool.PHEP_NAM);
        assertThat(after.namTruoc()).isEqualByComparingTo("0");
    }

    @Test
    void joiningMonthInPreviousMonthIsAccruedWhenProfileCreatedNextMonth() {
        // Vào 05/10 (20/22 ngày làm việc), hồ sơ tạo 03/11.
        clock.set(LocalDateTime.of(2026, 11, 3, 0, 5));
        Employee nv = LeaveFixtures.employee(employeeRepository, "NV9808", PersonType.NHAN_VIEN, LocalDate.of(2026, 10, 5));
        clock.set(LocalDateTime.of(2026, 11, 3, 0, 10));

        job.scheduled();
        job.scheduled();

        List<LeaveLedgerEntry> accrued = rows(nv, LedgerReason.CONG_THANG);
        assertThat(accrued).extracting(LeaveLedgerEntry::getRefId).containsExactly(202610L, 202611L);
        assertThat(accrued).allSatisfy(r -> assertThat(r.getAmount()).isEqualByComparingTo("1.00"));
        assertThat(accrued.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(accrued.get(1).getEffectiveDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void joiningMonthInPreviousMonthUnderHalfIsNotAccrued() {
        // Vào 20/10: 9/22 ngày làm việc của tháng 10, dưới 50%.
        clock.set(LocalDateTime.of(2026, 11, 3, 0, 5));
        Employee nv = LeaveFixtures.employee(employeeRepository, "NV9809", PersonType.NHAN_VIEN, LocalDate.of(2026, 10, 20));
        clock.set(LocalDateTime.of(2026, 11, 3, 0, 10));

        job.scheduled();
        job.scheduled();

        assertThat(rows(nv, LedgerReason.CONG_THANG)).extracting(LeaveLedgerEntry::getRefId).containsExactly(202611L);
    }

    @Test
    void olderJoinDateEnteredLateGetsOnlyCurrentMonth() {
        clock.set(LocalDateTime.of(2026, 11, 3, 0, 5));
        Employee nv = LeaveFixtures.employee(employeeRepository, "NV9810", PersonType.NHAN_VIEN, LocalDate.of(2024, 3, 15));
        clock.set(LocalDateTime.of(2026, 11, 3, 0, 10));

        job.scheduled();
        job.scheduled();

        assertThat(rows(nv, LedgerReason.CONG_THANG)).extracting(LeaveLedgerEntry::getRefId).containsExactly(202611L);
    }

    private static LeaveSettingDTO withExpiry(LeaveSettingDTO s, int month, int day) {
        return new LeaveSettingDTO(
            s.advanceDays(),
            s.directorThresholdDays(),
            s.otMonthCap(),
            s.otYearCap(),
            month,
            day,
            s.pastRequestDays(),
            s.otConfirmDay(),
            s.seniorityCycleYears(),
            s.seniorityExtraDays()
        );
    }
}

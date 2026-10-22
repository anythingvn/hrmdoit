package vn.doit.hrm.service.leave;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;

/** Cộng phép đầu tháng, chuyển năm 01/01, hết hạn 01/04, chạy lại không ghi trùng, chạy bù khi khởi động. */
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
}

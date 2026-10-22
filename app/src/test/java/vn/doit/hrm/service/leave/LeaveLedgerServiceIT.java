package vn.doit.hrm.service.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.Balance;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;

/** Sổ phép: ghi đúng ngăn, số dư bằng tổng các dòng (tính tại hôm nay), dòng không sửa không xóa. Hôm nay = 22/10/2026. */
@IntegrationTest
@Transactional
@Import(LeaveLedgerServiceIT.FixedClockConfig.class)
class LeaveLedgerServiceIT {

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private LeaveLedgerService ledger;

    @Autowired
    private LeaveLedgerEntryRepository entries;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee nv;

    /** Không dùng @BeforeEach: test chạy ngoài transaction sẽ ghi hẳn hồ sơ vào DB. */
    private void init() {
        nv = LeaveFixtures.employee(employeeRepository, "NV9701", PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void postWritesRowsInTheirBucketsAndBalanceSumsThem() {
        init();
        List<LeaveLedgerEntry> rows = ledger.post(
            nv.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_TRUOC, d("2.5")), new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, d("1.333"))),
            LedgerReason.DIEU_CHINH,
            null,
            null,
            "Điều chỉnh thử"
        );
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getBucket()).isEqualTo(Bucket.NAM_TRUOC);
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("2.50");
        assertThat(rows.get(0).getAmount().scale()).isEqualTo(2);
        assertThat(rows.get(1).getAmount()).isEqualByComparingTo("1.33");
        assertThat(rows.get(0).getYear()).isEqualTo(2026);
        assertThat(rows.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(rows.get(0).getReason()).isEqualTo(LedgerReason.DIEU_CHINH);
        assertThat(rows.get(0).getCreatedBy()).isNotBlank();
        assertThat(rows.get(0).getCreatedAt()).isNotNull();

        ledger.post(
            nv.getId(),
            Pool.NGHI_BU,
            List.of(new Entry(Pool.NGHI_BU, Bucket.NAM_NAY, d("6"))),
            LedgerReason.DIEU_CHINH,
            null,
            null,
            "Giờ bù"
        );
        ledger.post(
            nv.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, d("-0.5"))),
            LedgerReason.DIEU_CHINH,
            null,
            null,
            "Trừ"
        );

        Balance annual = ledger.balance(nv.getId(), Pool.PHEP_NAM);
        assertThat(annual.namTruoc()).isEqualByComparingTo("2.50");
        assertThat(annual.namNay()).isEqualByComparingTo("0.83");
        assertThat(annual.total()).isEqualByComparingTo("3.33");
        assertThat(ledger.balance(nv.getId(), Pool.NGHI_BU).total()).isEqualByComparingTo("6");
        assertThat(entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(nv.getId())).hasSize(4);
    }

    @Test
    void zeroRowsAndPoolMismatchAreRejected() {
        init();
        assertThatThrownBy(() ->
            ledger.post(
                nv.getId(),
                Pool.PHEP_NAM,
                List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, d("0.001"))),
                LedgerReason.DIEU_CHINH,
                null,
                null,
                "x"
            )
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            ledger.post(
                nv.getId(),
                Pool.PHEP_NAM,
                List.of(new Entry(Pool.NGHI_BU, Bucket.NAM_NAY, d("1"))),
                LedgerReason.DIEU_CHINH,
                null,
                null,
                "x"
            )
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.post(nv.getId(), Pool.PHEP_NAM, List.of(), LedgerReason.DIEU_CHINH, null, null, "x")).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThat(entries.findByEmployeeIdOrderByEffectiveDateAscIdAsc(nv.getId())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void postNeedsTheCallersTransaction() {
        assertThatThrownBy(() ->
            ledger.post(
                1L,
                Pool.PHEP_NAM,
                List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, d("1"))),
                LedgerReason.DIEU_CHINH,
                null,
                null,
                "x"
            )
        ).isInstanceOf(IllegalTransactionStateException.class);
    }

    /** Dòng của năm trước chưa được chuyển năm / hết hạn: số dư hôm nay đã tính như việc tự động đã chạy. */
    @Test
    void balanceIsComputedAsOfToday() {
        init();
        ledger.post(
            nv.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, d("3"))),
            LedgerReason.DIEU_CHINH,
            null,
            null,
            "Còn của 2025",
            LocalDate.of(2025, 12, 1)
        );
        ledger.post(
            nv.getId(),
            Pool.PHEP_NAM,
            List.of(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, d("2"))),
            LedgerReason.DIEU_CHINH,
            null,
            null,
            "Của 2026",
            LocalDate.of(2026, 5, 1)
        );
        // 01/01/2026: 3 chuyển sang ngăn năm trước; 01/04/2026: hết hạn. Còn 2 của năm nay.
        Balance b = ledger.balance(nv.getId(), Pool.PHEP_NAM);
        assertThat(b.namTruoc()).isEqualByComparingTo("0");
        assertThat(b.namNay()).isEqualByComparingTo("2");
    }

    @Test
    void ledgerRowsCannotBeChangedOrDeleted() {
        assertThat(CrudRepository.class.isAssignableFrom(LeaveLedgerEntryRepository.class)).isFalse();
        assertThat(Arrays.stream(LeaveLedgerEntryRepository.class.getMethods()).map(Method::getName)).noneMatch(
            n -> n.startsWith("delete") || n.startsWith("update") || n.startsWith("remove")
        );
        assertThat(Arrays.stream(LeaveLedgerEntry.class.getMethods()).map(Method::getName)).noneMatch(n -> n.startsWith("set"));
    }
}

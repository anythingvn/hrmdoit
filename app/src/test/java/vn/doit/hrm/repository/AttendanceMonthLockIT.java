package vn.doit.hrm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;

/**
 * Tạo dòng tháng chấm công lần đầu ({@link AttendanceMonthRepository#lockOrCreate}): gọi lại hay hai giao dịch cùng gọi đều không lỗi,
 * chỉ một dòng (R28). Không chạy trong giao dịch của test: mỗi bước tự mở giao dịch và dọn dữ liệu sau khi xong.
 */
@IntegrationTest
class AttendanceMonthLockIT {

    @Autowired
    private AttendanceMonthRepository monthRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private DataSource dataSource;

    private long rows(TransactionTemplate tx, String month) {
        return tx.execute(st -> monthRepository.findAll().stream().filter(m -> month.equals(m.getMonth())).count());
    }

    private void cleanup(TransactionTemplate tx, String month) {
        tx.executeWithoutResult(st -> monthRepository.findByMonth(month).ifPresent(monthRepository::delete));
    }

    @Test
    void lockOrCreateTwiceGivesOneRow() {
        String month = "2031-01";
        TransactionTemplate tx = new TransactionTemplate(txManager);
        try {
            tx.executeWithoutResult(st -> {
                AttendanceMonth first = monthRepository.lockOrCreate(month);
                AttendanceMonth second = monthRepository.lockOrCreate(month);
                assertThat(second.getId()).isEqualTo(first.getId());
                assertThat(first.getStatus()).isEqualTo(AttendanceMonthStatus.DANG_MO);
            });
            // Giao dịch sau: dòng đã có thì chỉ khóa, không tạo thêm.
            Long again = tx.execute(st -> monthRepository.lockOrCreate(month).getId());
            assertThat(again).isNotNull();
            assertThat(rows(tx, month)).isEqualTo(1L);
        } finally {
            cleanup(tx, month);
        }
    }

    /**
     * Một giao dịch khác (kết nối JDBC riêng, vì pool của test chỉ có một kết nối) vừa tạo dòng tháng nhưng chưa commit; lockOrCreate
     * chờ nó commit rồi dùng chính dòng đó, không lỗi khóa duy nhất.
     */
    @Test
    void concurrentFirstLockOrCreateDoesNotFail() throws Exception {
        String month = "2031-02";
        TransactionTemplate tx = new TransactionTemplate(txManager);
        HikariDataSource ds = dataSource.unwrap(HikariDataSource.class);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection other = DriverManager.getConnection(ds.getJdbcUrl(), ds.getUsername(), ds.getPassword())) {
            other.setAutoCommit(false);
            Long otherId;
            try (PreparedStatement ps = other.prepareStatement(
                    "insert into attendance_month(id, month, status) values (nextval('sequence_generator'), ?, 'DANG_MO') returning id"
                )) {
                ps.setString(1, month);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    otherId = rs.getLong(1);
                }
            }
            Future<?> commitLater = pool.submit(() -> {
                Thread.sleep(500);
                other.commit();
                return null;
            });
            Long mine = tx.execute(st -> monthRepository.lockOrCreate(month).getId());
            commitLater.get(30, TimeUnit.SECONDS);
            assertThat(mine).isEqualTo(otherId);
            assertThat(rows(tx, month)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
            cleanup(tx, month);
        }
    }
}

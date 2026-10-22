package vn.doit.hrm.service.leave;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;

/**
 * Việc tự động của sổ phép, 00:10 ngày 01 mỗi tháng (giờ Việt Nam). Với từng người loại NHAN_VIEN, mỗi người một transaction:
 * <ol>
 *   <li>ngày 01/01: chuyển năm ({@code CHUYEN_NAM}) trước;</li>
 *   <li>sau hạn dùng phép năm trước (mặc định 31/03, tức lần chạy 01/04): hết hạn ({@code HET_HAN});</li>
 *   <li>cộng phép của tháng ({@code CONG_THANG}).</li>
 * </ol>
 * Chạy lại hoặc chạy trên nhiều bản không ghi trùng: khóa dòng hồ sơ rồi mới đọc sổ, kèm chỉ mục duy nhất theo liên kết
 * (tháng / năm). Ứng dụng khởi động mà tháng hiện tại chưa chạy (hoặc đã qua 01/01, 01/04 mà chưa ghi) thì chạy bù ngay.
 */
@Component
public class LeaveAccrualJob {

    private static final Logger LOG = LoggerFactory.getLogger(LeaveAccrualJob.class);

    public record RunResult(int employees, int rowsWritten, int failures) {}

    private final Clock clock;
    private final EmployeeRepository employeeRepository;
    private final LeaveAccrualService accrualService;
    private final boolean catchUpOnStartup;

    public LeaveAccrualJob(
        Clock clock,
        EmployeeRepository employeeRepository,
        LeaveAccrualService accrualService,
        ApplicationProperties properties
    ) {
        this.clock = clock;
        this.employeeRepository = employeeRepository;
        this.accrualService = accrualService;
        this.catchUpOnStartup = properties.getLeave().isCatchUpOnStartup();
    }

    @Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduled() {
        runMonth(YearMonth.now(clock));
    }

    /** Chạy bù khi khởi động; không bao giờ làm hỏng việc khởi động. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (catchUpOnStartup) {
            catchUp();
        }
    }

    /** Chạy bù tháng hiện tại (an toàn khi đã chạy rồi). */
    public void catchUp() {
        try {
            RunResult r = runMonth(YearMonth.now(clock));
            if (r.rowsWritten() > 0) {
                LOG.info("Chạy bù sổ phép: ghi {} dòng cho {} người", r.rowsWritten(), r.employees());
            }
        } catch (RuntimeException e) {
            LOG.error("Không chạy bù được sổ phép khi khởi động", e);
        }
    }

    /** Chạy cho tháng {@code month}; lỗi ở một người không chặn người khác. */
    public synchronized RunResult runMonth(YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        LocalDate asOf = YearMonth.from(today).equals(month) ? today : month.atDay(1);
        int rows = 0;
        int failures = 0;
        var ids = employeeRepository.findIdsByPersonType(PersonType.NHAN_VIEN);
        for (Long id : ids) {
            try {
                rows += accrualService.settle(id, month, asOf);
            } catch (DataIntegrityViolationException e) {
                // Bản khác vừa ghi cùng dòng: bỏ qua.
                LOG.info("Sổ phép của hồ sơ {} tháng {} đã được ghi ở nơi khác", id, month);
            } catch (RuntimeException e) {
                failures++;
                LOG.error("Lỗi sổ phép của hồ sơ {} tháng {}", id, month, e);
            }
        }
        return new RunResult(ids.size(), rows, failures);
    }
}

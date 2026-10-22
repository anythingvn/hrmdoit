package vn.doit.hrm.service.leave;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
 * Việc tự động của sổ phép, 00:10 mỗi ngày (giờ Việt Nam), luôn chốt tháng hiện tại. Với từng người loại NHAN_VIEN, mỗi người
 * một transaction:
 * <ol>
 *   <li>từ 01/01: chuyển năm ({@code CHUYEN_NAM}) trước;</li>
 *   <li>từ ngày sau hạn dùng phép năm trước (mặc định 31/03, tức lần chạy 01/04): hết hạn ({@code HET_HAN});</li>
 *   <li>cộng phép của tháng ({@code CONG_THANG}); nếu ngày vào thuộc tháng liền trước mà tháng đó chưa cộng thì cộng cả tháng vào
 *       (ghi trước bước chuyển năm).</li>
 * </ol>
 * Cộng phép theo ngày vào, không theo ngày tạo hồ sơ. Chạy mỗi ngày để hồ sơ tạo sau ngày 01 vẫn được cộng phép tháng vào làm (cả
 * khi tạo sang đầu tháng sau); ngày vào sớm hơn tháng liền trước thì không cộng bù (HR nhập số dư đầu kỳ). Phần hết hạn được
 * ghi ngay ngày sau hạn dù hạn không phải cuối tháng. Chạy lại hoặc chạy trên nhiều bản không ghi trùng: khóa dòng hồ sơ rồi mới
 * đọc sổ, kèm chỉ mục duy nhất theo liên kết (tháng / năm). Ứng dụng khởi động thì chạy bù tháng hiện tại ngay.
 */
@Component
public class LeaveAccrualJob {

    private static final Logger LOG = LoggerFactory.getLogger(LeaveAccrualJob.class);

    public record RunResult(int employees, int rowsWritten, int failures) {}

    private final Clock clock;
    private final EmployeeRepository employeeRepository;
    private final LeaveAccrualService accrualService;
    private final boolean catchUpOnStartup;
    private final LeaveRequestService requestService;
    private final OvertimeService overtimeService;

    public LeaveAccrualJob(
        Clock clock,
        EmployeeRepository employeeRepository,
        LeaveAccrualService accrualService,
        ApplicationProperties properties,
        LeaveRequestService requestService,
        OvertimeService overtimeService
    ) {
        this.clock = clock;
        this.employeeRepository = employeeRepository;
        this.accrualService = accrualService;
        this.requestService = requestService;
        this.overtimeService = overtimeService;
        this.catchUpOnStartup = properties.getLeave().isCatchUpOnStartup();
    }

    /** 00:10 mỗi ngày: chốt tháng hiện tại (không ghi trùng phần đã ghi). */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduled() {
        runMonth(YearMonth.now(clock));
    }

    /**
     * 00:20 mỗi ngày: chuyển các đơn nghỉ và đơn làm thêm giờ đang chờ có người duyệt đã nghỉ việc hoặc bị khóa tài khoản lên cấp kế
     * tiếp (ghi lý do trên đơn, báo người duyệt mới). Mỗi đơn một transaction (như tự xác nhận): lỗi ở một đơn chỉ ghi log, không chặn
     * và không hoàn tác các đơn khác.
     */
    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void reassignOrphans() {
        reassignEach("đơn nghỉ", requestService::orphanCandidates, requestService::reassignOrphan);
        reassignEach("đơn làm thêm giờ", overtimeService::orphanCandidates, overtimeService::reassignOrphan);
    }

    private static void reassignEach(String kind, Supplier<List<Long>> candidates, Predicate<Long> reassign) {
        List<Long> ids;
        try {
            ids = candidates.get();
        } catch (RuntimeException e) {
            LOG.error("Không đọc được danh sách {} có người duyệt đã nghỉ hoặc bị khóa", kind, e);
            return;
        }
        int moved = 0;
        for (Long id : ids) {
            try {
                if (reassign.test(id)) {
                    moved++;
                }
            } catch (RuntimeException e) {
                LOG.error("Lỗi khi chuyển {} {} sang người duyệt kế tiếp", kind, id, e);
            }
        }
        if (moved > 0) {
            LOG.info("Đã chuyển {} {} sang người duyệt kế tiếp", moved, kind);
        }
    }

    /**
     * 00:30 mỗi ngày: từ ngày tự xác nhận (cài đặt {@code ot_confirm_day}, mặc định 05) của tháng, các đơn làm thêm giờ của các tháng
     * trước còn chờ xác nhận giờ được tự xác nhận với giờ thực tế = giờ đăng ký ({@code auto_confirmed}). Chạy mỗi ngày nên lần chạy bị
     * lỡ (ứng dụng tắt) được bù vào ngày sau; đơn đã xong không bị xử lý lại. Mỗi đơn một transaction, lỗi ở một đơn chỉ ghi log.
     */
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void autoConfirmOvertime() {
        List<Long> ids;
        try {
            ids = overtimeService.autoConfirmCandidates(LocalDate.now(clock));
        } catch (RuntimeException e) {
            LOG.error("Không đọc được danh sách đơn làm thêm giờ cần tự xác nhận", e);
            return;
        }
        int done = 0;
        for (Long id : ids) {
            try {
                if (overtimeService.autoConfirm(id)) {
                    done++;
                }
            } catch (RuntimeException e) {
                LOG.error("Lỗi khi tự xác nhận đơn làm thêm giờ {}", id, e);
            }
        }
        if (done > 0) {
            LOG.info("Đã tự xác nhận {} đơn làm thêm giờ", done);
        }
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

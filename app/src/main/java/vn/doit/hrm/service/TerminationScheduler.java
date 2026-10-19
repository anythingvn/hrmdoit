package vn.doit.hrm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job hằng ngày 00:05 (giờ Việt Nam): áp dụng các lịch nghỉ việc đã đến hạn (ngày làm việc cuối trước hôm nay).
 * Mỗi lịch một transaction riêng; lỗi ở một người không chặn người khác. Chạy lặp hoặc trên nhiều máy đều an toàn
 * (xem {@link TerminationService#applyScheduled}).
 */
@Component
public class TerminationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(TerminationScheduler.class);

    private final TerminationService terminationService;

    public TerminationScheduler(TerminationService terminationService) {
        this.terminationService = terminationService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void applyDue() {
        for (Long id : terminationService.dueRecordIds()) {
            try {
                if (terminationService.applyScheduled(id)) {
                    LOG.info("Đã áp dụng lịch nghỉ việc {}", id);
                }
            } catch (RuntimeException e) {
                LOG.error("Lỗi khi áp dụng lịch nghỉ việc {}", id, e);
            }
        }
    }
}

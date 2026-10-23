package vn.doit.hrm.service.attendance;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.CompanySetting;
import vn.doit.hrm.repository.CompanySettingRepository;

/**
 * 00:40 mỗi ngày (giờ Việt Nam): đặt tọa độ, sai số và IP về null cho lần chấm có ngày công trước (hôm nay − số ngày lưu vị trí
 * trong cài đặt công ty, mặc định 90). Giữ nguyên điểm chấm, khoảng cách, phương thức, trạng thái, lý do. Bảng lịch sử
 * {@code attendance_punch_aud} cũng bị xóa cột vị trí để lịch sử không giữ lại tọa độ. Không ghi log giá trị vị trí, chỉ ghi số dòng.
 */
@Component
public class PunchLocationPurgeJob {

    private static final Logger LOG = LoggerFactory.getLogger(PunchLocationPurgeJob.class);

    private static final String CLEAR = "latitude = null, longitude = null, accuracy_m = null, client_ip = null";
    private static final String ANY_LOCATION = "(latitude is not null or longitude is not null or accuracy_m is not null or client_ip is not null)";

    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final CompanySettingRepository settingRepository;

    public PunchLocationPurgeJob(Clock clock, JdbcTemplate jdbc, CompanySettingRepository settingRepository) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.settingRepository = settingRepository;
    }

    @Scheduled(cron = "0 40 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduled() {
        purge();
    }

    /** Xóa vị trí của lần chấm quá hạn lưu; trả về số dòng của {@code attendance_punch} đã xóa vị trí. */
    @Transactional
    public int purge() {
        int days = settingRepository.findById(CompanySetting.ID).map(CompanySetting::getLocationRetentionDays).orElse(90);
        Date cutoff = Date.valueOf(LocalDate.now(clock).minusDays(days));
        int punches = jdbc.update("update attendance_punch set " + CLEAR + " where work_day < ? and " + ANY_LOCATION, cutoff);
        int history = jdbc.update("update attendance_punch_aud set " + CLEAR + " where work_day < ? and " + ANY_LOCATION, cutoff);
        LOG.info("Đã xóa vị trí của {} lần chấm và {} bản lịch sử quá hạn {} ngày", punches, history, days);
        return punches;
    }
}

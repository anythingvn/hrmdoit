package vn.doit.hrm.service.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.AttendancePunch;
import vn.doit.hrm.domain.CompanySetting;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.AttendancePunchKind;
import vn.doit.hrm.domain.enumeration.AttendancePunchMethod;
import vn.doit.hrm.domain.enumeration.AttendancePunchSource;
import vn.doit.hrm.domain.enumeration.AttendancePunchStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AttendancePunchRepository;
import vn.doit.hrm.repository.CompanySettingRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.CompanySettingService;
import vn.doit.hrm.service.leave.LeaveFixtures;

/** Xóa tọa độ, sai số và IP của lần chấm quá hạn lưu; giữ nguyên kết quả chấm. Hôm nay 23/10/2026, giữ 90 ngày. */
@IntegrationTest
@Transactional
@Import(PunchLocationPurgeJobIT.FixedClockConfig.class)
class PunchLocationPurgeJobIT {

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-23T01:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private PunchLocationPurgeJob job;

    @Autowired
    private AttendancePunchRepository punchRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private WorkSiteRepository workSiteRepository;

    @Autowired
    private CompanySettingRepository settingRepository;

    @Autowired
    private CompanySettingService settingService;

    @Autowired
    private JdbcTemplate jdbc;

    private AttendancePunch punch(Employee who, WorkSite site, LocalDate day) {
        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(who.getId());
        p.setWorkDay(day);
        p.setPunchTime(LocalTime.of(8, 0));
        p.setKind(AttendancePunchKind.VAO);
        p.setSource(AttendancePunchSource.APP);
        p.setStatus(AttendancePunchStatus.DA_DUYET);
        p.setMethod(AttendancePunchMethod.GPS);
        p.setSiteId(site.getId());
        p.setDistanceM(42);
        p.setReviewReason("Ngoài bán kính");
        p.setLatitude(new BigDecimal("21.027900"));
        p.setLongitude(new BigDecimal("105.836000"));
        p.setAccuracyM(new BigDecimal("12.5"));
        p.setClientIp("203.0.113.77");
        return punchRepository.saveAndFlush(p);
    }

    private long audit(AttendancePunch p, long rev) {
        jdbc.update("insert into audit_revision(id, revision_at) values (?, now())", rev);
        jdbc.update(
            "insert into attendance_punch_aud(id, rev, revtype, employee_id, work_day, status, method, latitude, longitude, accuracy_m, client_ip)" +
            " values (?, ?, 0, ?, ?, 'DA_DUYET', 'GPS', 21.0279, 105.836, 12.5, '203.0.113.77')",
            p.getId(),
            rev,
            p.getEmployeeId(),
            java.sql.Date.valueOf(p.getWorkDay())
        );
        return rev;
    }

    private Map<String, Object> row(AttendancePunch p) {
        return jdbc.queryForMap("select * from attendance_punch where id = ?", p.getId());
    }

    private Map<String, Object> auditRow(AttendancePunch p, long rev) {
        return jdbc.queryForMap("select * from attendance_punch_aud where id = ? and rev = ?", p.getId(), rev);
    }

    private void retention(int days) {
        CompanySetting s = settingService.current();
        s.setLocationRetentionDays(days);
        settingRepository.saveAndFlush(s);
    }

    @Test
    void purgesOnlyExpiredAndKeepsResult() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Employee who = employeeRepository.saveAndFlush(
            LeaveFixtures.employee(employeeRepository, "PG" + suffix, PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2))
        );
        WorkSite site = workSiteRepository.saveAndFlush(
            new WorkSite().name("Văn phòng PG " + suffix).type(WorkLocationType.VAN_PHONG).active(true)
        );
        retention(90);
        AttendancePunch old = punch(who, site, LocalDate.of(2026, 7, 24));
        AttendancePunch edge = punch(who, site, LocalDate.of(2026, 7, 25));
        long revId = jdbc.queryForObject("select coalesce(max(id), 0) + 1000000 from audit_revision", Long.class);
        audit(old, revId);
        audit(edge, revId + 1);
        int before = jdbc.queryForObject(
            "select count(*) from attendance_punch where latitude is not null and work_day < date '2026-07-25'",
            Integer.class
        );

        int purged = job.purge();

        assertThat(purged).isEqualTo(before).isGreaterThanOrEqualTo(1);
        Map<String, Object> o = row(old);
        assertThat(o.get("latitude")).isNull();
        assertThat(o.get("longitude")).isNull();
        assertThat(o.get("accuracy_m")).isNull();
        assertThat(o.get("client_ip")).isNull();
        assertThat(o.get("status")).isEqualTo("DA_DUYET");
        assertThat(o.get("method")).isEqualTo("GPS");
        assertThat(o.get("site_id")).isEqualTo(site.getId());
        assertThat(o.get("distance_m")).isEqualTo(42);
        assertThat(o.get("review_reason")).isEqualTo("Ngoài bán kính");
        Map<String, Object> e = row(edge);
        assertThat(e.get("latitude")).isNotNull();
        assertThat(e.get("client_ip")).isEqualTo("203.0.113.77");

        Map<String, Object> oa = auditRow(old, revId);
        assertThat(oa.get("latitude")).isNull();
        assertThat(oa.get("longitude")).isNull();
        assertThat(oa.get("accuracy_m")).isNull();
        assertThat(oa.get("client_ip")).isNull();
        assertThat(oa.get("status")).isEqualTo("DA_DUYET");
        assertThat(auditRow(edge, revId + 1).get("latitude")).isNotNull();

        // chạy lại: không còn gì để xóa
        assertThat(job.purge()).isZero();

        // rút ngắn còn 30 ngày: lần chấm 25/07 cũng quá hạn
        retention(30);
        assertThat(job.purge()).isGreaterThanOrEqualTo(1);
        assertThat(row(edge).get("latitude")).isNull();
        assertThat(row(edge).get("client_ip")).isNull();
        assertThat(auditRow(edge, revId + 1).get("latitude")).isNull();
        assertThat(row(edge).get("status")).isEqualTo("DA_DUYET");
    }
}

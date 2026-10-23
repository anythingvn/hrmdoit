package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.service.leave.LeaveFixtures;

/**
 * Hai (nhiều) lần bấm cùng loại gửi cùng lúc (bấm đúp, mạng chậm, gửi lại): chỉ ghi một lần chấm, mọi request trả cùng id. Pool 4 kết
 * nối để các request chạy song song thật (pool 1 của cấu hình test sẽ xếp hàng chúng); không dùng transaction của test: dữ liệu tự commit,
 * tự dọn.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(AttendanceSelfPunchIT.MutableClockConfig.class)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class AttendanceSelfPunchConcurrencyIT {

    private static final Instant START = Instant.parse("2026-10-23T01:03:00Z");
    private static final int PARALLEL = 4;
    private static final int ROUNDS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AttendanceSelfPunchIT.MutableClock clock;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private Employee me;
    private RequestPostProcessor ME;

    @BeforeEach
    void init() {
        clock.set(START);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String login = "sp-cc-" + suffix;
        ME = user(login).authorities(() -> "ROLE_NHAN_VIEN");
        Employee p = LeaveFixtures.employee(employeeRepository, "SC" + suffix, PersonType.NHAN_VIEN, LocalDate.of(2024, 1, 2));
        p.setUser(LeaveFixtures.user(userRepository, login));
        me = employeeRepository.saveAndFlush(p);
    }

    @AfterEach
    void cleanup() {
        Long eid = me.getId();
        Long uid = me.getUser().getId();
        tx.executeWithoutResult(s -> {
            jdbc.update("delete from attendance_punch where employee_id = ?", eid);
            jdbc.update("delete from attendance_consent where employee_id = ?", eid);
            jdbc.update("delete from leave_ledger_entry where employee_id = ?", eid);
            jdbc.update("delete from employment_period where employee_id = ?", eid);
            jdbc.update("delete from employee where id = ?", eid);
            jdbc.update("delete from jhi_user where id = ?", uid);
        });
    }

    @Test
    void concurrentTapsStoreOnce() throws Exception {
        mockMvc.perform(post("/api/attendance/consent").with(ME)).andExpect(status().isNoContent());
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String kind = round % 2 == 0 ? "VAO" : "RA";
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Long>> results = new ArrayList<>();
                for (int i = 0; i < PARALLEL; i++) {
                    results.add(
                        pool.submit(() -> {
                            go.await();
                            String body = mockMvc
                                .perform(
                                    post("/api/attendance/punches")
                                        .with(ME)
                                        .with(r -> {
                                            r.setRemoteAddr("198.51.100.5");
                                            return r;
                                        })
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"kind\":\"" + kind + "\"}")
                                )
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                            return om.readTree(body).get("id").asLong();
                        })
                    );
                }
                go.countDown();
                Set<Long> ids = new HashSet<>();
                for (Future<Long> f : results) {
                    ids.add(f.get(60, TimeUnit.SECONDS));
                }
                assertThat(ids).as("vòng %d: mọi request trả cùng một lần chấm", round).hasSize(1);
                assertThat(rows(kind)).as("vòng %d: chỉ một dòng %s", round, kind).isEqualTo(round / 2 + 1);
                clock.plusSeconds(61);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private long rows(String kind) {
        Long n = jdbc.queryForObject(
            "select count(*) from attendance_punch where employee_id = ? and kind = ? and source = 'APP'",
            Long.class,
            me.getId(),
            kind
        );
        return n == null ? 0 : n;
    }
}

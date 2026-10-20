package vn.doit.hrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.repository.UserRepository;

class LoginAttemptServiceTest {

    /** Đồng hồ chỉnh được để thử hết cửa sổ 15 phút. */
    static final class MutableClock extends Clock {

        volatile Instant now = Instant.parse("2026-10-20T01:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private final MutableClock clock = new MutableClock();
    private final UserRepository userRepository = mock(UserRepository.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<UserRepository> provider = mock(ObjectProvider.class);

    private final LoginAttemptService service;

    private ExecutorService executor;

    LoginAttemptServiceTest() {
        when(provider.getIfAvailable()).thenReturn(userRepository);
        when(userRepository.findOneByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        service = new LoginAttemptService(clock, provider);
    }

    @AfterEach
    void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void fail(String login, String ip, int times) {
        for (int i = 0; i < times; i++) {
            LoginAttemptService.Attempt a = service.tryAcquire(login, ip);
            assertThat(a.allowed())
                .as("lần thử %d của %s", i + 1, login)
                .isTrue();
            service.recordFailure(a);
            clock.advance(Duration.ofSeconds(10));
        }
    }

    private boolean allowed(String login, String ip) {
        LoginAttemptService.Attempt a = service.tryAcquire(login, ip);
        if (a.allowed()) {
            service.release(a);
        }
        return a.allowed();
    }

    @Test
    void renamedLoginKeepsItsBlock() {
        fail("079100000001", "1.1.1.1", 5);
        fail("079100000001", "2.2.2.2", 2);
        service.moveCounters("079100000001", "079100000099");
        assertThat(allowed("079100000099", "1.1.1.1")).as("tên mới vẫn bị chặn").isFalse();
        assertThat(allowed("079100000001", "1.1.1.1")).as("tên cũ không còn bộ đếm").isTrue();
        // IP khác: 2 lần sai cũ + 3 lần nữa thì bị chặn.
        fail("079100000099", "2.2.2.2", 3);
        assertThat(allowed("079100000099", "2.2.2.2")).isFalse();
    }

    @Test
    void movingMergesWithExistingCounter() {
        fail("cu", "1.1.1.1", 3);
        fail("moi", "1.1.1.1", 2);
        service.moveCounters("cu", "moi");
        assertThat(allowed("moi", "1.1.1.1")).isFalse();
    }

    @Test
    void fourFailuresNotBlocked() {
        fail("an", "1.1.1.1", 4);
        assertThat(allowed("an", "1.1.1.1")).isTrue();
    }

    @Test
    void fiveFailuresBlockedUntilOldestLeavesWindow() {
        fail("an", "1.1.1.1", 5);
        // Lần sai đầu lúc 01:00:00, bây giờ 01:00:50 -> còn 14 phút 10 giây.
        assertThat(service.tryAcquire("an", "1.1.1.1").blockedFor()).isEqualTo(Duration.ofMinutes(14).plusSeconds(10));
        clock.advance(Duration.ofMinutes(14).plusSeconds(9));
        assertThat(allowed("an", "1.1.1.1")).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(allowed("an", "1.1.1.1")).isTrue();
    }

    @Test
    void keyedByLoginCaseInsensitiveAndIp() {
        fail("An", "1.1.1.1", 5);
        assertThat(allowed("an", "1.1.1.1")).isFalse();
        assertThat(allowed(" AN ", "1.1.1.1")).isFalse();
        assertThat(allowed("an", "2.2.2.2")).isTrue();
        assertThat(allowed("binh", "1.1.1.1")).isTrue();
    }

    @Test
    void emailAliasCountsWithLogin() {
        User user = new User();
        user.setLogin("mai.nguyen");
        when(userRepository.findOneByEmailIgnoreCase("mai@congty.vn")).thenReturn(Optional.of(user));
        fail("mai.nguyen", "1.1.1.1", 3);
        fail("Mai@CongTy.vn", "1.1.1.1", 2);
        assertThat(allowed("mai.nguyen", "1.1.1.1")).isFalse();
        assertThat(allowed("mai@congty.vn", "1.1.1.1")).isFalse();
    }

    @Test
    void successResetsCounter() {
        fail("an", "1.1.1.1", 4);
        LoginAttemptService.Attempt ok = service.tryAcquire("an", "1.1.1.1");
        service.recordSuccess(ok);
        fail("an", "1.1.1.1", 4);
        assertThat(allowed("an", "1.1.1.1")).isTrue();
    }

    @Test
    void failuresOlderThanWindowDoNotCount() {
        fail("an", "1.1.1.1", 4);
        clock.advance(Duration.ofMinutes(15));
        fail("an", "1.1.1.1", 1);
        assertThat(allowed("an", "1.1.1.1")).isTrue();
    }

    @Test
    void inFlightAttemptsAreReserved() {
        List<LoginAttemptService.Attempt> held = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LoginAttemptService.Attempt a = service.tryAcquire("an", "1.1.1.1");
            assertThat(a.allowed()).isTrue();
            held.add(a);
        }
        // Năm lần đang kiểm tra mật khẩu, lần thứ sáu bị từ chối ngay.
        assertThat(service.tryAcquire("an", "1.1.1.1").allowed()).isFalse();
        service.release(held.remove(0));
        assertThat(allowed("an", "1.1.1.1")).isTrue();
        held.forEach(service::recordFailure);
        assertThat(allowed("an", "1.1.1.1")).isTrue(); // 4 lần sai
    }

    @Test
    void concurrentAttemptsAtMostFiveGetThrough() throws Exception {
        executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(
                executor.submit(() -> {
                    start.await();
                    return service.tryAcquire("an", "1.1.1.1").allowed();
                })
            );
        }
        start.countDown();
        int allowed = 0;
        for (Future<Boolean> f : results) {
            if (f.get(10, TimeUnit.SECONDS)) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(5);
    }

    @Test
    void perIpCapAcrossLogins() {
        for (int i = 0; i < LoginAttemptService.MAX_IP_FAILURES; i++) {
            LoginAttemptService.Attempt a = service.tryAcquire("u" + i, "9.9.9.9");
            assertThat(a.allowed()).isTrue();
            service.recordFailure(a);
        }
        LoginAttemptService.Attempt blocked = service.tryAcquire("tai-khoan-moi", "9.9.9.9");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.key()).isNull();
        assertThat(allowed("tai-khoan-moi", "8.8.8.8")).isTrue();
        clock.advance(LoginAttemptService.WINDOW);
        assertThat(allowed("tai-khoan-moi", "9.9.9.9")).isTrue();
    }

    @Test
    void evictionKeepsRecentlyBlockedKeyAndIpCounter() {
        fail("nan-nhan", "7.7.7.7", 5);
        for (int i = 0; i < LoginAttemptService.MAX_IP_FAILURES; i++) {
            LoginAttemptService.Attempt a = service.tryAcquire("ip-cap-" + i, "6.6.6.6");
            service.recordFailure(a);
        }
        // Làm đầy bộ nhớ bằng tên đăng nhập ngẫu nhiên từ nhiều IP (mỗi IP dưới giới hạn theo IP) với hoạt động cũ hơn.
        clock.now = clock.now.minus(Duration.ofMinutes(5));
        for (int i = 0; i < LoginAttemptService.MAX_TRACKED_KEYS + 50; i++) {
            LoginAttemptService.Attempt a = service.tryAcquire("u" + i, "10.0." + i / 40 + "." + (i % 40));
            if (a.allowed()) {
                service.recordFailure(a);
            }
        }
        clock.advance(Duration.ofMinutes(5));
        assertThat(allowed("nan-nhan", "7.7.7.7")).isFalse();
        // Bộ đếm theo IP không bị bỏ dù bộ nhớ đầy.
        assertThat(allowed("bat-ky", "6.6.6.6")).isFalse();
    }

    @Test
    void adminUnlockClearsAllIpsOfLogin() {
        fail("an", "1.1.1.1", 5);
        fail("an", "2.2.2.2", 5);
        assertThat(service.unlock("AN")).isEqualTo(2);
        assertThat(allowed("an", "1.1.1.1")).isTrue();
        assertThat(allowed("an", "2.2.2.2")).isTrue();
    }
}

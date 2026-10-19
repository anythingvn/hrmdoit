package vn.doit.hrm.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    /** Đồng hồ chỉnh được để thử hết cửa sổ 15 phút. */
    static final class MutableClock extends Clock {

        Instant now = Instant.parse("2026-10-20T01:00:00Z");

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
    private final LoginAttemptService service = new LoginAttemptService(clock);

    private void fail(String login, String ip, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(login, ip);
            clock.advance(Duration.ofSeconds(10));
        }
    }

    @Test
    void fourFailuresNotBlocked() {
        fail("an", "1.1.1.1", 4);
        assertThat(service.blockedFor("an", "1.1.1.1")).isEmpty();
    }

    @Test
    void fiveFailuresBlockedUntilOldestLeavesWindow() {
        fail("an", "1.1.1.1", 5);
        // Lần sai đầu lúc 01:00:00, bây giờ 01:00:50 -> còn 14 phút 10 giây.
        assertThat(service.blockedFor("an", "1.1.1.1")).contains(Duration.ofMinutes(14).plusSeconds(10));
        clock.advance(Duration.ofMinutes(14).plusSeconds(9));
        assertThat(service.blockedFor("an", "1.1.1.1")).isPresent();
        clock.advance(Duration.ofSeconds(1));
        assertThat(service.blockedFor("an", "1.1.1.1")).isEmpty();
    }

    @Test
    void keyedByLoginCaseInsensitiveAndIp() {
        fail("An", "1.1.1.1", 5);
        assertThat(service.blockedFor("an", "1.1.1.1")).isPresent();
        assertThat(service.blockedFor(" AN ", "1.1.1.1")).isPresent();
        assertThat(service.blockedFor("an", "2.2.2.2")).isEmpty();
        assertThat(service.blockedFor("binh", "1.1.1.1")).isEmpty();
    }

    @Test
    void successResetsCounter() {
        fail("an", "1.1.1.1", 4);
        service.recordSuccess("an", "1.1.1.1");
        fail("an", "1.1.1.1", 4);
        assertThat(service.blockedFor("an", "1.1.1.1")).isEmpty();
    }

    @Test
    void failuresOlderThanWindowDoNotCount() {
        fail("an", "1.1.1.1", 4);
        clock.advance(Duration.ofMinutes(15));
        fail("an", "1.1.1.1", 1);
        assertThat(service.blockedFor("an", "1.1.1.1")).isEmpty();
    }

    @Test
    void memoryBounded() {
        for (int i = 0; i < LoginAttemptService.MAX_TRACKED_KEYS + 50; i++) {
            service.recordFailure("u" + i, "1.1.1.1");
        }
        fail("an", "1.1.1.1", 5);
        assertThat(service.blockedFor("an", "1.1.1.1")).isPresent();
    }
}

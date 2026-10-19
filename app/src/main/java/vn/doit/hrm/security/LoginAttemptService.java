package vn.doit.hrm.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Chặn dò mật khẩu: sai {@value #MAX_FAILURES} lần trong {@link #WINDOW} theo cặp (tên đăng nhập, IP) thì từ chối các lần
 * đăng nhập tiếp theo (kể cả đúng mật khẩu) cho đến khi lần sai cũ nhất ra khỏi cửa sổ. Đăng nhập thành công xóa bộ đếm.
 *
 * <p>Lưu trong bộ nhớ: đủ cho triển khai một instance (docker-compose hiện tại); khởi động lại app thì bộ đếm về 0. Chạy nhiều
 * instance thì cần chuyển sang kho dùng chung (DB/Redis). IP lấy từ {@code request.getRemoteAddr()}, Tomcat đã thay bằng IP
 * thật từ {@code X-Forwarded-For} khi request đến từ proxy tin cậy (Caddy trong mạng nội bộ Docker), xem
 * {@code server.forward-headers-strategy}.
 */
@Component
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    /** Giới hạn số khóa giữ trong bộ nhớ để không bị làm đầy bộ nhớ bằng tên đăng nhập ngẫu nhiên. */
    static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    private static String key(String login, String ip) {
        String normalized = login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
        return normalized + '\u0000' + (ip == null ? "" : ip);
    }

    /** Nếu đang bị chặn: thời gian còn phải chờ; ngược lại empty. */
    public Optional<Duration> blockedFor(String login, String ip) {
        Instant now = clock.instant();
        Deque<Instant> list = failures.get(key(login, ip));
        if (list == null) {
            return Optional.empty();
        }
        synchronized (list) {
            prune(list, now);
            if (list.size() < MAX_FAILURES) {
                return Optional.empty();
            }
            // Mở lại khi lần sai thứ (size - MAX_FAILURES + 1) tính từ cũ nhất ra khỏi cửa sổ.
            Instant unblockAt = list
                .stream()
                .skip(list.size() - (long) MAX_FAILURES)
                .findFirst()
                .orElseThrow()
                .plus(WINDOW);
            Duration wait = Duration.between(now, unblockAt);
            return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
        }
    }

    public void recordFailure(String login, String ip) {
        Instant now = clock.instant();
        if (failures.size() >= MAX_TRACKED_KEYS) {
            evictExpired(now);
        }
        Deque<Instant> list = failures.computeIfAbsent(key(login, ip), k -> new ArrayDeque<>());
        synchronized (list) {
            prune(list, now);
            list.addLast(now);
            while (list.size() > MAX_FAILURES) {
                list.removeFirst();
            }
        }
    }

    public void recordSuccess(String login, String ip) {
        failures.remove(key(login, ip));
    }

    /** Xóa toàn bộ bộ đếm (dùng trong test). */
    public void clear() {
        failures.clear();
    }

    private void evictExpired(Instant now) {
        failures.entrySet().removeIf(e -> {
            Deque<Instant> list = e.getValue();
            synchronized (list) {
                prune(list, now);
                return list.isEmpty();
            }
        });
        if (failures.size() >= MAX_TRACKED_KEYS) {
            // Vẫn quá nhiều khóa còn hiệu lực (đang bị dò hàng loạt): bỏ bớt để giữ bộ nhớ ổn định.
            failures
                .keySet()
                .stream()
                .limit(MAX_TRACKED_KEYS / 10)
                .toList()
                .forEach(failures::remove);
        }
    }

    private static void prune(Deque<Instant> list, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!list.isEmpty() && !list.peekFirst().isAfter(cutoff)) {
            list.removeFirst();
        }
    }
}

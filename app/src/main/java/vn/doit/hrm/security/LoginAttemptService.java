package vn.doit.hrm.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.repository.UserRepository;

/**
 * Chặn dò mật khẩu.
 *
 * <ul>
 *   <li>Theo tài khoản + IP: sai {@value #MAX_FAILURES} lần trong {@link #WINDOW} thì từ chối các lần tiếp theo (kể cả đúng mật
 *   khẩu) cho đến khi lần sai cũ nhất ra khỏi cửa sổ. Đăng nhập thành công xóa bộ đếm. Mỗi lần thử phải "giữ chỗ"
 *   ({@link #tryAcquire}) trước khi kiểm tra mật khẩu, nên gửi song song nhiều request cũng chỉ tối đa {@value #MAX_FAILURES}
 *   lần được kiểm tra. Đăng nhập bằng email được quy về tên đăng nhập của tài khoản (không nhân đôi số lần thử).</li>
 *   <li>Theo IP (mọi tài khoản): sai {@value #MAX_IP_FAILURES} lần trong {@link #WINDOW} thì chặn cả IP. Bộ đếm theo IP không
 *   bao giờ bị bỏ khi còn hiệu lực, nên không thể "xóa dấu" bằng cách làm đầy bộ nhớ.</li>
 * </ul>
 *
 * <p>Lưu trong bộ nhớ: đủ cho triển khai một instance (docker-compose hiện tại); khởi động lại app thì bộ đếm về 0. Nhiều người
 * dùng chung một IP (NAT văn phòng) cùng chịu giới hạn theo IP; Quản trị mở khóa một tài khoản bằng
 * {@code DELETE /api/admin/login-attempts/{login}}. IP lấy từ {@code request.getRemoteAddr()}, Tomcat đã thay bằng IP thật từ
 * {@code X-Forwarded-For} khi request đến từ proxy tin cậy (xem {@code server.forward-headers-strategy}).
 */
@Component
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final int MAX_IP_FAILURES = 50;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    /** Giới hạn số khóa (tài khoản + IP) giữ trong bộ nhớ. */
    static final int MAX_TRACKED_KEYS = 10_000;

    /** Kết quả {@link #tryAcquire}: được thử (giữ một chỗ) hoặc bị chặn kèm thời gian chờ. */
    public record Attempt(String key, String ip, Duration blockedFor) {
        public boolean allowed() {
            return blockedFor == null;
        }
    }

    private static final class Entry {

        final Deque<Instant> failures = new ArrayDeque<>();
        int inFlight;
        Instant lastActivity = Instant.MIN;
    }

    private final Clock clock;
    private final ObjectProvider<UserRepository> userRepository;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> ipFailures = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock, ObjectProvider<UserRepository> userRepository) {
        this.clock = clock;
        this.userRepository = userRepository;
    }

    /** Tên đăng nhập chuẩn hóa: chữ thường; email thì đổi sang tên đăng nhập của tài khoản có email đó. */
    String canonicalLogin(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("@")) {
            UserRepository repo = userRepository.getIfAvailable();
            if (repo != null) {
                return repo
                    .findOneByEmailIgnoreCase(normalized)
                    .map(User::getLogin)
                    .map(l -> l.toLowerCase(Locale.ROOT))
                    .orElse(normalized);
            }
        }
        return normalized;
    }

    private static String key(String login, String ip) {
        return login + '\u0000' + (ip == null ? "" : ip);
    }

    /**
     * Giữ chỗ cho một lần thử đăng nhập. Nếu được phép, PHẢI gọi đúng một trong {@link #recordFailure}, {@link #recordSuccess}
     * hoặc {@link #release} với kết quả trả về.
     */
    public Attempt tryAcquire(String identifier, String ip) {
        Instant now = clock.instant();
        String ipKey = ip == null ? "" : ip;
        Deque<Instant> ipList = ipFailures.computeIfAbsent(ipKey, k -> new ArrayDeque<>());
        synchronized (ipList) {
            prune(ipList, now);
            if (ipList.size() >= MAX_IP_FAILURES) {
                Instant unblockAt = ipList
                    .stream()
                    .skip(ipList.size() - (long) MAX_IP_FAILURES)
                    .findFirst()
                    .orElseThrow()
                    .plus(WINDOW);
                return new Attempt(null, ipKey, positive(Duration.between(now, unblockAt)));
            }
        }
        if (entries.size() >= MAX_TRACKED_KEYS) {
            evict(now);
        }
        String key = key(canonicalLogin(identifier), ipKey);
        while (true) {
            Entry entry = entries.computeIfAbsent(key, k -> new Entry());
            synchronized (entry) {
                if (entries.get(key) != entry) {
                    continue; // vừa bị bỏ khỏi map, lấy lại
                }
                prune(entry.failures, now);
                entry.lastActivity = now;
                if (entry.failures.size() + entry.inFlight >= MAX_FAILURES) {
                    if (entry.failures.size() >= MAX_FAILURES) {
                        Instant unblockAt = entry.failures
                            .stream()
                            .skip(entry.failures.size() - (long) MAX_FAILURES)
                            .findFirst()
                            .orElseThrow()
                            .plus(WINDOW);
                        return new Attempt(key, ipKey, positive(Duration.between(now, unblockAt)));
                    }
                    // Đang có đủ lần thử song song chưa xong: chờ chúng xong rồi thử lại.
                    return new Attempt(key, ipKey, Duration.ofSeconds(1));
                }
                entry.inFlight++;
                return new Attempt(key, ipKey, null);
            }
        }
    }

    public void recordFailure(Attempt attempt) {
        Instant now = clock.instant();
        Deque<Instant> ipList = ipFailures.computeIfAbsent(attempt.ip(), k -> new ArrayDeque<>());
        synchronized (ipList) {
            prune(ipList, now);
            ipList.addLast(now);
        }
        Entry entry = entries.computeIfAbsent(attempt.key(), k -> new Entry());
        synchronized (entry) {
            entry.inFlight = Math.max(0, entry.inFlight - 1);
            prune(entry.failures, now);
            entry.failures.addLast(now);
            entry.lastActivity = now;
        }
    }

    public void recordSuccess(Attempt attempt) {
        Entry entry = entries.get(attempt.key());
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            entry.inFlight = Math.max(0, entry.inFlight - 1);
            entry.failures.clear();
            if (entry.inFlight == 0) {
                entries.remove(attempt.key(), entry);
            }
        }
    }

    /** Trả chỗ khi lần thử kết thúc bất thường (lỗi hệ thống), không tính là sai. */
    public void release(Attempt attempt) {
        Entry entry = entries.get(attempt.key());
        if (entry != null) {
            synchronized (entry) {
                entry.inFlight = Math.max(0, entry.inFlight - 1);
            }
        }
    }

    /** Quản trị mở khóa: xóa bộ đếm của tài khoản (mọi IP). Trả về số khóa đã xóa. */
    public int unlock(String login) {
        String prefix = canonicalLogin(login) + '\u0000';
        int[] removed = { 0 };
        entries.keySet().removeIf(k -> {
            if (k.startsWith(prefix)) {
                removed[0]++;
                return true;
            }
            return false;
        });
        return removed[0];
    }

    /**
     * Tên đăng nhập đổi (sửa CCCD trên hồ sơ, gắn tài khoản): chuyển bộ đếm đăng nhập sai sang tên mới (mọi IP), để đổi CCCD
     * không thành cách mở khóa tài khoản đang bị chặn. Gộp với bộ đếm sẵn có của tên mới (nếu có).
     */
    public void moveCounters(String oldLogin, String newLogin) {
        String oldPrefix = canonicalLogin(oldLogin) + '\u0000';
        String target = canonicalLogin(newLogin);
        if (oldPrefix.equals(target + '\u0000')) {
            return;
        }
        for (String key : java.util.List.copyOf(entries.keySet())) {
            if (!key.startsWith(oldPrefix)) {
                continue;
            }
            Entry old = entries.remove(key);
            if (old == null) {
                continue;
            }
            Entry moved = new Entry();
            synchronized (old) {
                moved.failures.addAll(old.failures);
                moved.lastActivity = old.lastActivity;
            }
            String newKey = target + '\u0000' + key.substring(oldPrefix.length());
            entries.merge(newKey, moved, (existing, add) -> {
                synchronized (existing) {
                    java.util.List<Instant> all = new java.util.ArrayList<>(existing.failures);
                    all.addAll(add.failures);
                    all.sort(Comparator.naturalOrder());
                    existing.failures.clear();
                    existing.failures.addAll(all);
                    if (add.lastActivity.isAfter(existing.lastActivity)) {
                        existing.lastActivity = add.lastActivity;
                    }
                }
                return existing;
            });
        }
    }

    /** Xóa toàn bộ bộ đếm (dùng trong test). */
    public void clear() {
        entries.clear();
        ipFailures.clear();
    }

    private void evict(Instant now) {
        entries.entrySet().removeIf(e -> {
            Entry entry = e.getValue();
            synchronized (entry) {
                prune(entry.failures, now);
                return entry.failures.isEmpty() && entry.inFlight == 0;
            }
        });
        ipFailures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
        if (entries.size() >= MAX_TRACKED_KEYS) {
            // Vẫn quá nhiều khóa còn hiệu lực: bỏ những khóa hoạt động cũ nhất (khóa bị chặn gần đây được giữ lại;
            // bộ đếm theo IP vẫn còn nên kẻ dò không thoát được giới hạn theo IP).
            entries
                .entrySet()
                .stream()
                .filter(e -> e.getValue().inFlight == 0)
                .sorted(Comparator.comparing(e -> e.getValue().lastActivity))
                .limit(MAX_TRACKED_KEYS / 10)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(entries::remove);
        }
    }

    private static Duration positive(Duration d) {
        return d.isNegative() || d.isZero() ? Duration.ofSeconds(1) : d;
    }

    private static void prune(Deque<Instant> list, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!list.isEmpty() && !list.peekFirst().isAfter(cutoff)) {
            list.removeFirst();
        }
    }
}

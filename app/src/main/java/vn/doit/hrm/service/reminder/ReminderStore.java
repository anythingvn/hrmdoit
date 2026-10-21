package vn.doit.hrm.service.reminder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hr.reminder.Reminder;
import vn.doit.hr.reminder.ReminderLog;

/**
 * Bảng nhắc việc: {@code reminder_log} (mốc đã gửi xong cho mọi người nhận), {@code reminder_delivery} (mốc đã gửi tới
 * từng email) và {@code reminder_run} (mỗi lần chạy). Mỗi lần ghi là một giao dịch riêng để lỗi gửi sau không xóa
 * kết quả gửi trước.
 */
@Repository
public class ReminderStore {

    public record Run(LocalDate runDate, Instant startedAt, boolean automatic, int recipients, int items, String error) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public ReminderStore(NamedParameterJdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** Nhật ký cho bộ nhắc, đọc một lần vào bộ nhớ. */
    public ReminderLog log() {
        Map<String, Set<Integer>> steps = new HashMap<>();
        jdbc.query("select event_key, step from reminder_log", rs -> {
            steps.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getInt(2));
        });
        return new ReminderLog() {
            @Override
            public OptionalInt smallestLeadSent(String eventKey) {
                return steps.getOrDefault(eventKey, Set.of()).stream().mapToInt(Integer::intValue).filter(s -> s >= 0).min();
            }

            @Override
            public boolean overdueSent(String eventKey, int overdueDay) {
                return steps.getOrDefault(eventKey, Set.of()).contains(-overdueDay);
            }
        };
    }

    /** Các cặp (mốc, email) đã gửi, trong số các lời nhắc cho trước. */
    public Set<String> deliveries(Collection<Reminder> reminders) {
        Set<String> out = new HashSet<>();
        if (reminders.isEmpty()) {
            return out;
        }
        List<String> keys = reminders.stream().map(Reminder::eventKey).distinct().toList();
        jdbc.query("select event_key, step, email from reminder_delivery where event_key in (:keys)", Map.of("keys", keys), rs -> {
            out.add(deliveryKey(rs.getString(1), rs.getInt(2), rs.getString(3)));
        });
        return out;
    }

    public static String deliveryKey(Reminder r, String email) {
        return deliveryKey(r.eventKey(), r.step(), email);
    }

    private static String deliveryKey(String eventKey, int step, String email) {
        return eventKey + "#" + step + "#" + email;
    }

    public void markDelivered(Collection<Reminder> reminders, String email, Instant at) {
        MapSqlParameterSource[] rows = reminders
            .stream()
            .map(r ->
                new MapSqlParameterSource()
                    .addValue("key", r.eventKey())
                    .addValue("step", r.step())
                    .addValue("email", email)
                    .addValue("at", Timestamp.from(at))
            )
            .toArray(MapSqlParameterSource[]::new);
        tx.executeWithoutResult(s ->
            jdbc.batchUpdate(
                "insert into reminder_delivery (event_key, step, email, sent_at) values (:key, :step, :email, :at) on conflict do nothing",
                rows
            )
        );
    }

    public void markLogged(Collection<Reminder> reminders, Instant at) {
        if (reminders.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] rows = reminders
            .stream()
            .map(r -> new MapSqlParameterSource().addValue("key", r.eventKey()).addValue("step", r.step()).addValue("at", Timestamp.from(at)))
            .toArray(MapSqlParameterSource[]::new);
        tx.executeWithoutResult(s ->
            jdbc.batchUpdate("insert into reminder_log (event_key, step, sent_at) values (:key, :step, :at) on conflict do nothing", rows)
        );
    }

    public void recordRun(Run run, Instant finishedAt) {
        tx.executeWithoutResult(s ->
            jdbc.update(
                "insert into reminder_run (run_date, started_at, finished_at, automatic, recipients, items, error) " +
                "values (:day, :started, :finished, :automatic, :recipients, :items, :error)",
                new MapSqlParameterSource()
                    .addValue("day", run.runDate())
                    .addValue("started", Timestamp.from(run.startedAt()))
                    .addValue("finished", Timestamp.from(finishedAt))
                    .addValue("automatic", run.automatic())
                    .addValue("recipients", run.recipients())
                    .addValue("items", run.items())
                    .addValue("error", run.error())
            )
        );
    }

    /** Đã có lần chạy tự động không lỗi trong ngày. */
    public boolean ranSuccessfully(LocalDate day) {
        Integer n = jdbc.queryForObject(
            "select count(*) from reminder_run where run_date = :day and automatic and error is null",
            Map.of("day", day),
            Integer.class
        );
        return n != null && n > 0;
    }

    public Optional<Run> lastRun() {
        return jdbc
            .query(
                "select run_date, started_at, automatic, recipients, items, error from reminder_run order by started_at desc, id desc limit 1",
                (rs, i) ->
                    new Run(
                        rs.getObject(1, LocalDate.class),
                        rs.getTimestamp(2).toInstant(),
                        rs.getBoolean(3),
                        rs.getInt(4),
                        rs.getInt(5),
                        rs.getString(6)
                    )
            )
            .stream()
            .findFirst();
    }
}

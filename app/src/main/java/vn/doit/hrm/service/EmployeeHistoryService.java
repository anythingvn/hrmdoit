package vn.doit.hrm.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.service.dto.HistoryEntryDTO;

/**
 * Dựng nhật ký thay đổi của một hồ sơ từ các bảng _aud của Envers: so sánh từng bản ghi với phiên bản liền trước.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeHistoryService {

    static final String MASK = "***";

    /** Bảng _aud → tên thực thể; cột khóa ngoại về nhân viên (null = chính bảng employee, lọc theo id). */
    private static final Map<String, String> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put("employee", "Employee");
        TABLES.put("employment_contract", "EmploymentContract");
        TABLES.put("salary_change", "SalaryChange");
        TABLES.put("allowance", "Allowance");
        TABLES.put("work_assignment", "WorkAssignment");
        TABLES.put("employee_document", "EmployeeDocument");
    }

    private static final Set<String> SENSITIVE = Set.of(
        "id_number",
        "id_issue_date",
        "id_issue_place",
        "tax_code",
        "bank_account",
        "bank_name",
        "social_insurance_no"
    );
    private static final Set<String> TECHNICAL = Set.of("id", "rev", "revtype", "search_text", "employee_id", "revision_at", "login");
    private static final Set<String> RELATIONS = Set.of("department_id", "manager_id", "user_id", "site_id");

    private final JdbcTemplate jdbc;

    public EmployeeHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<HistoryEntryDTO> history(Long employeeId) {
        List<HistoryEntryDTO> result = new ArrayList<>();
        for (Map.Entry<String, String> table : TABLES.entrySet()) {
            String name = table.getKey();
            String filter = name.equals("employee") ? "a.id" : "a.employee_id";
            List<Map<String, Object>> rows = jdbc.queryForList(
                "select a.*, r.revision_at as revision_at, r.login as login from " +
                name +
                "_aud a join audit_revision r on r.id = a.rev where " +
                filter +
                " = ? order by a.id, a.rev",
                employeeId
            );
            Map<Object, Map<String, Object>> previousById = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                Map<String, Object> before = previousById.get(id);
                int type = ((Number) row.get("revtype")).intValue();
                List<HistoryEntryDTO.Change> changes =
                    type == 2 ? diff(before, null) : diff(type == 0 ? null : before, row);
                previousById.put(id, row);
                if (type == 1 && changes.isEmpty()) {
                    continue;
                }
                result.add(
                    new HistoryEntryDTO(
                        toInstant(row.get("revision_at")),
                        (String) row.get("login"),
                        table.getValue(),
                        type == 0 ? "TẠO" : type == 1 ? "SỬA" : "XÓA",
                        changes
                    )
                );
            }
        }
        result.sort(Comparator.comparing(HistoryEntryDTO::at, Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        return result;
    }

    private static List<HistoryEntryDTO.Change> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> source = after != null ? after : before;
        List<HistoryEntryDTO.Change> changes = new ArrayList<>();
        if (source == null) {
            return changes;
        }
        for (String column : source.keySet()) {
            if (TECHNICAL.contains(column)) {
                continue;
            }
            Object b = before == null ? null : before.get(column);
            Object a = after == null ? null : after.get(column);
            if (Objects.equals(text(b), text(a))) {
                continue;
            }
            boolean sensitive = SENSITIVE.contains(column);
            changes.add(new HistoryEntryDTO.Change(field(column), mask(sensitive, b), mask(sensitive, a)));
        }
        return changes;
    }

    private static String mask(boolean sensitive, Object value) {
        return sensitive && value != null ? MASK : text(value);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** snake_case → camelCase; cột khóa ngoại về thực thể liên quan bỏ hậu tố _id. */
    static String field(String column) {
        String base = RELATIONS.contains(column) ? column.substring(0, column.length() - 3) : column;
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : base.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toInstant(java.time.ZoneOffset.UTC);
        }
        return null;
    }
}

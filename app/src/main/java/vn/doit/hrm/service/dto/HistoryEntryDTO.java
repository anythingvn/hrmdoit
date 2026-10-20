package vn.doit.hrm.service.dto;

import java.time.Instant;
import java.util.List;

/**
 * Một mục trong nhật ký thay đổi hồ sơ.
 */
public record HistoryEntryDTO(Instant at, String login, String entity, String action, List<Change> changes) {
    public record Change(String field, String before, String after) {}
}

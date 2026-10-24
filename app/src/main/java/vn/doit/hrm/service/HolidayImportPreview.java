package vn.doit.hrm.service;

import java.time.LocalDate;
import java.util.List;

/** Kết quả so file Excel ngày lễ với ngày lễ hiện có (chỉ trong các năm có trong file). */
public record HolidayImportPreview(
    List<Integer> years,
    List<Item> added,
    List<Renamed> renamed,
    List<Item> removed,
    List<Item> unchanged,
    List<Problem> errors
) {
    public record Item(LocalDate day, String name) {}

    public record Renamed(LocalDate day, String oldName, String name) {}

    /** {@code row} là số dòng trong Excel (từ 1); 0 là lỗi không gắn với dòng nào. */
    public record Problem(int row, String key, String message) {}
}

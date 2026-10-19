package vn.doit.hr.assignment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Báo cáo trên nhiều nhân viên. */
public final class AssignmentReport {

    private AssignmentReport() {
    }

    /** Ai đang ở đâu trong ngày {@code date}: nơi → danh sách mã nhân viên. */
    public static Map<String, List<String>> whoIsWhere(Collection<AssignmentHistory> all, LocalDate date) {
        Map<String, List<String>> out = new TreeMap<>();
        for (AssignmentHistory h : all) {
            String place = h.locationOn(date).map(Assignment::placeLabel).orElse("(chưa có dữ liệu)");
            out.computeIfAbsent(place, k -> new ArrayList<>()).add(h.employeeCode());
        }
        out.values().forEach(l -> l.sort(null));
        return out;
    }
}

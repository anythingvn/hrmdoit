package vn.doit.hr.attendance;

import java.util.List;
import java.util.Map;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.WorkLocationType;

/** Tra các điểm chấm công hợp lệ cho một giai đoạn làm việc (lấy từ lịch sử vị trí). */
@FunctionalInterface
public interface SiteDirectory {

    List<WorkSite> sitesFor(Assignment assignment);

    /**
     * Văn phòng công ty dùng chung danh sách {@code officeSites}; nơi khác (khách hàng, nơi công tác)
     * tra theo đúng tên đơn vị đã ghi trong lịch sử vị trí.
     */
    static SiteDirectory of(List<WorkSite> officeSites, Map<String, List<WorkSite>> sitesByUnit) {
        return a -> a.type() == WorkLocationType.VAN_PHONG
                ? officeSites
                : sitesByUnit.getOrDefault(a.unit() == null ? "" : a.unit().trim(), List.of());
    }
}

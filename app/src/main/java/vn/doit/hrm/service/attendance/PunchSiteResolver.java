package vn.doit.hrm.service.attendance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.AssignmentHistory;
import vn.doit.hr.attendance.GeoPoint;
import vn.doit.hr.attendance.SiteDirectory;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.AssignmentMapping;
import vn.doit.hrm.service.WorkSiteService;

/**
 * Hôm nay nhân viên phải chấm ở đâu: nối lõi ({@link AssignmentHistory#locationOn}, {@link SiteDirectory}) với {@link WorkAssignment}
 * và {@link WorkSite} của ngày bấm.
 * <ul>
 *   <li>Công tác: nơi công tác (nơi làm việc gắn với đợt, nếu không thì tra theo tên đơn vị).</li>
 *   <li>Onsite ({@code KHACH_HANG}): nơi làm việc của khách hàng (gắn với đợt, hoặc tra theo tên đơn vị); không có thì không đối chiếu
 *       được (lõi trả chờ duyệt "chưa khai báo").</li>
 *   <li>Từ xa: không cần vị trí.</li>
 *   <li>Còn lại (văn phòng, hoặc ngày không có phân công nào – như khối "Văn phòng" của phần 1): văn phòng gắn với phân công, nếu không
 *       có thì mọi văn phòng đang dùng trong danh mục Cài đặt.</li>
 * </ul>
 */
@Component
@Transactional(readOnly = true)
public class PunchSiteResolver {

    /**
     * Kết quả cho ngày {@code day}.
     *
     * @param history lịch sử để đưa vào {@code PunchValidator}; ngày không có phân công thì có thêm một dòng văn phòng chỉ phủ ngày đó
     * @param siteKind {@code VAN_PHONG|KHACH_HANG|CONG_TAC|TU_XA}
     * @param hoursSiteId khách hàng có bộ giờ riêng (theo cách phần 1 chia khối onsite), null = giờ công ty
     */
    public record Resolved(AssignmentHistory history, SiteDirectory directory, String siteKind, String siteName, Long hoursSiteId) {}

    private static final String OFFICE_LABEL = "Văn phòng";
    private static final String REMOTE_LABEL = "Làm việc từ xa";
    private static final String TRIP_LABEL = "Công tác";
    private static final String CUSTOMER_LABEL = "Khách hàng";

    private final WorkAssignmentRepository assignmentRepository;
    private final WorkSiteRepository siteRepository;

    public PunchSiteResolver(WorkAssignmentRepository assignmentRepository, WorkSiteRepository siteRepository) {
        this.assignmentRepository = assignmentRepository;
        this.siteRepository = siteRepository;
    }

    public Resolved resolve(Employee e, LocalDate day) {
        String code = e.getCode() == null ? "" : e.getCode();
        List<WorkAssignment> rows = assignmentRepository.findOverlapping(List.of(e.getId()), day, day);
        Map<Long, WorkAssignment> byId = new HashMap<>();
        List<Assignment> entries = new ArrayList<>();
        for (WorkAssignment w : rows) {
            if (w.getId() == null || w.getType() == null || w.getFromDate() == null) continue;
            byId.put(w.getId(), w);
            entries.add(AssignmentMapping.toCore(w, code));
        }
        AssignmentHistory history = AssignmentHistory.of(code, entries);
        Optional<Assignment> found = history.locationOn(day);
        if (found.isEmpty()) {
            entries.add(new Assignment(0L, code, vn.doit.hr.assignment.WorkLocationType.VAN_PHONG, null, null, day, day, null, null));
            history = AssignmentHistory.of(code, entries);
            found = history.locationOn(day);
        }
        Assignment where = found.orElseThrow();
        WorkAssignment row = byId.get(where.id());
        Lazy offices = new Lazy();
        SiteDirectory directory = a -> sitesFor(a, byId.get(a.id()), offices);

        WorkSite site = row == null ? null : row.getSite();
        String kind = where.type().name();
        String name;
        Long hoursSiteId = null;
        switch (where.type()) {
            case TU_XA -> name = REMOTE_LABEL;
            case VAN_PHONG -> {
                if (site != null) {
                    name = site.getName();
                } else {
                    List<WorkSite> o = offices.get();
                    name = o.size() == 1 ? o.get(0).getName() : OFFICE_LABEL;
                }
            }
            case KHACH_HANG -> {
                name = label(site, row, CUSTOMER_LABEL);
                hoursSiteId = site == null ? null : site.getId();
            }
            default -> name = label(site, row, TRIP_LABEL);
        }
        return new Resolved(history, directory, kind, name, hoursSiteId);
    }

    private List<vn.doit.hr.attendance.WorkSite> sitesFor(Assignment a, WorkAssignment row, Lazy offices) {
        WorkSite assigned = row == null ? null : row.getSite();
        return switch (a.type()) {
            case TU_XA -> List.of();
            case VAN_PHONG -> assigned != null ? List.of(toCore(assigned)) : offices.get().stream().map(PunchSiteResolver::toCore).toList();
            case KHACH_HANG, CONG_TAC -> {
                if (assigned != null) yield List.of(toCore(assigned));
                if (a.unit() == null || a.unit().isBlank()) yield List.of();
                yield siteRepository.findByNameIgnoreCaseOrderByIdAsc(a.unit().trim()).stream().map(PunchSiteResolver::toCore).toList();
            }
        };
    }

    private static String label(WorkSite site, WorkAssignment row, String fallback) {
        if (site != null && site.getName() != null && !site.getName().isBlank()) return site.getName();
        if (row != null && row.getUnit() != null && !row.getUnit().isBlank()) return row.getUnit().trim();
        return fallback;
    }

    /** Điểm chấm công của lõi từ nơi làm việc: id là id bảng {@code work_site}; tọa độ chỉ khi có đủ vĩ độ và kinh độ. */
    static vn.doit.hr.attendance.WorkSite toCore(WorkSite s) {
        GeoPoint center =
            s.getLatitude() != null && s.getLongitude() != null
                ? new GeoPoint(s.getLatitude().doubleValue(), s.getLongitude().doubleValue())
                : null;
        int radius = s.getRadiusM() == null || s.getRadiusM() <= 0 ? vn.doit.hr.attendance.WorkSite.DEFAULT_RADIUS_METERS : s.getRadiusM();
        return new vn.doit.hr.attendance.WorkSite(
            String.valueOf(s.getId()),
            s.getName(),
            center,
            radius,
            null,
            new LinkedHashSet<>(WorkSiteService.parseOfficeIps(s.getOfficeIps()))
        );
    }

    /** Danh sách văn phòng đang dùng, chỉ nạp khi cần. */
    private final class Lazy {

        private List<WorkSite> value;

        List<WorkSite> get() {
            if (value == null) {
                value = siteRepository.findByTypeAndActiveTrueOrderByIdAsc(WorkLocationType.VAN_PHONG);
            }
            return value;
        }
    }
}

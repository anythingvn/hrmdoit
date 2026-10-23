package vn.doit.hrm.web.rest;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.attendance.AttendanceScheduleService;
import vn.doit.hrm.service.attendance.AttendanceScheduleService.OnsiteLeadDTO;
import vn.doit.hrm.service.attendance.AttendanceScheduleService.ScheduleDTO;

/** Cài đặt chấm công: bộ giờ làm của công ty và khách hàng, trưởng nhóm onsite. Quản trị, HR sửa; mọi người đăng nhập xem. */
@RestController
@RequestMapping("/api")
public class AttendanceSettingResource {

    private static final String EDIT = "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')";

    private final AttendanceScheduleService service;

    public AttendanceSettingResource(AttendanceScheduleService service) {
        this.service = service;
    }

    public record OnsiteLeadsVM(List<Long> employeeIds) {}

    /** Bộ giờ của công ty (không có {@code siteId}) hoặc của một khách hàng, mới nhất trước. */
    @GetMapping("/attendance/schedules")
    public List<ScheduleDTO> schedules(@RequestParam(required = false) Long siteId) {
        return service.list(siteId);
    }

    @PostMapping("/attendance/schedules")
    @PreAuthorize(EDIT)
    public ResponseEntity<ScheduleDTO> save(@RequestBody ScheduleDTO body) {
        ScheduleDTO saved = service.save(body);
        return ResponseEntity.created(URI.create("/api/attendance/schedules/" + saved.id())).body(saved);
    }

    @DeleteMapping("/attendance/schedules/{id}")
    @PreAuthorize(EDIT)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/work-sites/{id}/onsite-leads")
    public List<OnsiteLeadDTO> onsiteLeads(@PathVariable Long id) {
        return service.onsiteLeads(id);
    }

    @PutMapping("/work-sites/{id}/onsite-leads")
    @PreAuthorize(EDIT)
    public List<OnsiteLeadDTO> setOnsiteLeads(@PathVariable Long id, @RequestBody OnsiteLeadsVM body) {
        return service.setOnsiteLeads(id, body == null ? List.of() : body.employeeIds());
    }
}

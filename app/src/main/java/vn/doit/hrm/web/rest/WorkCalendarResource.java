package vn.doit.hrm.web.rest;

import java.net.URI;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.reminder.WorkCalendarService;
import vn.doit.hrm.service.reminder.WorkCalendarService.HolidayDTO;

/** Lịch làm việc trong tuần (Quản trị sửa) và ngày lễ theo năm (Quản trị, HR sửa; Kế toán xem). */
@RestController
@RequestMapping("/api")
public class WorkCalendarResource {

    private static final String VIEW =
        "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "')";
    private static final String EDIT_HOLIDAYS = "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')";

    private final WorkCalendarService service;

    public WorkCalendarResource(WorkCalendarService service) {
        this.service = service;
    }

    public record WorkingDaysVM(List<DayOfWeek> workingDays) {}

    @GetMapping("/work-calendar")
    @PreAuthorize(VIEW)
    public WorkingDaysVM get() {
        return new WorkingDaysVM(service.workingDays());
    }

    @PutMapping("/work-calendar")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public WorkingDaysVM set(@RequestBody WorkingDaysVM body) {
        return new WorkingDaysVM(service.setWorkingDays(body.workingDays()));
    }

    @GetMapping("/holidays")
    @PreAuthorize(VIEW)
    public List<HolidayDTO> holidays(@RequestParam int year) {
        return service.holidays(year);
    }

    @PostMapping("/holidays")
    @PreAuthorize(EDIT_HOLIDAYS)
    public ResponseEntity<HolidayDTO> add(@RequestBody HolidayDTO body) {
        HolidayDTO created = service.addHoliday(body);
        return ResponseEntity.created(URI.create("/api/holidays/" + created.id())).body(created);
    }

    @PutMapping("/holidays/{id}")
    @PreAuthorize(EDIT_HOLIDAYS)
    public HolidayDTO update(@PathVariable Long id, @RequestBody HolidayDTO body) {
        return service.updateHoliday(id, body);
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize(EDIT_HOLIDAYS)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/holidays/copy")
    @PreAuthorize(EDIT_HOLIDAYS)
    public List<HolidayDTO> copy(@RequestParam int fromYear, @RequestParam int toYear) {
        return service.copy(fromYear, toYear);
    }
}

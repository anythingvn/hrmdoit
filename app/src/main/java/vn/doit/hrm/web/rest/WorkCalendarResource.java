package vn.doit.hrm.web.rest;

import java.net.URI;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.HolidayExcelService;
import vn.doit.hrm.service.HolidayImportPreview;
import vn.doit.hrm.service.reminder.WorkCalendarService;
import vn.doit.hrm.service.reminder.WorkCalendarService.HolidayDTO;

/** Lịch làm việc trong tuần (Quản trị sửa) và ngày lễ theo năm (Quản trị, HR sửa; Kế toán xem). */
@RestController
@RequestMapping("/api")
public class WorkCalendarResource {

    private static final String VIEW =
        "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "')";
    private static final String EDIT_HOLIDAYS = "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')";

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final WorkCalendarService service;
    private final HolidayExcelService excel;

    public WorkCalendarResource(WorkCalendarService service, HolidayExcelService excel) {
        this.service = service;
        this.excel = excel;
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

    @GetMapping("/holidays/export.xlsx")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> export(@RequestParam int year) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment("ngay-le-" + year + ".xlsx"))
            .contentType(XLSX)
            .body(excel.export(year));
    }

    /** Đọc file và so với ngày lễ hiện có, không ghi gì. */
    @PostMapping(value = "/holidays/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(EDIT_HOLIDAYS)
    public HolidayImportPreview importPreview(@RequestParam("file") MultipartFile file) {
        return excel.preview(file);
    }

    /** Tính lại từ file rồi mới ghi; còn lỗi thì 400. */
    @PostMapping(value = "/holidays/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(EDIT_HOLIDAYS)
    public HolidayImportPreview importApply(@RequestParam("file") MultipartFile file) {
        return excel.apply(file);
    }
}

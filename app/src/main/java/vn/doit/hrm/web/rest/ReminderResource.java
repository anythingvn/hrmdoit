package vn.doit.hrm.web.rest;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hr.reminder.ReminderType;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.reminder.ReminderService;
import vn.doit.hrm.service.reminder.dto.ReminderStatusDTO;
import vn.doit.hrm.service.reminder.dto.UpcomingDTO;

/** Việc sắp tới (Quản trị, HR, Kế toán, BGĐ) và email nhắc việc: tình trạng, gửi thử (Quản trị). */
@RestController
@RequestMapping("/api")
public class ReminderResource {

    private static final String ADMIN = "hasAuthority('" + AuthoritiesConstants.ADMIN + "')";

    private final ReminderService service;

    public ReminderResource(ReminderService service) {
        this.service = service;
    }

    @GetMapping("/reminders/upcoming")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')"
    )
    public List<UpcomingDTO> upcoming(@RequestParam(required = false) ReminderType type) {
        return service.upcoming(type);
    }

    @GetMapping("/admin/reminders/status")
    @PreAuthorize(ADMIN)
    public ReminderStatusDTO status() {
        return service.status();
    }

    @PostMapping("/admin/reminders/test-send")
    @PreAuthorize(ADMIN)
    public Map<String, String> testSend() {
        return Map.of("sentTo", service.testSend(SecurityUtils.getCurrentUserLogin().orElseThrow()));
    }
}

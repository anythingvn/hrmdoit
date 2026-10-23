package vn.doit.hrm.web.rest;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.service.leave.LeaveCalendarService;
import vn.doit.hrm.service.leave.dto.LeaveCalendarDTO;

/** Lịch nghỉ tháng. Mọi tài khoản đăng nhập gọi được; phạm vi xem tính theo dữ liệu ở service. */
@RestController
@RequestMapping("/api/leave")
public class LeaveCalendarResource {

    private final LeaveCalendarService service;

    public LeaveCalendarResource(LeaveCalendarService service) {
        this.service = service;
    }

    @GetMapping("/calendar")
    public LeaveCalendarDTO calendar(
        @RequestParam(name = "month") String month,
        @RequestParam(name = "departmentId", required = false) Long departmentId,
        @RequestParam(name = "type", required = false) String type
    ) {
        return service.month(month, departmentId, type);
    }

    /** Phòng ban để lọc lịch: chỉ phòng của những người mình được xem. */
    @GetMapping("/calendar/departments")
    public List<LeaveCalendarDTO.DepartmentOption> departments() {
        return service.departments();
    }
}

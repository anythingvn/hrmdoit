package vn.doit.hrm.web.rest;

import java.time.Clock;
import java.time.YearMonth;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.dashboard.DashboardService;
import vn.doit.hrm.service.dashboard.HrReportService;
import vn.doit.hrm.service.dashboard.dto.DashboardDTO;

/** Trang Tổng quan và báo cáo nhân sự Excel; chi phí lương chỉ Quản trị và Kế toán thấy. */
@RestController
@RequestMapping("/api")
public class DashboardResource {

    private static final String VIEW =
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')";
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DashboardService dashboardService;
    private final HrReportService reportService;
    private final Clock clock;

    public DashboardResource(DashboardService dashboardService, HrReportService reportService, Clock clock) {
        this.dashboardService = dashboardService;
        this.reportService = reportService;
        this.clock = clock;
    }

    @GetMapping("/dashboard")
    @PreAuthorize(VIEW)
    public DashboardDTO dashboard() {
        return dashboardService.dashboard(seesPayrollCost());
    }

    /** Mặc định 12 tháng gần nhất, gom chi phí theo phòng ban. */
    @GetMapping("/reports/hr.xlsx")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> report(
        @RequestParam(required = false) YearMonth from,
        @RequestParam(required = false) YearMonth to,
        @RequestParam(defaultValue = "DEPARTMENT") HrReportService.GroupBy groupBy
    ) {
        YearMonth end = to != null ? to : YearMonth.now(clock);
        YearMonth start = from != null ? from : end.minusMonths(11);
        byte[] body = reportService.excel(start, end, groupBy, seesPayrollCost());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment("Báo cáo nhân sự " + start + " – " + end + ".xlsx"))
            .contentType(XLSX)
            .body(body);
    }

    private static boolean seesPayrollCost() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.KE_TOAN);
    }
}

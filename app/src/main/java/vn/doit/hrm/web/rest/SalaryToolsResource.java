package vn.doit.hrm.web.rest;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hr.payroll.NetGrossConverter;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.security.AuthoritiesConstants;

/** Công cụ lương: gợi ý Gross từ Net và danh sách thời hạn hợp đồng. */
@RestController
@RequestMapping("/api/salary-tools")
@PreAuthorize(
    "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "', '" + AuthoritiesConstants.BGD + "')"
)
public class SalaryToolsResource {

    private final ApplicationProperties applicationProperties;

    public SalaryToolsResource(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @GetMapping("/gross-suggestion")
    public Map<String, Object> grossSuggestion(
        @RequestParam("net") long net,
        @RequestParam(value = "dependants", defaultValue = "0") int dependants,
        @RequestParam("month") String month
    ) {
        Map<String, Object> result = new HashMap<>();
        result.put(
            "gross",
            NetGrossConverter.suggestGross(BigDecimal.valueOf(net), Math.max(dependants, 0), YearMonth.parse(month)).map(BigDecimal::longValueExact).orElse(null)
        );
        return result;
    }

    @GetMapping("/contract-terms")
    public List<Integer> contractTerms() {
        return applicationProperties.getContract().getTermOptionsMonths();
    }
}

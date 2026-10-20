package vn.doit.hrm.web.rest;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hr.payroll.NetGrossConverter;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/** Công cụ lương: gợi ý Gross từ Net và danh sách thời hạn hợp đồng. */
@RestController
@RequestMapping("/api/salary-tools")
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
public class SalaryToolsResource {

    private final ApplicationProperties applicationProperties;

    public SalaryToolsResource(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    /**
     * Gợi ý Gross từ Net cho kỳ {@code month} (yyyy-MM): {@code {gross, estimated, rulesFrom, reason}}. Kỳ sau mốc luật đã
     * biết thì ước tính theo quy định mới nhất ({@code estimated = true}, {@code rulesFrom} = tháng hiệu lực của bộ quy tắc);
     * kỳ trước 07/2020 thì {@code gross = null} kèm {@code reason}.
     */
    @GetMapping("/gross-suggestion")
    public Map<String, Object> grossSuggestion(
        @RequestParam("net") long net,
        @RequestParam(value = "dependants", defaultValue = "0") int dependants,
        @RequestParam("month") String month
    ) {
        YearMonth period;
        try {
            period = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestAlertException("Kỳ lương không hợp lệ, cần dạng yyyy-MM", "salaryTools", "invalidmonth");
        }
        if (net < 0) {
            throw new BadRequestAlertException("Số tiền không được âm", "salaryTools", "negativeamount");
        }
        Map<String, Object> result = new HashMap<>();
        Optional<NetGrossConverter.GrossSuggestion> suggestion = NetGrossConverter.suggestGrossOrEstimate(
            BigDecimal.valueOf(net),
            Math.max(dependants, 0),
            period
        );
        result.put("gross", suggestion.map(s -> s.gross().longValueExact()).orElse(null));
        result.put("estimated", suggestion.map(NetGrossConverter.GrossSuggestion::estimated).orElse(false));
        suggestion.ifPresent(s -> result.put("rulesFrom", s.rulesEffectiveFrom().toString()));
        if (suggestion.isEmpty()) {
            result.put("reason", "Chưa có biểu thuế cho kỳ này");
        }
        return result;
    }

    @GetMapping("/contract-terms")
    public List<Integer> contractTerms() {
        return applicationProperties.getContract().getTermOptionsMonths();
    }
}

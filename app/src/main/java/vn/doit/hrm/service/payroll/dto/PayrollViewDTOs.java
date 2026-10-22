package vn.doit.hrm.service.payroll.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import vn.doit.hrm.domain.enumeration.PayrollPeriodAction;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.service.payroll.PayrollPeriodService;

/** Các bản ghi trả về khi xem bảng lương đã lưu. Tổng tiền tính từ phiên bản chính thức (đã duyệt, hoặc mới nhất). */
public final class PayrollViewDTOs {

    private PayrollViewDTOs() {}

    public record PeriodSummary(
        String period,
        PayrollPeriodStatus status,
        Integer officialVersion,
        Integer latestVersion,
        long headcount,
        BigDecimal totalNet,
        BigDecimal totalCost,
        String approvedBy,
        Instant approvedAt
    ) {}

    public record Version(
        int version,
        String importedBy,
        Instant importedAt,
        String fileName,
        long headcount,
        BigDecimal totalNet,
        BigDecimal totalCost,
        /** Bản chụp mốc luật lúc xác nhận (null nếu kỳ chưa có luật). */
        PayrollPeriodService.RulesSnapshot rules,
        /** Lỗi/cảnh báo lúc xác nhận (không chạy lại kiểm tra). */
        List<IssueDTO> issues
    ) {}

    public record Event(PayrollPeriodAction action, Integer version, String actor, Instant at, String reason) {}

    public record PeriodDetail(
        String period,
        PayrollPeriodStatus status,
        Integer officialVersion,
        Integer approvedVersion,
        String approvedBy,
        Instant approvedAt,
        List<Version> versions,
        List<Event> events
    ) {}

    public record Line(
        int excelRow,
        Long employeeId,
        String employeeCode,
        String employeeName,
        String fileCode,
        String fileName,
        String position,
        boolean mappedManually,
        Map<String, BigDecimal> amounts,
        BigDecimal thucNhan,
        BigDecimal tongChiPhi,
        BigDecimal thueTncn,
        BigDecimal tongBhNld,
        /** Hồ sơ của dòng đã lưu trữ: màn kỳ lương hiện nhãn "Lưu trữ". */
        boolean archived
    ) {}

    public record EmployeePayroll(
        String period,
        PayrollPeriodStatus status,
        int version,
        BigDecimal thucNhan,
        BigDecimal tongChiPhi,
        BigDecimal thueTncn,
        BigDecimal tongBhNld
    ) {}

    public record Field(String field, String label) {}
}

package vn.doit.hr.reminder;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dữ liệu một nhân viên mà bộ nhắc hạn cần. Lắp từ OrangeHRM (ngày sinh, trạng thái làm việc)
 * và từ HR Extension (lịch sử hợp đồng, thử việc, tăng lương).
 *
 * @param probationEnd ngày kết thúc thử việc; null nếu không thử việc
 * @param probationResultRecorded đã ghi nhận/thông báo kết quả thử việc chưa
 * @param lastSalaryChange ngày hiệu lực của lần điều chỉnh lương gần nhất (hoặc ngày vào làm)
 * @param salaryReviewCycleMonths chu kỳ xét tăng lương (vd 12); null = không áp dụng
 * @param nextSalaryReviewOverride ngày xét tăng lương ấn định riêng, ưu tiên hơn chu kỳ
 */
public record EmployeeSnapshot(
        String code,
        String fullName,
        boolean active,
        LocalDate birthDate,
        List<Contract> contracts,
        LocalDate probationEnd,
        boolean probationResultRecorded,
        LocalDate lastSalaryChange,
        Integer salaryReviewCycleMonths,
        LocalDate nextSalaryReviewOverride) {

    public EmployeeSnapshot {
        contracts = contracts == null ? List.of() : List.copyOf(contracts);
    }

    /** Hợp đồng lao động (không gồm HĐ thử việc). {@code end == null} là HĐ không xác định thời hạn. */
    public record Contract(String number, LocalDate start, LocalDate end) {
        public boolean fixedTerm() {
            return end != null;
        }
    }

    /** Hợp đồng ký gần nhất (theo ngày bắt đầu). */
    public Optional<Contract> latestContract() {
        return contracts.stream().max(Comparator.comparing(Contract::start));
    }

    /** Số HĐ xác định thời hạn ký liên tiếp tính từ HĐ gần nhất trở về trước. */
    public int consecutiveFixedTermContracts() {
        List<Contract> sorted = contracts.stream().sorted(Comparator.comparing(Contract::start).reversed()).toList();
        int n = 0;
        for (Contract c : sorted) {
            if (!c.fixedTerm()) {
                break;
            }
            n++;
        }
        return n;
    }

    public Optional<LocalDate> nextSalaryReview() {
        if (nextSalaryReviewOverride != null) {
            return Optional.of(nextSalaryReviewOverride);
        }
        if (lastSalaryChange == null || salaryReviewCycleMonths == null) {
            return Optional.empty();
        }
        return Optional.of(lastSalaryChange.plusMonths(salaryReviewCycleMonths));
    }
}

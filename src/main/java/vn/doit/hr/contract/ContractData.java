package vn.doit.hr.contract;

import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu để điền mẫu HĐLĐ. Phần lớn lấy từ hồ sơ nhân viên; phần hợp đồng do HR nhập khi tạo HĐ.
 *
 * @param termMonths thời hạn HĐ tính bằng tháng; null = HĐ không xác định thời hạn
 * @param allowances các khoản phụ cấp (tên + số tiền/tháng); rỗng → in "Không"
 */
public record ContractData(
        String contractNo,
        // Người lao động
        String fullName,
        String gender,
        LocalDate birthDate,
        String residence,
        String phone,
        String email,
        String education,
        String idNumber,
        LocalDate idIssueDate,
        String idIssuePlace,
        String bankAccount,
        String bankName,
        // Hợp đồng
        Integer termMonths,
        LocalDate startDate,
        String department,
        String position,
        long baseSalary,
        List<Allowance> allowances,
        String raisePolicy,
        String companyRepresentative) {

    public record Allowance(String name, long amountPerMonth) {
    }

    /** Ngày kết thúc: ngày bắt đầu + thời hạn − 1 ngày (HĐ 12 tháng từ 01/01/2026 kết thúc 31/12/2026). */
    public LocalDate endDate() {
        return termMonths == null ? null : startDate.plusMonths(termMonths).minusDays(1);
    }
}

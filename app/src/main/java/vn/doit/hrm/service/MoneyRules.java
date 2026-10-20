package vn.doit.hrm.service;

/**
 * Số tiền (lương, Gross, phụ cấp, lương đóng BHXH) không được âm. PUT/POST đã kiểm tra bằng {@code @Min(0)} trên DTO;
 * PATCH không chạy {@code @Valid} (cho phép bỏ trống trường) nên kiểm tra lại ở đây.
 */
final class MoneyRules {

    private MoneyRules() {}

    static void requireNonNegative(Long... amounts) {
        for (Long amount : amounts) {
            if (amount != null && amount < 0) {
                throw new EmployeeRuleException("Số tiền không được âm", "negativeamount");
            }
        }
    }
}

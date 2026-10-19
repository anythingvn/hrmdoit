package vn.doit.hrm.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.service.dto.EmploymentContractDTO;

/** Quy tắc thời hạn hợp đồng. Vi phạm ném {@link EmployeeRuleException} (resource đổi sang BadRequestAlertException). */
public final class ContractRules {

    private ContractRules() {}

    public static LocalDate defaultEndDate(ContractType type, Integer termMonths, LocalDate start) {
        if (type == ContractType.HDLD_KXDTH || termMonths == null || start == null) {
            return null;
        }
        return start.plusMonths(termMonths).minusDays(1);
    }

    public static void validate(EmploymentContractDTO c, List<Integer> termOptions) {
        validate(c.getType(), c.getTermMonths(), c.getStartDate(), c.getEndDate(), termOptions);
    }

    public static void validate(ContractType type, Integer termMonths, LocalDate start, LocalDate end, List<Integer> termOptions) {
        if (type == ContractType.HDLD_XDTH && termMonths != null && !termOptions.contains(termMonths)) {
            throw new EmployeeRuleException("Thời hạn hợp đồng không nằm trong danh sách cho phép", "badterm");
        }
        if (type == ContractType.HDLD_KXDTH && end != null) {
            throw new EmployeeRuleException("Hợp đồng không xác định thời hạn không có ngày kết thúc", "indefinitehasend");
        }
        if (start != null && end != null && end.isBefore(start)) {
            throw new EmployeeRuleException("Ngày kết thúc trước ngày bắt đầu", "endbeforestart");
        }
    }

    /** true khi 2 HĐ đã ký gần nhất đều là HĐLĐ xác định thời hạn (lần tiếp theo phải không xác định thời hạn). */
    public static boolean nextMustBeIndefinite(List<EmploymentContract> signed) {
        List<EmploymentContract> last = signed
            .stream()
            .filter(c -> c.getStatus() == ContractStatus.DA_KY || c.getStatus() == ContractStatus.DA_KET_THUC)
            .filter(c -> c.getStartDate() != null)
            .sorted(Comparator.comparing(EmploymentContract::getStartDate))
            .toList();
        if (last.size() < 2) {
            return false;
        }
        return (
            last.get(last.size() - 1).getType() == ContractType.HDLD_XDTH && last.get(last.size() - 2).getType() == ContractType.HDLD_XDTH
        );
    }
}

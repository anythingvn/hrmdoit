package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.service.dto.EmploymentContractDTO;

class ContractRulesTest {

    private static final List<Integer> OPTIONS = List.of(12, 24, 36);

    @Test
    void defaultEndDate() {
        assertThat(ContractRules.defaultEndDate(ContractType.HDLD_XDTH, 12, LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(ContractRules.defaultEndDate(ContractType.HDLD_XDTH, 24, LocalDate.of(2026, 10, 25))).isEqualTo(LocalDate.of(2028, 10, 24));
        assertThat(ContractRules.defaultEndDate(ContractType.HDLD_KXDTH, null, LocalDate.of(2026, 1, 1))).isNull();
        assertThat(ContractRules.defaultEndDate(ContractType.HDLD_KXDTH, 12, LocalDate.of(2026, 1, 1))).isNull();
        assertThat(ContractRules.defaultEndDate(ContractType.HDTV, null, LocalDate.of(2026, 1, 1))).isNull();
    }

    private static EmploymentContractDTO dto(ContractType type, Integer term, LocalDate start, LocalDate end) {
        EmploymentContractDTO d = new EmploymentContractDTO();
        d.setType(type);
        d.setTermMonths(term);
        d.setStartDate(start);
        d.setEndDate(end);
        return d;
    }

    @Test
    void validateRejectsBadTerm() {
        assertThatThrownBy(() -> ContractRules.validate(dto(ContractType.HDLD_XDTH, 18, LocalDate.of(2026, 1, 1), null), OPTIONS))
            .isInstanceOf(EmployeeRuleException.class)
            .extracting("errorKey")
            .isEqualTo("badterm");
    }

    @Test
    void validateRejectsIndefiniteWithEnd() {
        assertThatThrownBy(() -> ContractRules.validate(dto(ContractType.HDLD_KXDTH, null, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)), OPTIONS))
            .extracting("errorKey")
            .isEqualTo("indefinitehasend");
    }

    @Test
    void validateRejectsEndBeforeStart() {
        assertThatThrownBy(() -> ContractRules.validate(dto(ContractType.HDTV, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)), OPTIONS))
            .extracting("errorKey")
            .isEqualTo("endbeforestart");
    }

    @Test
    void validateAcceptsGood() {
        ContractRules.validate(dto(ContractType.HDLD_XDTH, 12, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), OPTIONS);
    }

    private static EmploymentContract c(ContractType t, ContractStatus s, LocalDate start) {
        return new EmploymentContract().type(t).status(s).startDate(start);
    }

    @Test
    void nextMustBeIndefinite() {
        assertThat(
            ContractRules.nextMustBeIndefinite(
                List.of(
                    c(ContractType.HDLD_XDTH, ContractStatus.DA_KET_THUC, LocalDate.of(2024, 1, 1)),
                    c(ContractType.HDLD_XDTH, ContractStatus.DA_KY, LocalDate.of(2025, 1, 1))
                )
            )
        ).isTrue();
        assertThat(
            ContractRules.nextMustBeIndefinite(
                List.of(
                    c(ContractType.HDTV, ContractStatus.DA_KET_THUC, LocalDate.of(2024, 1, 1)),
                    c(ContractType.HDLD_XDTH, ContractStatus.DA_KY, LocalDate.of(2025, 1, 1))
                )
            )
        ).isFalse();
    }
}

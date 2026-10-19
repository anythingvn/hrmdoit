package vn.doit.hrm.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class EmployeeCriteriaTest {

    @Test
    void newEmployeeCriteriaHasAllFiltersNullTest() {
        var employeeCriteria = new EmployeeCriteria();
        assertThat(employeeCriteria).is(criteriaFiltersAre(filter -> filter == null));
    }

    @Test
    void employeeCriteriaFluentMethodsCreatesFiltersTest() {
        var employeeCriteria = new EmployeeCriteria();

        setAllFilters(employeeCriteria);

        assertThat(employeeCriteria).is(criteriaFiltersAre(filter -> filter != null));
    }

    @Test
    void employeeCriteriaCopyCreatesNullFilterTest() {
        var employeeCriteria = new EmployeeCriteria();
        var copy = employeeCriteria.copy();

        assertThat(employeeCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(filter -> filter == null)),
            criteria -> assertThat(criteria).isEqualTo(employeeCriteria)
        );
    }

    @Test
    void employeeCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var employeeCriteria = new EmployeeCriteria();
        setAllFilters(employeeCriteria);

        var copy = employeeCriteria.copy();

        assertThat(employeeCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(filter -> filter != null)),
            criteria -> assertThat(criteria).isEqualTo(employeeCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var employeeCriteria = new EmployeeCriteria();

        assertThat(employeeCriteria).hasToString("EmployeeCriteria{}");
    }

    private static void setAllFilters(EmployeeCriteria employeeCriteria) {
        employeeCriteria.id();
        employeeCriteria.code();
        employeeCriteria.personType();
        employeeCriteria.fullName();
        employeeCriteria.searchText();
        employeeCriteria.gender();
        employeeCriteria.birthDate();
        employeeCriteria.email();
        employeeCriteria.phone();
        employeeCriteria.residence();
        employeeCriteria.education();
        employeeCriteria.idNumber();
        employeeCriteria.idIssueDate();
        employeeCriteria.idIssuePlace();
        employeeCriteria.taxCode();
        employeeCriteria.bankAccount();
        employeeCriteria.bankName();
        employeeCriteria.socialInsuranceNo();
        employeeCriteria.socialInsuranceStart();
        employeeCriteria.socialInsuranceSalary();
        employeeCriteria.dependants();
        employeeCriteria.position();
        employeeCriteria.status();
        employeeCriteria.joinDate();
        employeeCriteria.lastWorkingDay();
        employeeCriteria.terminationType();
        employeeCriteria.terminationReason();
        employeeCriteria.departmentId();
        employeeCriteria.managerId();
        employeeCriteria.userId();
        employeeCriteria.distinct();
    }

    private static Condition<EmployeeCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getCode()) &&
                condition.apply(criteria.getPersonType()) &&
                condition.apply(criteria.getFullName()) &&
                condition.apply(criteria.getSearchText()) &&
                condition.apply(criteria.getGender()) &&
                condition.apply(criteria.getBirthDate()) &&
                condition.apply(criteria.getEmail()) &&
                condition.apply(criteria.getPhone()) &&
                condition.apply(criteria.getResidence()) &&
                condition.apply(criteria.getEducation()) &&
                condition.apply(criteria.getIdNumber()) &&
                condition.apply(criteria.getIdIssueDate()) &&
                condition.apply(criteria.getIdIssuePlace()) &&
                condition.apply(criteria.getTaxCode()) &&
                condition.apply(criteria.getBankAccount()) &&
                condition.apply(criteria.getBankName()) &&
                condition.apply(criteria.getSocialInsuranceNo()) &&
                condition.apply(criteria.getSocialInsuranceStart()) &&
                condition.apply(criteria.getSocialInsuranceSalary()) &&
                condition.apply(criteria.getDependants()) &&
                condition.apply(criteria.getPosition()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getJoinDate()) &&
                condition.apply(criteria.getLastWorkingDay()) &&
                condition.apply(criteria.getTerminationType()) &&
                condition.apply(criteria.getTerminationReason()) &&
                condition.apply(criteria.getDepartmentId()) &&
                condition.apply(criteria.getManagerId()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<EmployeeCriteria> copyFiltersAre(EmployeeCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getCode(), copy.getCode()) &&
                condition.apply(criteria.getPersonType(), copy.getPersonType()) &&
                condition.apply(criteria.getFullName(), copy.getFullName()) &&
                condition.apply(criteria.getSearchText(), copy.getSearchText()) &&
                condition.apply(criteria.getGender(), copy.getGender()) &&
                condition.apply(criteria.getBirthDate(), copy.getBirthDate()) &&
                condition.apply(criteria.getEmail(), copy.getEmail()) &&
                condition.apply(criteria.getPhone(), copy.getPhone()) &&
                condition.apply(criteria.getResidence(), copy.getResidence()) &&
                condition.apply(criteria.getEducation(), copy.getEducation()) &&
                condition.apply(criteria.getIdNumber(), copy.getIdNumber()) &&
                condition.apply(criteria.getIdIssueDate(), copy.getIdIssueDate()) &&
                condition.apply(criteria.getIdIssuePlace(), copy.getIdIssuePlace()) &&
                condition.apply(criteria.getTaxCode(), copy.getTaxCode()) &&
                condition.apply(criteria.getBankAccount(), copy.getBankAccount()) &&
                condition.apply(criteria.getBankName(), copy.getBankName()) &&
                condition.apply(criteria.getSocialInsuranceNo(), copy.getSocialInsuranceNo()) &&
                condition.apply(criteria.getSocialInsuranceStart(), copy.getSocialInsuranceStart()) &&
                condition.apply(criteria.getSocialInsuranceSalary(), copy.getSocialInsuranceSalary()) &&
                condition.apply(criteria.getDependants(), copy.getDependants()) &&
                condition.apply(criteria.getPosition(), copy.getPosition()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getJoinDate(), copy.getJoinDate()) &&
                condition.apply(criteria.getLastWorkingDay(), copy.getLastWorkingDay()) &&
                condition.apply(criteria.getTerminationType(), copy.getTerminationType()) &&
                condition.apply(criteria.getTerminationReason(), copy.getTerminationReason()) &&
                condition.apply(criteria.getDepartmentId(), copy.getDepartmentId()) &&
                condition.apply(criteria.getManagerId(), copy.getManagerId()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}

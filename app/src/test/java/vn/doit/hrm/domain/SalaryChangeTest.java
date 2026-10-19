package vn.doit.hrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hrm.domain.EmployeeTestSamples.*;
import static vn.doit.hrm.domain.SalaryChangeTestSamples.*;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class SalaryChangeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(SalaryChange.class);
        SalaryChange salaryChange1 = getSalaryChangeSample1();
        SalaryChange salaryChange2 = new SalaryChange();
        assertThat(salaryChange1).isNotEqualTo(salaryChange2);

        salaryChange2.setId(salaryChange1.getId());
        assertThat(salaryChange1).isEqualTo(salaryChange2);

        salaryChange2 = getSalaryChangeSample2();
        assertThat(salaryChange1).isNotEqualTo(salaryChange2);
    }

    @Test
    void employeeTest() {
        SalaryChange salaryChange = getSalaryChangeRandomSampleGenerator();
        Employee employeeBack = getEmployeeRandomSampleGenerator();

        salaryChange.setEmployee(employeeBack);
        assertThat(salaryChange.getEmployee()).isEqualTo(employeeBack);

        salaryChange.employee(null);
        assertThat(salaryChange.getEmployee()).isNull();
    }
}

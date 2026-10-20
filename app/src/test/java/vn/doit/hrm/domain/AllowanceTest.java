package vn.doit.hrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hrm.domain.AllowanceTestSamples.*;
import static vn.doit.hrm.domain.EmployeeTestSamples.*;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class AllowanceTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Allowance.class);
        Allowance allowance1 = getAllowanceSample1();
        Allowance allowance2 = new Allowance();
        assertThat(allowance1).isNotEqualTo(allowance2);

        allowance2.setId(allowance1.getId());
        assertThat(allowance1).isEqualTo(allowance2);

        allowance2 = getAllowanceSample2();
        assertThat(allowance1).isNotEqualTo(allowance2);
    }

    @Test
    void employeeTest() {
        Allowance allowance = getAllowanceRandomSampleGenerator();
        Employee employeeBack = getEmployeeRandomSampleGenerator();

        allowance.setEmployee(employeeBack);
        assertThat(allowance.getEmployee()).isEqualTo(employeeBack);

        allowance.employee(null);
        assertThat(allowance.getEmployee()).isNull();
    }
}

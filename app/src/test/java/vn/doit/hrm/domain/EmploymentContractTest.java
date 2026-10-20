package vn.doit.hrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hrm.domain.DepartmentTestSamples.*;
import static vn.doit.hrm.domain.EmployeeTestSamples.*;
import static vn.doit.hrm.domain.EmploymentContractTestSamples.*;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class EmploymentContractTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(EmploymentContract.class);
        EmploymentContract employmentContract1 = getEmploymentContractSample1();
        EmploymentContract employmentContract2 = new EmploymentContract();
        assertThat(employmentContract1).isNotEqualTo(employmentContract2);

        employmentContract2.setId(employmentContract1.getId());
        assertThat(employmentContract1).isEqualTo(employmentContract2);

        employmentContract2 = getEmploymentContractSample2();
        assertThat(employmentContract1).isNotEqualTo(employmentContract2);
    }

    @Test
    void employeeTest() {
        EmploymentContract employmentContract = getEmploymentContractRandomSampleGenerator();
        Employee employeeBack = getEmployeeRandomSampleGenerator();

        employmentContract.setEmployee(employeeBack);
        assertThat(employmentContract.getEmployee()).isEqualTo(employeeBack);

        employmentContract.employee(null);
        assertThat(employmentContract.getEmployee()).isNull();
    }

    @Test
    void departmentTest() {
        EmploymentContract employmentContract = getEmploymentContractRandomSampleGenerator();
        Department departmentBack = getDepartmentRandomSampleGenerator();

        employmentContract.setDepartment(departmentBack);
        assertThat(employmentContract.getDepartment()).isEqualTo(departmentBack);

        employmentContract.department(null);
        assertThat(employmentContract.getDepartment()).isNull();
    }
}

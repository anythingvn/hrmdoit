package vn.doit.hrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hrm.domain.EmployeeTestSamples.*;
import static vn.doit.hrm.domain.WorkAssignmentTestSamples.*;
import static vn.doit.hrm.domain.WorkSiteTestSamples.*;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class WorkAssignmentTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(WorkAssignment.class);
        WorkAssignment workAssignment1 = getWorkAssignmentSample1();
        WorkAssignment workAssignment2 = new WorkAssignment();
        assertThat(workAssignment1).isNotEqualTo(workAssignment2);

        workAssignment2.setId(workAssignment1.getId());
        assertThat(workAssignment1).isEqualTo(workAssignment2);

        workAssignment2 = getWorkAssignmentSample2();
        assertThat(workAssignment1).isNotEqualTo(workAssignment2);
    }

    @Test
    void employeeTest() {
        WorkAssignment workAssignment = getWorkAssignmentRandomSampleGenerator();
        Employee employeeBack = getEmployeeRandomSampleGenerator();

        workAssignment.setEmployee(employeeBack);
        assertThat(workAssignment.getEmployee()).isEqualTo(employeeBack);

        workAssignment.employee(null);
        assertThat(workAssignment.getEmployee()).isNull();
    }

    @Test
    void siteTest() {
        WorkAssignment workAssignment = getWorkAssignmentRandomSampleGenerator();
        WorkSite workSiteBack = getWorkSiteRandomSampleGenerator();

        workAssignment.setSite(workSiteBack);
        assertThat(workAssignment.getSite()).isEqualTo(workSiteBack);

        workAssignment.site(null);
        assertThat(workAssignment.getSite()).isNull();
    }
}

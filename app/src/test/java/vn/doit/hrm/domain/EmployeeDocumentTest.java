package vn.doit.hrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hrm.domain.EmployeeDocumentTestSamples.*;
import static vn.doit.hrm.domain.EmployeeTestSamples.*;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class EmployeeDocumentTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(EmployeeDocument.class);
        EmployeeDocument employeeDocument1 = getEmployeeDocumentSample1();
        EmployeeDocument employeeDocument2 = new EmployeeDocument();
        assertThat(employeeDocument1).isNotEqualTo(employeeDocument2);

        employeeDocument2.setId(employeeDocument1.getId());
        assertThat(employeeDocument1).isEqualTo(employeeDocument2);

        employeeDocument2 = getEmployeeDocumentSample2();
        assertThat(employeeDocument1).isNotEqualTo(employeeDocument2);
    }

    @Test
    void employeeTest() {
        EmployeeDocument employeeDocument = getEmployeeDocumentRandomSampleGenerator();
        Employee employeeBack = getEmployeeRandomSampleGenerator();

        employeeDocument.setEmployee(employeeBack);
        assertThat(employeeDocument.getEmployee()).isEqualTo(employeeBack);

        employeeDocument.employee(null);
        assertThat(employeeDocument.getEmployee()).isNull();
    }
}

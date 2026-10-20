package vn.doit.hrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hrm.domain.DepartmentTestSamples.*;
import static vn.doit.hrm.domain.EmployeeTestSamples.*;
import static vn.doit.hrm.domain.EmployeeTestSamples.*;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class EmployeeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Employee.class);
        Employee employee1 = getEmployeeSample1();
        Employee employee2 = new Employee();
        assertThat(employee1).isNotEqualTo(employee2);

        employee2.setId(employee1.getId());
        assertThat(employee1).isEqualTo(employee2);

        employee2 = getEmployeeSample2();
        assertThat(employee1).isNotEqualTo(employee2);
    }

    @Test
    void departmentTest() {
        Employee employee = getEmployeeRandomSampleGenerator();
        Department departmentBack = getDepartmentRandomSampleGenerator();

        employee.setDepartment(departmentBack);
        assertThat(employee.getDepartment()).isEqualTo(departmentBack);

        employee.department(null);
        assertThat(employee.getDepartment()).isNull();
    }

    @Test
    void managerTest() {
        Employee employee = getEmployeeRandomSampleGenerator();
        Employee employeeBack = getEmployeeRandomSampleGenerator();

        employee.setManager(employeeBack);
        assertThat(employee.getManager()).isEqualTo(employeeBack);

        employee.manager(null);
        assertThat(employee.getManager()).isNull();
    }

    @Test
    void toStringOmitsSensitiveFields() {
        Employee employee = new Employee()
            .code("NV0142")
            .fullName("Trần Minh Quân")
            .idNumber("079123456789")
            .idIssueDate(java.time.LocalDate.of(2020, 1, 2))
            .idIssuePlace("Cục CSQLHC về TTXH")
            .taxCode("8001234567")
            .bankAccount("111222333444")
            .bankName("Ngân hàng Bí Mật")
            .socialInsuranceNo("7900112233");
        String text = employee.toString();
        org.assertj.core.api.Assertions.assertThat(text)
            .contains("NV0142")
            .doesNotContain("079123456789")
            .doesNotContain("2020-01-02")
            .doesNotContain("Cục CSQLHC")
            .doesNotContain("8001234567")
            .doesNotContain("111222333444")
            .doesNotContain("Ngân hàng Bí Mật")
            .doesNotContain("7900112233")
            .doesNotContain("idNumber")
            .doesNotContain("taxCode")
            .doesNotContain("bankAccount");
    }
}

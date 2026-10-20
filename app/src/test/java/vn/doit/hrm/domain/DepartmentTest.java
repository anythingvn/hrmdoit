package vn.doit.hrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hrm.domain.DepartmentTestSamples.*;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class DepartmentTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Department.class);
        Department department1 = getDepartmentSample1();
        Department department2 = new Department();
        assertThat(department1).isNotEqualTo(department2);

        department2.setId(department1.getId());
        assertThat(department1).isEqualTo(department2);

        department2 = getDepartmentSample2();
        assertThat(department1).isNotEqualTo(department2);
    }
}

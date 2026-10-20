package vn.doit.hrm.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class EmploymentContractDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(EmploymentContractDTO.class);
        EmploymentContractDTO employmentContractDTO1 = new EmploymentContractDTO();
        employmentContractDTO1.setId(1L);
        EmploymentContractDTO employmentContractDTO2 = new EmploymentContractDTO();
        assertThat(employmentContractDTO1).isNotEqualTo(employmentContractDTO2);
        employmentContractDTO2.setId(employmentContractDTO1.getId());
        assertThat(employmentContractDTO1).isEqualTo(employmentContractDTO2);
        employmentContractDTO2.setId(2L);
        assertThat(employmentContractDTO1).isNotEqualTo(employmentContractDTO2);
        employmentContractDTO1.setId(null);
        assertThat(employmentContractDTO1).isNotEqualTo(employmentContractDTO2);
    }
}

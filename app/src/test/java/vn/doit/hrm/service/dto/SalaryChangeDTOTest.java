package vn.doit.hrm.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class SalaryChangeDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(SalaryChangeDTO.class);
        SalaryChangeDTO salaryChangeDTO1 = new SalaryChangeDTO();
        salaryChangeDTO1.setId(1L);
        SalaryChangeDTO salaryChangeDTO2 = new SalaryChangeDTO();
        assertThat(salaryChangeDTO1).isNotEqualTo(salaryChangeDTO2);
        salaryChangeDTO2.setId(salaryChangeDTO1.getId());
        assertThat(salaryChangeDTO1).isEqualTo(salaryChangeDTO2);
        salaryChangeDTO2.setId(2L);
        assertThat(salaryChangeDTO1).isNotEqualTo(salaryChangeDTO2);
        salaryChangeDTO1.setId(null);
        assertThat(salaryChangeDTO1).isNotEqualTo(salaryChangeDTO2);
    }
}

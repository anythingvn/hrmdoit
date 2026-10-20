package vn.doit.hrm.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class AllowanceDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(AllowanceDTO.class);
        AllowanceDTO allowanceDTO1 = new AllowanceDTO();
        allowanceDTO1.setId(1L);
        AllowanceDTO allowanceDTO2 = new AllowanceDTO();
        assertThat(allowanceDTO1).isNotEqualTo(allowanceDTO2);
        allowanceDTO2.setId(allowanceDTO1.getId());
        assertThat(allowanceDTO1).isEqualTo(allowanceDTO2);
        allowanceDTO2.setId(2L);
        assertThat(allowanceDTO1).isNotEqualTo(allowanceDTO2);
        allowanceDTO1.setId(null);
        assertThat(allowanceDTO1).isNotEqualTo(allowanceDTO2);
    }
}

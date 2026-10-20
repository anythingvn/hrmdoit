package vn.doit.hrm.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class WorkSiteDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(WorkSiteDTO.class);
        WorkSiteDTO workSiteDTO1 = new WorkSiteDTO();
        workSiteDTO1.setId(1L);
        WorkSiteDTO workSiteDTO2 = new WorkSiteDTO();
        assertThat(workSiteDTO1).isNotEqualTo(workSiteDTO2);
        workSiteDTO2.setId(workSiteDTO1.getId());
        assertThat(workSiteDTO1).isEqualTo(workSiteDTO2);
        workSiteDTO2.setId(2L);
        assertThat(workSiteDTO1).isNotEqualTo(workSiteDTO2);
        workSiteDTO1.setId(null);
        assertThat(workSiteDTO1).isNotEqualTo(workSiteDTO2);
    }
}

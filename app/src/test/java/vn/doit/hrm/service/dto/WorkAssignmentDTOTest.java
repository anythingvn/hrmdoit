package vn.doit.hrm.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class WorkAssignmentDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(WorkAssignmentDTO.class);
        WorkAssignmentDTO workAssignmentDTO1 = new WorkAssignmentDTO();
        workAssignmentDTO1.setId(1L);
        WorkAssignmentDTO workAssignmentDTO2 = new WorkAssignmentDTO();
        assertThat(workAssignmentDTO1).isNotEqualTo(workAssignmentDTO2);
        workAssignmentDTO2.setId(workAssignmentDTO1.getId());
        assertThat(workAssignmentDTO1).isEqualTo(workAssignmentDTO2);
        workAssignmentDTO2.setId(2L);
        assertThat(workAssignmentDTO1).isNotEqualTo(workAssignmentDTO2);
        workAssignmentDTO1.setId(null);
        assertThat(workAssignmentDTO1).isNotEqualTo(workAssignmentDTO2);
    }
}

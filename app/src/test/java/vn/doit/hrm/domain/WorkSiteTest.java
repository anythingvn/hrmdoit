package vn.doit.hrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.doit.hrm.domain.WorkSiteTestSamples.*;

import org.junit.jupiter.api.Test;
import vn.doit.hrm.web.rest.TestUtil;

class WorkSiteTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(WorkSite.class);
        WorkSite workSite1 = getWorkSiteSample1();
        WorkSite workSite2 = new WorkSite();
        assertThat(workSite1).isNotEqualTo(workSite2);

        workSite2.setId(workSite1.getId());
        assertThat(workSite1).isEqualTo(workSite2);

        workSite2 = getWorkSiteSample2();
        assertThat(workSite1).isNotEqualTo(workSite2);
    }
}

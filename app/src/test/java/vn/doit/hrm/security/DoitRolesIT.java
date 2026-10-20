package vn.doit.hrm.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.repository.AuthorityRepository;
import vn.doit.hrm.repository.UserRepository;

@IntegrationTest
class DoitRolesIT {

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void authoritiesSeeded() {
        List<String> names = authorityRepository.findAll().stream().map(Authority::getName).toList();
        assertThat(names).contains("ROLE_ADMIN", "ROLE_HR", "ROLE_KE_TOAN", "ROLE_BGD");
        assertThat(AuthoritiesConstants.HR).isEqualTo("ROLE_HR");
        assertThat(AuthoritiesConstants.KE_TOAN).isEqualTo("ROLE_KE_TOAN");
        assertThat(AuthoritiesConstants.BGD).isEqualTo("ROLE_BGD");
    }

    @Test
    void sampleUserRemovedAndAdminKept() {
        assertThat(userRepository.findOneByLogin("user")).isEmpty();
        assertThat(userRepository.findOneWithAuthoritiesByLogin("admin")).isPresent();
    }
}

package vn.doit.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.SensitiveAccessLog;

/**
 * Spring Data JPA repository for the SensitiveAccessLog entity.
 */
@Repository
public interface SensitiveAccessLogRepository extends JpaRepository<SensitiveAccessLog, Long> {}

package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollVersion;

@Repository
public interface PayrollVersionRepository extends JpaRepository<PayrollVersion, Long> {
    @Query("select max(v.version) from PayrollVersion v where v.period.id = :periodId")
    Integer maxVersion(@Param("periodId") Long periodId);

    List<PayrollVersion> findByPeriodIdOrderByVersionDesc(Long periodId);

    Optional<PayrollVersion> findByPeriodIdAndVersion(Long periodId, int version);
}

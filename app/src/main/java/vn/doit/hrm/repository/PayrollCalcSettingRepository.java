package vn.doit.hrm.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.PayrollCalcSetting;

@Repository
public interface PayrollCalcSettingRepository extends JpaRepository<PayrollCalcSetting, Long> {
    List<PayrollCalcSetting> findAllByOrderByEffectiveFromAsc();
}

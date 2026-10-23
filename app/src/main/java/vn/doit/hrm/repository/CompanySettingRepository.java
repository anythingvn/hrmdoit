package vn.doit.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.CompanySetting;

@Repository
public interface CompanySettingRepository extends JpaRepository<CompanySetting, Long> {}

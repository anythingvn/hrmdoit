package vn.doit.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.WorkCalendarSetting;

@Repository
public interface WorkCalendarSettingRepository extends JpaRepository<WorkCalendarSetting, Long> {}

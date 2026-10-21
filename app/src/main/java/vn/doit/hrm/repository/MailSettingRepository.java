package vn.doit.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.MailSetting;

@Repository
public interface MailSettingRepository extends JpaRepository<MailSetting, Long> {}

package vn.doit.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.Holiday;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByDayBetweenOrderByDayAsc(LocalDate from, LocalDate to);

    boolean existsByDay(LocalDate day);

    boolean existsByDayAndIdNot(LocalDate day, Long id);
}

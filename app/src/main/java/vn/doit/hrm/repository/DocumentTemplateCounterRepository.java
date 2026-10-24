package vn.doit.hrm.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.DocumentTemplateCounter;
import vn.doit.hrm.domain.enumeration.TemplateKind;

@Repository
public interface DocumentTemplateCounterRepository extends JpaRepository<DocumentTemplateCounter, TemplateKind> {
    /** Khóa dòng để hai lần tải lên cùng lúc không cấp trùng số. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DocumentTemplateCounter c where c.kind = :kind")
    Optional<DocumentTemplateCounter> lockByKind(TemplateKind kind);
}

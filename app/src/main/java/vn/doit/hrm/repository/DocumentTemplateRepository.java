package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.DocumentTemplate;
import vn.doit.hrm.domain.enumeration.TemplateKind;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    Optional<DocumentTemplate> findFirstByKindAndActiveTrue(TemplateKind kind);

    Optional<DocumentTemplate> findByKindAndVersion(TemplateKind kind, int version);

    long countByKind(TemplateKind kind);

    List<DocumentTemplate> findByKindOrderByVersionDesc(TemplateKind kind);

    @Query("select coalesce(max(t.version), 0) from DocumentTemplate t where t.kind = :kind")
    int maxVersion(TemplateKind kind);
}

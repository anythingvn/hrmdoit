package vn.doit.hrm.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.GeneratedDocument;
import vn.doit.hrm.domain.enumeration.DocumentOwnerType;
import vn.doit.hrm.domain.enumeration.GeneratedFileKind;

@Repository
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {
    List<GeneratedDocument> findByOwnerTypeAndOwnerIdOrderByIdDesc(DocumentOwnerType ownerType, Long ownerId);

    Optional<GeneratedDocument> findFirstByOwnerTypeAndOwnerIdAndKindOrderByIdDesc(
        DocumentOwnerType ownerType,
        Long ownerId,
        GeneratedFileKind kind
    );
}

package vn.doit.hrm.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.DocumentTemplateCounter;
import vn.doit.hrm.domain.enumeration.TemplateKind;

@Repository
public interface DocumentTemplateCounterRepository extends JpaRepository<DocumentTemplateCounter, TemplateKind> {
    /**
     * Tạo dòng đếm nếu chưa có, bắt đầu từ số phiên bản lớn nhất đang có của loại đó; nhiều giao dịch gọi cùng lúc thì chỉ một dòng
     * được tạo (không lỗi trùng khóa).
     */
    @Modifying
    @Query(
        value = "insert into document_template_counter (kind, last_version) " +
            "values (:kind, (select coalesce(max(t.version), 0) from document_template t where t.kind = :kind)) " +
            "on conflict (kind) do nothing",
        nativeQuery = true
    )
    void insertIfMissing(@Param("kind") String kind);

    /** Khóa dòng để hai lần tải lên cùng lúc không cấp trùng số. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DocumentTemplateCounter c where c.kind = :kind")
    Optional<DocumentTemplateCounter> lockByKind(TemplateKind kind);
}

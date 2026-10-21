package vn.doit.hrm.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.doit.hrm.domain.DocumentSequence;
import vn.doit.hrm.domain.enumeration.DocumentType;

@Repository
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {
    /** Tạo dãy (bắt đầu từ 1) nếu chưa có; nhiều giao dịch gọi cùng lúc thì chỉ một dòng được tạo. */
    @Modifying
    @Query(
        value = "insert into document_sequence (type_code, year, next_value, last_issued) values (:type, :year, 1, 0) " +
        "on conflict (type_code, year) do nothing",
        nativeQuery = true
    )
    void insertIfMissing(@Param("type") String type, @Param("year") int year);

    /** Khóa dòng đến hết giao dịch: hai người cấp số cùng lúc thì người sau chờ người trước. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.type = :type and s.year = :year")
    Optional<DocumentSequence> findForUpdate(@Param("type") DocumentType type, @Param("year") int year);

    List<DocumentSequence> findByYear(int year);
}

package com.smsweb.sms.repositories.tc;

import com.smsweb.sms.models.tc.ClassDivision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassDivisionRepository extends JpaRepository<ClassDivision, Long> {

    List<ClassDivision> findBySchool_IdOrderByNameAsc(Long schoolId);

    Optional<ClassDivision> findByUuidAndSchool_Id(UUID uuid, Long schoolId);

    boolean existsBySchool_IdAndNameIgnoreCase(Long schoolId, String name);

    /** True if any class group of the school uses this TC format name. */
    boolean existsBySchool_IdAndTcFormat(Long schoolId, String tcFormat);

    /**
     * Locks the division row for the TC number. Must run inside the caller's transaction.
     * Lock order: exit row, then school row, then this row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ClassDivision d WHERE d.id = :id AND d.school.id = :schoolId")
    Optional<ClassDivision> findByIdForUpdate(@Param("id") Long id, @Param("schoolId") Long schoolId);
}

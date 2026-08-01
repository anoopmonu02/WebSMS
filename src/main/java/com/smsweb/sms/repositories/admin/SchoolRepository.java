package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.admin.School;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {
    List<School> findAllBySchoolName(String name);

    List<School> findAllByStatus(String status);

    /**
     * Locks the given school's row for the duration of the caller's transaction.
     * Used as a per-school mutex (currently: PSRN assignment at student
     * registration, see PsrnService) so two concurrent requests for the same
     * school serialize instead of racing. Must be called from within an
     * existing @Transactional method — the lock is held until that
     * transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM School s WHERE s.id = :id")
    Optional<School> findByIdForUpdate(@Param("id") Long id);
}

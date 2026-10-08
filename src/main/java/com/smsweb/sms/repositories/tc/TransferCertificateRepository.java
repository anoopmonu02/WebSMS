package com.smsweb.sms.repositories.tc;

import com.smsweb.sms.models.tc.TransferCertificate;
import com.smsweb.sms.models.tc.enums.TcStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferCertificateRepository
        extends JpaRepository<TransferCertificate, Long>, JpaSpecificationExecutor<TransferCertificate> {

    Optional<TransferCertificate> findByUuidAndSchool_Id(UUID uuid, Long schoolId);

    Optional<TransferCertificate> findFirstByExit_IdAndStatus(Long exitId, TcStatus status);

    /** All TCs of one exit, oldest first. */
    List<TransferCertificate> findByExit_IdOrderByIdAsc(Long exitId);

    List<TransferCertificate> findByStudent_IdAndSchool_IdOrderByIdAsc(Long studentId, Long schoolId);

    long countByStudent_IdAndSchool_Id(Long studentId, Long schoolId);

    /** Used to lock the division start number once any TC exists. */
    boolean existsByDivision_Id(Long divisionId);

    /** The uuid of the exit a TC belongs to (so the exit row can be locked before the TC is read). */
    @Query("SELECT t.exit.uuid FROM TransferCertificate t WHERE t.uuid = :uuid AND t.school.id = :schoolId")
    Optional<UUID> findExitUuid(@Param("uuid") UUID uuid, @Param("schoolId") Long schoolId);
}

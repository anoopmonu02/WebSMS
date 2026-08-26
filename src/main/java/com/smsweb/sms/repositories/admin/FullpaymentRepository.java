package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.FullPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FullpaymentRepository extends JpaRepository<FullPayment, Long> {

    List<FullPayment> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    /**
     * Medium-aware lookup used by FeeSubmissionService#getFeeDetailsBasedOnMonth (Full-Payment
     * migration, mirrors the fee_class_map / discount_class_map medium migrations). Replaces the
     * previous grade-only findBySchool_IdAndAcademicYear_IdAndGrade_Id - once medium is mandatory
     * on FullPayment, more than one row can exist per grade (one per medium), so a grade-only
     * Optional lookup would throw IncorrectResultSizeDataAccessException as soon as a second
     * medium is configured for the same grade. Removed rather than kept as a fallback: its one
     * caller already has a non-null mediumId in scope by the time it reaches this lookup (see
     * that method's own null-guard earlier), so there's no legitimate defensive case for it.
     */
    Optional<FullPayment> findBySchool_IdAndAcademicYear_IdAndGrade_IdAndMedium_Id(Long school_id, Long academic_id, Long grade_id, Long medium_id);

}

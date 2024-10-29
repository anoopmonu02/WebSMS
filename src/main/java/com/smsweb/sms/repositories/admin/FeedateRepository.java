package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.FeeDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedateRepository extends JpaRepository<FeeDate, Long> {
    List<FeeDate> findAllByAcademicYear_IdAndSchool_IdOrderByIdDesc(Long academic, Long school);

    Optional<FeeDate> findByAcademicYear_IdAndSchool_IdAndMonthMaster_Id(Long academic, Long school, Long monthId);


    @Query("SELECT fd FROM FeeDate fd WHERE fd.academicYear.id = :academicYear AND fd.school.id = :school AND month(fd.feeSubmissiondate) = :givenMonth")
    List<FeeDate> findByAcademicYearAndSchoolAndGivenMonth(@Param("academicYear") Long academicYear, @Param("school")Long school, @Param("givenMonth")  int givenMonth);

    Optional<FeeDate> findByAcademicYear_IdAndSchool_IdAndMonthMaster_MonthName(Long academic, Long school, String monthName);
}

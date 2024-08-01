package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.MonthMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface MonthmappingRepository extends JpaRepository<MonthMapping, Long> {
    List<MonthMapping> findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(Long id, Long schoolid);
    Optional<MonthMapping> findByAcademicYear_IdAndSchool_IdAndMonthMaster_MonthName(Long id, Long schoolid, String monthName);

    @Query(value="select " +
            "((select priority from month_mapping where month_master_id=(select id from month_master where month_name=monthname(str_to_date(:subDate,'%d/%b/%Y'))) and academic_year_id=:academicYearId and school_id=:schoolId)- " +
            "(select priority from month_mapping where month_master_id=(select id from month_master where month_name=:monthName) and academic_year_id=:academicYearId) " +
            ") as DIFF limit 1", nativeQuery = true)
    int findMonthDifference(@Param("academicYearId") Long academicYearId,
                            @Param("schoolId") Long schoolId,
                            @Param("monthName") String monthName,
                            @Param("subDate") String subDate);

    @Query(value = "select datediff(str_to_date(:feeDate,'%d/%b/%Y'),str_to_date(:subDate,'%d/%b/%Y')) as ddiff", nativeQuery = true)
    int currentFeeDateDifference(@Param("feeDate") String feeDate, @Param("subDate") String subDate);

    @Query(value = "select (month(str_to_date(:subDate,'%d/%b/%Y'))-month(:academicYearStartDate)) as DIFF", nativeQuery = true)
    int firstMonthDifference(@Param("subDate") String subDate, @Param("academicYearStartDate") Date academicYearStartDate);

    @Query(value = "select (" +
            "(select priority from month_mapping where month_master_id=(select id from month_master where month_name=:monthName) and school_id=:schoolId and academic_year_id=:academicYearId) - " +
            "(select priority from month_mapping where month_master_id=(select id from month_master where month_name=monthname(curdate())) and school_id=:schoolId and academic_year_id=:academicYearId) " +
            ") as Result", nativeQuery = true)
    int findMonthDifferenceToNullify(@Param("academicYearId") Long academicYearId,
                                     @Param("schoolId") Long schoolId,
                                     @Param("monthName") String monthName);

}

package com.smsweb.sms.repositories.tc;

import com.smsweb.sms.models.tc.ClassDivisionGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassDivisionGradeRepository extends JpaRepository<ClassDivisionGrade, Long> {

    List<ClassDivisionGrade> findBySchool_Id(Long schoolId);

    /** All mappings of the school with grade and division loaded (for the Class Groups page). */
    @Query("SELECT m FROM ClassDivisionGrade m JOIN FETCH m.grade JOIN FETCH m.division WHERE m.school.id = :schoolId")
    List<ClassDivisionGrade> findAllWithGradeAndDivision(@Param("schoolId") Long schoolId);

    /** The mapping of one grade with its division loaded, so it is usable outside a transaction. */
    @Query("SELECT m FROM ClassDivisionGrade m JOIN FETCH m.division WHERE m.school.id = :schoolId AND m.grade.id = :gradeId")
    Optional<ClassDivisionGrade> findWithDivision(@Param("schoolId") Long schoolId, @Param("gradeId") Long gradeId);

    List<ClassDivisionGrade> findByDivision_Id(Long divisionId);

    Optional<ClassDivisionGrade> findBySchool_IdAndGrade_Id(Long schoolId, Long gradeId);

    /** Caller must be inside a transaction. */
    void deleteByDivision_Id(Long divisionId);
}

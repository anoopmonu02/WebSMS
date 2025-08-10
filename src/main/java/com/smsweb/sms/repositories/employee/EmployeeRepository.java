package com.smsweb.sms.repositories.employee;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Employee findByEmployeeCode(String employeeCode);
    Optional<Employee> findByUuidAndStatus(UUID uuid, String status);

    List<Employee> findAllBySchool_IdAndStatusOrderByEmployeeNameAsc(Long school, String status);
    List<Employee> findAllByStatusOrderByEmployeeNameAsc(String status);

    Employee findByUserEntity(UserEntity userEntity);

    @Query("SELECT e.school FROM Employee e WHERE e.userEntity.username = :username")
    Optional<School> findSchoolByUsername(@Param("username") String username);

    int countAllBySchool_IdAndStatus(Long school, String status);

    @Query(value = "SELECT a.dob, a.employee_name, a.employee_code " +
            "FROM employees a " +
            "WHERE a.school_id = :schoolId " +
            "AND a.status = :status " +
            "AND a.dob IS NOT NULL " +
            "AND DATE_FORMAT(a.dob, '%m-%d') BETWEEN DATE_FORMAT(CURDATE(), '%m-%d') " +
            "AND DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '%m-%d')",
            nativeQuery = true)
    List<Object[]> findUpcomingBirthdaysInNext7Days(@Param("schoolId") Long school,
                                                    @Param("status") String status);
}

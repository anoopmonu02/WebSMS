package com.smsweb.sms.repositories.employee;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

}

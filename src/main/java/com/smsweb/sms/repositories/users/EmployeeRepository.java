package com.smsweb.sms.repositories.users;

import com.smsweb.sms.models.Users.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Employee findByEmployeeCode(String employeeCode);
}

package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.admin.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolRepository extends JpaRepository<School, Long> {
    List<School> findAllBySchoolName(String name);

    List<School> findAllByStatus(String status);
}

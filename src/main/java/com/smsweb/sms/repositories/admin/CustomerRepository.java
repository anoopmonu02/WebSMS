package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByName(String name);

    List<Customer> findAllByStatus(String status);
}

package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.repositories.admin.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;

    @Autowired
    public CustomerService(CustomerRepository customerRepository){
        this.customerRepository = customerRepository;
    }

    public List<Customer> getAllCustomers(){
        return customerRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerById(Long id){
        return customerRepository.findById(id);
    }

    @Transactional
    public void saveCustomer(Customer customer){
        customerRepository.save(customer);
    }

    public void deleteCustomer(Long id){
        customerRepository.deleteById(id);
    }

    public List<Customer> getAllCustomerByName(String customerName){
        return customerRepository.findAllByName(customerName);
    }

    public List<Customer> getAllCustomerByStatus(String status){
        return customerRepository.findAllByStatus(status);
    }

}

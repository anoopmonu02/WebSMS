package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.models.universal.Province;
import com.smsweb.sms.repositories.admin.CustomerRepository;
import com.smsweb.sms.repositories.universal.CityRepository;
import com.smsweb.sms.repositories.universal.ProvinceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;

    @Autowired
    public CustomerService(CustomerRepository customerRepository, ProvinceRepository provinceRepository, CityRepository cityRepository){
        this.customerRepository = customerRepository;
        this.provinceRepository = provinceRepository;
        this.cityRepository = cityRepository;
    }

    public List<Customer> getAllCustomers(){
        return customerRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
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

    public List<Province> getAllProvinces(){
        return provinceRepository.findAll(Sort.by(Sort.DEFAULT_DIRECTION,"provinceName"));
    }

    public List<City> getAllCitiesByProvince(Long provinceId){
        return cityRepository.findByProvinceId(provinceId);
    }

}

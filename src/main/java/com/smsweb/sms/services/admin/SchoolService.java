package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.models.universal.Province;
import com.smsweb.sms.repositories.admin.CustomerRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.universal.CityRepository;
import com.smsweb.sms.repositories.universal.ProvinceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class SchoolService {
    private final SchoolRepository schoolRepository;
    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final CustomerRepository customerRepository;

    @Autowired
    public SchoolService(SchoolRepository schoolRepository, ProvinceRepository provinceRepository, CityRepository cityRepository,
                         CustomerRepository customerRepository){
        this.schoolRepository = schoolRepository;
        this.provinceRepository = provinceRepository;
        this.cityRepository = cityRepository;
        this.customerRepository = customerRepository;
    }


    public List<School> getAllSchools(){
        return schoolRepository.findAll(Sort.by(Sort.Direction.ASC, "schoolName"));
    }

    @Transactional(readOnly = true)
    public Optional<School> getSchoolById(Long id){
        return schoolRepository.findById(id);
    }

    @Transactional
    public void saveSchool(School school, MultipartFile logo, String fileNameOrSchoolCode){

        school.setLogo1("");
        school.setSchoolCode("SC-"+fileNameOrSchoolCode);
        schoolRepository.save(school);
    }

    public void deleteSchool(Long id){
        schoolRepository.deleteById(id);
    }

    public List<School> getAllSchoolByName(String schoolName){
        return schoolRepository.findAllBySchoolName(schoolName);
    }

    public List<School> getAllSchoolByStatus(String status){
        return schoolRepository.findAllByStatus(status);
    }

    public List<Province> getAllProvinces(){
        return provinceRepository.findAll(Sort.by(Sort.DEFAULT_DIRECTION,"provinceName"));
    }

    public List<City> getAllCitiesByProvince(Long provinceId){
        return cityRepository.findByProvinceId(provinceId);
    }

    public List<Customer> getAllCustomers(){
        return customerRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }


}

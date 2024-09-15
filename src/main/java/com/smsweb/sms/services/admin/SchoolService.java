package com.smsweb.sms.services.admin;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.models.universal.Province;
import com.smsweb.sms.repositories.admin.CustomerRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.universal.CityRepository;
import com.smsweb.sms.repositories.universal.ProvinceRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final UserRepository userRepository;
    private final FileHandleHelper fileHandleHelper;

    @Autowired
    public SchoolService(SchoolRepository schoolRepository, ProvinceRepository provinceRepository, CityRepository cityRepository,
                         CustomerRepository customerRepository, UserRepository userRepository, FileHandleHelper fileHandleHelper){
        this.schoolRepository = schoolRepository;
        this.provinceRepository = provinceRepository;
        this.cityRepository = cityRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.fileHandleHelper = fileHandleHelper;
    }


    public List<School> getAllSchools(){
        return schoolRepository.findAll(Sort.by(Sort.Direction.ASC, "schoolName"));
    }

    @Transactional(readOnly = true)
    public Optional<School> getSchoolById(Long id){
        return schoolRepository.findById(id);
    }

    @Transactional
    public School saveSchool(School school, MultipartFile logo, String fileNameOrSchoolCode) throws IOException {
        School existingSchool=null;
        String imageResponse = fileHandleHelper.saveImage("school", logo);
        if(imageResponse!=null && (imageResponse.equalsIgnoreCase("success") || imageResponse.equalsIgnoreCase("Success_no_image"))){
            try{
                if(!imageResponse.equalsIgnoreCase("Success_no_image")){
                    school.setLogo1(fileNameOrSchoolCode+"_"+logo.getOriginalFilename());
                } else{
                    // if school is going to update without new logo selection happen
                    if(imageResponse.equalsIgnoreCase("Success_no_image")){
                        school.setLogo1(null);
                        if(school.getId()!=null){
                            existingSchool = schoolRepository.findById(school.getId()).orElseThrow(() -> new RuntimeException("School not found"));
                            school.setLogo1(existingSchool.getLogo1());
                        }
                    }
                }
                school.setSchoolCode("SC-"+fileNameOrSchoolCode);
                if(existingSchool!=null && existingSchool.getSchoolCode().length()>0){
                    school.setSchoolCode(existingSchool.getSchoolCode());

                }
                if(existingSchool.getId()!=null){
                    school.setUpdatedBy(getLoggedInUser());
                }
                else{
                    school.setCreatedBy(getLoggedInUser());
                }
                return schoolRepository.save(school);
            }catch (DataIntegrityViolationException ed) {
                throw new UniqueConstraintsException("School Name: "+school.getSchoolName()+" already exists.", ed);
            }catch(Exception e){
                e.printStackTrace();
                throw new ObjectNotSaveException("Failed to save school", e);
            }
        } else if(imageResponse.equalsIgnoreCase("fail")){
            throw new FileFormatException("Fail to save logo");
        } else if(imageResponse.equalsIgnoreCase("Either image format not supported or size exceeded 2MB.")){
            throw new FileSizeLimitExceededException("Either image format not supported or size exceeded 2MB.");
        }
        return null;
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

    private UserEntity getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userRepository.findByUsername(userDetails.getUsername());
        }
        return null;
    }


}

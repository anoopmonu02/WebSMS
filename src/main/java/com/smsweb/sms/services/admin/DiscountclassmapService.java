package com.smsweb.sms.services.admin;

import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.models.admin.DiscountClassMap;
import com.smsweb.sms.repositories.admin.DiscountclassmapRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DiscountclassmapService {

    private final DiscountclassmapRepository repository;

    @Autowired
    public DiscountclassmapService(DiscountclassmapRepository repository){
        this.repository = repository;
    }

    public List<DiscountClassMap> getAllDiscountClassMapping(Long school_id, Long academic_id){
        return repository.findAllBySchool_IdAndAcademicYear_Id(school_id, academic_id);
    }

    public List<DiscountClassMap> getAllDiscountClassMappingByGrade(Long school_id, Long academic_id, Long grade_id){
        return repository.findAllByGrade_IdAndSchool_IdAndAcademicYear_Id(grade_id,school_id, academic_id);
    }

    @Transactional
    public List<DiscountClassMap> saveAllDiscountClassMap(List<DiscountClassMap> discountClassMaps){
        List<DiscountClassMap> discountClassMapList = repository.saveAll(discountClassMaps);
        return discountClassMapList;
    }

    @Transactional
    public DiscountClassMap save(DiscountClassMap fcm){
        DiscountClassMap discountClassMap = repository.save(fcm);
        return discountClassMap;
    }

    public Optional<DiscountClassMap> getDiscountClassMapById(Long id){
        return repository.findById(id);
    }
    public Optional<DiscountClassMap> getDiscountClassMapByDiscountName(String discountName, Long academic_id, Long school_id, Long grade_id){
        return repository.findByDiscounthead_DiscountNameAndAcademicYear_IdAndSchool_IdAndGrade_Id(discountName, academic_id, school_id, grade_id);
    }

    public String delete(Long id){
        try{
            repository.deleteById(id);
        }catch(Exception e){
            e.printStackTrace();
            throw new ObjectNotDeleteException("Error in deletion "+e.getLocalizedMessage());
        }
        return "success";
    }

}

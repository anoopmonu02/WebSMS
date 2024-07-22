package com.smsweb.sms.services.admin;


import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.models.admin.FeeClassMap;
import com.smsweb.sms.repositories.admin.FeeclassmapRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FeeclassmapService {
    private final FeeclassmapRepository feeclassmapRepository;

    @Autowired
    public FeeclassmapService(FeeclassmapRepository feeclassmapRepository){
        this.feeclassmapRepository = feeclassmapRepository;
    }

    public List<FeeClassMap> getAllFeeClassMapping(Long school_id, Long academic_id){
        return feeclassmapRepository.findAllBySchool_IdAndAcademicYear_Id(school_id, academic_id);
    }

    public List<FeeClassMap> getAllFeeClassMappingByGrade(Long grade_id, Long school_id, Long academic_id){
        return feeclassmapRepository.findAllByGrade_IdAndSchool_IdAndAcademicYear_Id(grade_id,school_id, academic_id);
    }

    @Transactional
    public List<FeeClassMap> saveAllFeeClassMap(List<FeeClassMap> feeClassMaps){
        List<FeeClassMap> feeClassMapList = feeclassmapRepository.saveAll(feeClassMaps);
        return feeClassMapList;
    }

    @Transactional
    public FeeClassMap save(FeeClassMap fcm){
        FeeClassMap feeClassMap = feeclassmapRepository.save(fcm);
        return feeClassMap;
    }


    public Optional<FeeClassMap> getFeeClassMapById(Long id){
        return feeclassmapRepository.findById(id);
    }

    public String delete(Long id){
        try{
            feeclassmapRepository.deleteById(id);
        }catch(Exception e){
            throw new ObjectNotDeleteException("Error in deletion "+e.getLocalizedMessage());
        }
        return "success";
    }
}

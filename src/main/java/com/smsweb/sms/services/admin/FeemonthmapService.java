package com.smsweb.sms.services.admin;


import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.models.admin.FeeMonthMap;
import com.smsweb.sms.repositories.admin.FeemonthmapRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FeemonthmapService {
    private final FeemonthmapRepository feemonthmapRepository;

    @Autowired
    public FeemonthmapService(FeemonthmapRepository feemonthmapRepository){
        this.feemonthmapRepository = feemonthmapRepository;
    }

    public List<FeeMonthMap> getAllFeeMonthMap(Long school_id, Long academic_id){
        Sort sort = Sort.by(Sort.Order.asc("feehead"), Sort.Order.asc("monthMaster"));
        return feemonthmapRepository.findAllBySchool_IdAndAcademicYear_IdOrderByFeeheadAscMonthMasterAsc(school_id, academic_id, sort);
    }

    public List<FeeMonthMap> getAllFeeMonthMapByFee(Long school_id, Long academic_id, Long fee_id){
        return feemonthmapRepository.findAllBySchool_IdAndAcademicYear_IdAndFeehead_Id(school_id, academic_id, fee_id);
    }

    public Optional<FeeMonthMap> getFeeMonthMapById(Long id){
        return feemonthmapRepository.findById(id);
    }

    public List<FeeMonthMap> saveAllFeeMonths(List<FeeMonthMap> feeMonthMaps){
        return feemonthmapRepository.saveAll(feeMonthMaps);
    }

    public FeeMonthMap saveFeeMonth(FeeMonthMap feeMonthMap){
        return feemonthmapRepository.save(feeMonthMap);
    }

    public String delete(Long id){
        try{
            feemonthmapRepository.deleteById(id);
        }catch(Exception e){
            throw new ObjectNotDeleteException("Error in deletion "+e.getLocalizedMessage());
        }
        return "success";
    }

}

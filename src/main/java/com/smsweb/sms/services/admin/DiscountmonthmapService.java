package com.smsweb.sms.services.admin;


import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.models.admin.DiscountMonthMap;
import com.smsweb.sms.repositories.admin.DiscountmonthmapRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DiscountmonthmapService {
    private final DiscountmonthmapRepository repository;

    @Autowired
    public DiscountmonthmapService(DiscountmonthmapRepository repository){
        this.repository = repository;
    }

    public List<DiscountMonthMap> getAllDiscountMonthMap(Long school_id, Long academic_id){
        Sort sort = Sort.by(Sort.Order.asc("discounthead"), Sort.Order.asc("monthMaster"));
        return repository.findAllBySchool_IdAndAcademicYear_IdOrderByDiscountheadAscMonthMasterAsc(school_id, academic_id, sort);
    }

    public List<DiscountMonthMap> getAllDiscountMonthMapByDiscount(Long school_id, Long academic_id, Long discount_id){
        return repository.findAllBySchool_IdAndAcademicYear_IdAndDiscounthead_Id(school_id, academic_id, discount_id);
    }

    public Optional<DiscountMonthMap> getDiscountMonthMapById(Long id){
        return repository.findById(id);
    }

    public List<DiscountMonthMap> saveAllDiscountMonths(List<DiscountMonthMap> discountMonthMaps){
        return repository.saveAll(discountMonthMaps);
    }

    public DiscountMonthMap saveDiscountMonth(DiscountMonthMap discountMonthMap){
        return repository.save(discountMonthMap);
    }

    public String delete(Long id){
        try{
            repository.deleteById(id);
        }catch(Exception e){
            throw new ObjectNotDeleteException("Error in deletion "+e.getLocalizedMessage());
        }
        return "success";
    }

}

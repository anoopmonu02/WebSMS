package com.smsweb.sms.services.admin;


import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.models.admin.DiscountMonthMap;
import com.smsweb.sms.repositories.admin.DiscountmonthmapRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class DiscountmonthmapService {
    private static final Logger log = LoggerFactory.getLogger(DiscountmonthmapService.class);

    private final DiscountmonthmapRepository repository;

    @Autowired
    public DiscountmonthmapService(DiscountmonthmapRepository repository){
        this.repository = repository;
    }

    public List<DiscountMonthMap> getAllDiscountMonthMap(Long school_id, Long academic_id){
        log.info("Inside getAllDiscountMonthMap");
        Sort sort = Sort.by(Sort.Order.asc("discounthead"), Sort.Order.asc("monthMaster"));
        return repository.findAllBySchool_IdAndAcademicYear_IdOrderByDiscountheadAscMonthMasterAsc(school_id, academic_id, sort);
    }

    public List<DiscountMonthMap> getAllDiscountMonthMapByDiscount(Long school_id, Long academic_id, Long discount_id){
        log.info("Inside getAllDiscountMonthMapByDiscount");
        return repository.findAllBySchool_IdAndAcademicYear_IdAndDiscounthead_Id(school_id, academic_id, discount_id);
    }

    public Optional<DiscountMonthMap> getDiscountMonthMapById(Long id){
        return repository.findById(id);
    }

    public List<DiscountMonthMap> saveAllDiscountMonths(List<DiscountMonthMap> discountMonthMaps){
        log.info("Inside saveAllDiscountMonths");
        return repository.saveAll(discountMonthMaps);
    }

    public DiscountMonthMap saveDiscountMonth(DiscountMonthMap discountMonthMap){
        log.info("Inside saveDiscountMonth");
        return repository.save(discountMonthMap);
    }

    public String delete(Long id){
        log.info("Inside delete");
        try{
            repository.deleteById(id);
        }catch(Exception e){
            throw new ObjectNotDeleteException("Error in deletion "+e.getLocalizedMessage());
        }
        return "success";
    }

}

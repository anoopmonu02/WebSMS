package com.smsweb.sms.services.admin;


import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.FeeDate;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.repositories.admin.FeedateRepository;
import com.smsweb.sms.repositories.universal.MonthMasterRepository;
import com.smsweb.sms.services.users.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FeedateService {
    private final FeedateRepository feedateRepository;
    private final MonthMasterRepository monthMasterRepository;
    private final UserService userService;

    @Autowired
    public FeedateService(FeedateRepository feedateRepository, MonthMasterRepository monthMasterRepository, UserService userService){
        this.feedateRepository = feedateRepository;
        this.monthMasterRepository = monthMasterRepository;
        this.userService = userService;
    }

    public List<FeeDate> getAllFeeDates(Long academicYear_id, Long school_id){
        return feedateRepository.findAllByAcademicYear_IdAndSchool_IdOrderByIdDesc(academicYear_id, school_id);
    }

    public FeeDate getFeeDate(Long academicYear_id, Long school_id, Long month_id){
        return feedateRepository.findByAcademicYear_IdAndSchool_IdAndMonthMaster_Id(academicYear_id, school_id, month_id).get();
    }

    public FeeDate save(FeeDate feeDate){
        try{
            feeDate.setCreatedBy(userService.getLoggedInUser());
            return feedateRepository.save(feeDate);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Fee date already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save mapping", e);
        }
    }

    public String delete(Long id){
        try{
            feedateRepository.deleteById(id);
        }catch(Exception e){
            throw new RuntimeException("Error in deletion "+e.getLocalizedMessage());
        }
        return "success";
    }

    public List<FeeDate> getByGivenMonth(Long academicYear_id, Long school_id, int monthGiven){
        return feedateRepository.findByAcademicYearAndSchoolAndGivenMonth(academicYear_id, school_id, monthGiven);
    }

}

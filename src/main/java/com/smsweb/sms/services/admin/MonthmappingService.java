package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.repositories.admin.MonthmappingRepository;
import com.smsweb.sms.repositories.universal.MonthMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MonthmappingService {
    private final MonthmappingRepository monthmappingRepository;
    private final MonthMasterRepository monthMasterRepository;

    @Autowired
    public MonthmappingService(MonthmappingRepository monthmappingRepository, MonthMasterRepository monthMasterRepository){
        this.monthmappingRepository = monthmappingRepository;
        this.monthMasterRepository = monthMasterRepository;
    }

    public List<MonthMapping> getAllMonthMapping(Long academicYear_id, Long school_id){
        return monthmappingRepository.findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYear_id, school_id);
    }

    public String save(MonthMaster startMonth, AcademicYear academicYear, School school){
        String msg = "fail";
        try{
            boolean checkExistingMapping = deleteExistingMapping(academicYear, school);
            if(checkExistingMapping){
                int priority = 1;
                Long startingMonth = startMonth.getId();
                for(Long i=startingMonth;i<13;i++){
                    saveMapping(academicYear, school, priority, i);
                    priority++;
                }
                if(startingMonth>0L){
                    for(Long j=1L; j<startingMonth;j++){
                        saveMapping(academicYear, school, priority, j);
                        priority++;
                    }
                }
                msg = "success";
            }
        }catch(Exception e){
            msg = "fail";
            e.printStackTrace();
            throw new RuntimeException("Error in generating month-mapping: "+e.getMessage());
        }
        return msg;
    }

    public void saveMapping(AcademicYear academicYear, School school, int priority, Long monthId) throws RuntimeException{
        MonthMapping monthMapping = new MonthMapping();
        monthMapping.setMonthMaster(monthMasterRepository.findById(monthId).get());
        monthMapping.setPriority(priority);
        monthMapping.setAcademicYear(academicYear);
        monthMapping.setSchool(school);
        monthmappingRepository.save(monthMapping);
    }

    public boolean deleteExistingMapping(AcademicYear academicYear, School school){
        boolean flag = true;
        try{
            List<MonthMapping> monthMappings = monthmappingRepository.findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYear.getId(), school.getId());
            if(monthMappings!=null && !monthMappings.isEmpty()){
                monthmappingRepository.deleteAll(monthMappings);
                flag = true;
            }
        }catch(Exception e){
            e.printStackTrace();
            flag = false;
        }
        return flag;
    }


}

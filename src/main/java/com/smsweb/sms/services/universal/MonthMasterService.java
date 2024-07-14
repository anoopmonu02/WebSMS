package com.smsweb.sms.services.universal;

import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.repositories.universal.MonthMasterRepository;
import com.smsweb.sms.services.admin.MonthmappingService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MonthMasterService {
    private final MonthMasterRepository monthMasterRepository;

    public MonthMasterService(MonthMasterRepository monthMasterRepository){
        this.monthMasterRepository = monthMasterRepository;
    }

    public List<MonthMaster> getAllMonths(){ return monthMasterRepository.findAll(); }

    public Optional<MonthMaster> getMonthById(Long id){ return monthMasterRepository.findById(id);}
}

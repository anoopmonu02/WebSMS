package com.smsweb.sms.services.admin;


import com.smsweb.sms.models.admin.Fine;
import com.smsweb.sms.repositories.admin.FineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FineService {
    private final FineRepository fineRepository;

    @Autowired
    public FineService(FineRepository fineRepository){
        this.fineRepository = fineRepository;
    }

    public List<Fine> getAllFines(){
        Long school_id = 4L;
        Long academic_id =14L;
        return fineRepository.findAllByAcademicYear_IdAndSchool_Id(academic_id, school_id);
    }

    public Fine getFine(Long id){
        return fineRepository.findById(id).get();
    }

    
}

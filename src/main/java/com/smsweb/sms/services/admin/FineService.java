package com.smsweb.sms.services.admin;


import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.admin.Fine;
import com.smsweb.sms.repositories.admin.FineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FineService {
    private final FineRepository fineRepository;

    @Autowired
    public FineService(FineRepository fineRepository){
        this.fineRepository = fineRepository;
    }

    public List<Fine> getAllFines(Long school_id, Long academic_id){
        return fineRepository.findAllByAcademicYear_IdAndSchool_Id(academic_id, school_id);
    }

    public Optional<Fine> getFineById(Long id){
        return fineRepository.findById(id);
    }

    public Fine saveFine(Fine fine){
        try{
            return fineRepository.save(fine);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Fine already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save Fine", e);
        }
    }

    public String deleteFine(Long id){
        try{
            fineRepository.deleteById(id);
            return "success";
        } catch(Exception e){
            throw new ObjectNotSaveException("Unable to delete Fine", e);
        }
    }
}

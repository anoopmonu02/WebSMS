package com.smsweb.sms.services.universal;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.universal.Feehead;
import com.smsweb.sms.repositories.universal.FeeheadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FeeheadService {
    private final FeeheadRepository feeheadRepository;
    @Autowired
    public FeeheadService(FeeheadRepository feeheadRepository){
        this.feeheadRepository = feeheadRepository;
    }

    public List<Feehead> getAllFeeheads(){
        return feeheadRepository.findAll(Sort.by(Sort.DEFAULT_DIRECTION, "id"));
    }

    public Feehead saveFeehead(Feehead feehead) {
        try{
            return feeheadRepository.save(feehead);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Fee-Head already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save fee head: "+e.getLocalizedMessage());
        }
    }

    public Optional<Feehead> getFeeheadById(Long id) {
        return feeheadRepository.findById(id);
    }

    public Feehead findByFeeheadName(String feeheadname){
        return feeheadRepository.findByFeeHeadName(feeheadname);
    };

    public void deleteFeehead(Long id) {
        feeheadRepository.deleteById(id);
    }
}

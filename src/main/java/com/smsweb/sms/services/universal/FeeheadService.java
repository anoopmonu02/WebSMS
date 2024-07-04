package com.smsweb.sms.services.universal;

import com.smsweb.sms.models.universal.Feehead;
import com.smsweb.sms.repositories.universal.FeeheadRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    public void saveFeehead(Feehead feehead) {
        feeheadRepository.save(feehead);
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

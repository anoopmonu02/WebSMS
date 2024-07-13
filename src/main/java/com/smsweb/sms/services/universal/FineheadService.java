package com.smsweb.sms.services.universal;

import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.models.universal.Finehead;
import com.smsweb.sms.repositories.universal.FineheadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FineheadService {
    private final FineheadRepository fineheadRepository;

    @Autowired
    public FineheadService(FineheadRepository fineheadRepository){
        this.fineheadRepository = fineheadRepository;
    }

    public List<Finehead> getAllFineHeads(){
        return fineheadRepository.findAll(Sort.by(Sort.DEFAULT_DIRECTION, "id"));
    }

    public void saveFinehead(Finehead finehead) {
        fineheadRepository.save(finehead);
    }

    public Optional<Finehead> getFineheadById(Long id) {
        return fineheadRepository.findById(id);
    }

    public Finehead findByFineHeadName(String fineHeadName){
        return fineheadRepository.findByFineHeadName(fineHeadName);
    }

    public void deleteFinehead(Long id) {
        fineheadRepository.deleteById(id);
    }
}

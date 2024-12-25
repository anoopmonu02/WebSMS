package com.smsweb.sms.services.universal;


import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.repositories.universal.MediumRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MediumService {

    private final MediumRepository mediumRepository;

    @Autowired
    public MediumService(MediumRepository mediumRepository) {
        this.mediumRepository = mediumRepository;
    }

    public List<Medium> getAllMediums() {
        return mediumRepository.findAll();
    }

    public Medium saveMedium(Medium medium) {
        try{
            return mediumRepository.save(medium);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Medium already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save medium: "+e.getLocalizedMessage());
        }
    }

    public Optional<Medium> getMediumById(Long id) {
        return mediumRepository.findById(id);
    }

    public void deleteMedium(Long id) {
        mediumRepository.deleteById(id);
    }

}

package com.smsweb.sms.services.universal;


import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.repositories.universal.MediumRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    public void saveMedium(Medium medium) {
        mediumRepository.save(medium);
    }

    public Optional<Medium> getMediumById(Long id) {
        return mediumRepository.findById(id);
    }

    public void deleteMedium(Long id) {
        mediumRepository.deleteById(id);
    }

}

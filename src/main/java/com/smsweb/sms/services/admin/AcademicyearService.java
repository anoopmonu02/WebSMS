package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AcademicyearService {
    private final AcademicyearRepository academicyearRepository;

    @Autowired
    public AcademicyearService(AcademicyearRepository academicyearRepository){
        this.academicyearRepository = academicyearRepository;
    }

    public List<AcademicYear> getAllAcademiyears(){
        return academicyearRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public Optional<AcademicYear> getAcademicyearById(Long id){
        return academicyearRepository.findById(id);
    }

    @Transactional
    public AcademicYear save(AcademicYear academicYear){
        academicyearRepository.save(academicYear);
        return academicYear;
    }

    public AcademicYear getCurrentAcademicYear(){
        return academicyearRepository.findTopByStatusOrderByIdDesc("active");
    }
}

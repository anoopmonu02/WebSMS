package com.smsweb.sms.services.universal;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.repositories.universal.GradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class GradeService {
    private static final Logger log = LoggerFactory.getLogger(GradeService.class);

    private final GradeRepository gradeRepository;

    @Autowired
    public GradeService(GradeRepository gradeRepository) {
        this.gradeRepository = gradeRepository;
    }

    public List<Grade> getAllGrades() {
        return gradeRepository.findAll();
    }

    public Grade saveGrade(Grade grade) {
        log.info("Inside saveGrade");
        try{
            return gradeRepository.save(grade);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Grade already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save grade: "+e.getLocalizedMessage());
        }
    }

    public Optional<Grade> getGradeById(Long id) {
        return gradeRepository.findById(id);
    }

    public void deleteGrade(Long id) {
        log.info("Inside deleteGrade");
        gradeRepository.deleteById(id);
    }
}

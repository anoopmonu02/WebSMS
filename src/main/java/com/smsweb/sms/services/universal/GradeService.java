package com.smsweb.sms.services.universal;

import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.repositories.universal.GradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GradeService {
    private final GradeRepository gradeRepository;

    @Autowired
    public GradeService(GradeRepository gradeRepository) {
        this.gradeRepository = gradeRepository;
    }

    public List<Grade> getAllGrades() {
        return gradeRepository.findAll();
    }

    public void saveGrade(Grade grade) {
        gradeRepository.save(grade);
    }

    public Optional<Grade> getGradeById(Long id) {
        return gradeRepository.findById(id);
    }

    public void deleteGrade(Long id) {
        gradeRepository.deleteById(id);
    }
}

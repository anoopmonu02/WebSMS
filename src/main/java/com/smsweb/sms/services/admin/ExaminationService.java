package com.smsweb.sms.services.admin;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.admin.ExamDetails;
import com.smsweb.sms.models.admin.Examination;
import com.smsweb.sms.repositories.admin.ExamDetailsRepository;
import com.smsweb.sms.repositories.admin.ExaminationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class ExaminationService {
    private static final Logger log = LoggerFactory.getLogger(ExaminationService.class);

    private final ExaminationRepository examinationRepository;
    private final ExamDetailsRepository examDetailsRepository;

    @Autowired
    public ExaminationService(ExaminationRepository examinationRepository, ExamDetailsRepository examDetailsRepository) {
        this.examinationRepository = examinationRepository;
        this.examDetailsRepository = examDetailsRepository;
    }

    public List<Examination> getAllExamination(){
        return examinationRepository.findAll();
    }

    public Examination save(Examination examination){
        log.info("Inside save");
        try{
            return examinationRepository.save(examination);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Examination already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save examination", e);
        }
    }

    public String deleteExamination(String uuid){
        log.info("Inside deleteExamination");
        try{
            Long id = examinationRepository.findByUuid(UUID.fromString(uuid)).get().getId();
            examinationRepository.deleteById(id);
            return "success";
        } catch(Exception e){
            throw new ObjectNotSaveException("Unable to delete examination", e);
        }
    }

    public List<ExamDetails> getAllExaminationDates(Long academic_id, Long school_id){
        log.info("Inside getAllExaminationDates");
        return examDetailsRepository.findAllByAcademicYear_IdAndSchool_Id(academic_id, school_id);
    }

    public ExamDetails saveExamDetails(ExamDetails examDetails){
        log.info("Inside saveExamDetails");
        try{
            return examDetailsRepository.save(examDetails);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Examination details already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save examination details", e);
        }
    }

    public String deleteExamDetails(String uuid){
        log.info("Inside deleteExamDetails");
        try{
            Long id = examDetailsRepository.findByUuid(UUID.fromString(uuid)).get().getId();
            examDetailsRepository.deleteById(id);
            return "success";
        } catch(Exception e){
            throw new ObjectNotSaveException("Unable to delete examination", e);
        }
    }

    public ExamDetails getExamDetailByName(String examName, Long academic_id, Long school_id){
        log.info("Inside getExamDetailByName");
        return examDetailsRepository.findByExaminationExaminationNameAndAcademicYear_IdAndSchool_Id(examName, academic_id, school_id);
    }
    public ExamDetails getExamDetailById(Long examName, Long academic_id, Long school_id){
        log.info("Inside getExamDetailById");
        return examDetailsRepository.findByExamination_IdAndAcademicYear_IdAndSchool_Id(examName, academic_id, school_id);
    }

    /** Fetch ExamDetails directly by its own PK — used when dropdown already sends ExamDetails.id */
    public ExamDetails getExamDetailByDetailsId(Long examDetailsId){
        return examDetailsRepository.findById(examDetailsId).orElse(null);
    }
}

package com.smsweb.sms.services.student;

import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.repositories.student.StudentDiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StudentDiscountService {
    private final StudentDiscountRepository studentDiscountRepository;

    @Autowired
    public StudentDiscountService(StudentDiscountRepository studentDiscountRepository){
        this.studentDiscountRepository = studentDiscountRepository;
    }

    public List<StudentDiscount> getAllStudentDiscounts(Long school_id, Long academic_id){
        return studentDiscountRepository.findAllBySchool_IdAndAcademicYear_Id(school_id, academic_id);
    }

    public Optional<StudentDiscount> getStudentDiscountForStudent(Long school_id, Long academic_id, Long stuId){
        return studentDiscountRepository.findBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(school_id, academic_id, stuId);
    }

    public StudentDiscount save(StudentDiscount studentDiscount){
        try{
            studentDiscountRepository.save(studentDiscount);
            return studentDiscount;
        } catch(DataIntegrityViolationException de){
            throw new DataIntegrityViolationException("Data already saved", de);
        } catch(ObjectNotSaveException oe){
            throw new ObjectNotSaveException("Error in saving", oe);
        }
    }

    public String deleteStudentDiscount(Long id){
        try{
            studentDiscountRepository.deleteById(id);
            return "success";
        }catch(Exception e){
            throw new ObjectNotDeleteException("Unable to delete Fine", e);
        }
    }

}

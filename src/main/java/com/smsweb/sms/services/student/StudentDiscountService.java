package com.smsweb.sms.services.student;

import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.repositories.student.StudentDiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StudentDiscountService {
    private final StudentDiscountRepository studentDiscountRepository;

    @Autowired
    public StudentDiscountService(StudentDiscountRepository studentDiscountRepository){
        this.studentDiscountRepository = studentDiscountRepository;
    }

    public List<StudentDiscount> getAllStudentDiscounts(Long school_id, Long academic_id){
        return studentDiscountRepository.findAllBySchool_IdAndAcademicYear_IdAndStatus(school_id, academic_id, "Active");
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
            throw new ObjectNotDeleteException("Unable to delete discount mapping", e);
        }
    }

    public String deactivateStudentDiscount(Long id){
        try{
            StudentDiscount studentDiscount = studentDiscountRepository.findById(id).orElse(null);
            if(studentDiscount!=null){
                studentDiscount.setStatus("Inactive");
                studentDiscountRepository.save(studentDiscount);
                return "success";
            } else{
                return "not-found";
            }
        }catch(Exception e){
            throw new ObjectNotDeleteException("Unable to delete discount mapping", e);
        }
    }

    public List<Map<String, String>> getAllStudentDiscountsBySession(Long school_id, Long academic_id){
        List<StudentDiscount> stuList = studentDiscountRepository.findAllBySchool_IdAndAcademicYear_IdAndStatus(school_id, academic_id, "Active");
        List<Map<String, String>> stuDiscList = new ArrayList<>();
        //Form map which data should show
        if(stuList!=null && !stuList.isEmpty()){
            for(StudentDiscount studentDiscount: stuList){
                Map<String, String> stuDisMap = new HashMap<>();
                String grade = studentDiscount.getAcademicStudent().getGrade().getGradeName() + " - " + studentDiscount.getAcademicStudent().getSection().getSectionName();
                stuDisMap.put("studentName", studentDiscount.getAcademicStudent().getStudent().getStudentName());
                stuDisMap.put("srNo", studentDiscount.getAcademicStudent().getClassSrNo());
                stuDisMap.put("grade", grade);
                stuDisMap.put("fatherName", studentDiscount.getAcademicStudent().getStudent().getFatherName());
                stuDisMap.put("motherName", studentDiscount.getAcademicStudent().getStudent().getMotherName());
                stuDisMap.put("discount", studentDiscount.getDiscounthead().getDiscountName());
                stuDisMap.put("description", studentDiscount.getDescription());
                String assignedBy = "";
                if (studentDiscount.getCreatedBy() != null) {
                    assignedBy = studentDiscount.getCreatedBy().getDisplayName();
                }
                stuDisMap.put("assignedBy", assignedBy);
                stuDiscList.add(stuDisMap);
            }
        }
        return stuDiscList;
    }

}

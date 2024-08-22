package com.smsweb.sms.services.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.DiscountClassMap;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.admin.DiscountclassmapRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.SiblingDiscountRepository;
import com.smsweb.sms.repositories.student.SiblingGroupStudentRepository;
import com.smsweb.sms.repositories.student.StudentDiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class SiblingDiscountService {
    private SiblingDiscountRepository siblingDiscountRepository;
    private final AcademicyearRepository academicyearRepository;
    private final SchoolRepository schoolRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final SiblingGroupStudentRepository siblingGroupStudentRepository;
    private final DiscountclassmapRepository discountclassmapRepository;
    private final StudentDiscountRepository studentDiscountRepository;

    @Autowired
    public SiblingDiscountService(SiblingDiscountRepository siblingDiscountRepository, AcademicyearRepository academicyearRepository, SchoolRepository schoolRepository, AcademicStudentRepository academicStudentRepository, SiblingGroupStudentRepository siblingGroupStudentRepository, DiscountclassmapRepository discountclassmapRepository, StudentDiscountRepository studentDiscountRepository){
        this.siblingDiscountRepository = siblingDiscountRepository;
        this.academicyearRepository = academicyearRepository;
        this.schoolRepository = schoolRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.siblingGroupStudentRepository = siblingGroupStudentRepository;
        this.discountclassmapRepository = discountclassmapRepository;
        this.studentDiscountRepository = studentDiscountRepository;
    }

    @Transactional
    public Map save(Map<String, String[]> paramsMap){
        Map<String, String> resultMap = new HashMap();
        try{
            if(paramsMap!=null && !paramsMap.isEmpty()){
                AcademicYear academicYear = academicyearRepository.findById(14L).orElse(null);
                School school = schoolRepository.findById(4L).orElse(null);
                Long groupId = null;
                Long stuId = null;
                for (Map.Entry<String, String[]> entry : paramsMap.entrySet()) {
                    String key = entry.getKey();
                    String[] values = entry.getValue();
                    System.out.println("Key: " + key);
                    System.out.println("Values:"+values);
                    for (String value : values) {
                        System.out.println(" - " + value);
                        if(key.equalsIgnoreCase("grp") && value!=null && value!=""){
                            groupId = Long.parseLong(value);
                        } else if(key.equalsIgnoreCase("assignedstu") && value!=null && value!=""){
                            stuId = Long.parseLong(value);
                        }
                    }
                }
                if(stuId!=null && groupId!=null){
                    Optional<AcademicStudent> student = academicStudentRepository.findById(stuId);
                    if(student.isPresent()){
                        Optional<DiscountClassMap> discountClassMap = discountclassmapRepository.findByDiscounthead_DiscountNameAndAcademicYear_IdAndSchool_IdAndGrade_Id("Sibling Discount", 14L, 4L, student.get().getGrade().getId());
                        if(discountClassMap.isPresent()){
                            StudentDiscount studentDiscount = new StudentDiscount();
                            studentDiscount.setAcademicStudent(student.get());
                            studentDiscount.setSchool(school);
                            studentDiscount.setAcademicYear(academicYear);
                            studentDiscount.setDiscounthead(discountClassMap.get().getDiscounthead());
                            studentDiscount.setDescription("Sibling Discount Mapped");
                            studentDiscountRepository.save(studentDiscount);
                            resultMap.put("DISCOUNT_SAVED", "Discount saved for Student: "+student.get().getStudent().getStudentName());
                        } else{
                            resultMap.put("NO_DISCOUNT_FOUND","Discount not mapped with Grade: "+student.get().getGrade().getGradeName());
                        }
                    } else{
                        resultMap.put("NO_STU_FOUND","Student not found");
                    }
                } else{
                    resultMap.put("NO_STU_OR_GRP_FOUND","Fail to process request");
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            resultMap.put("error", "Error: "+e.getLocalizedMessage());
        }
        return resultMap;
    }
}

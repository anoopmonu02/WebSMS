package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.FeeDate;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.FeedateService;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class FeeSubmissionRestController {

    private final StudentService studentService;
    private final AcademicStudentService academicStudentService;
    private final AcademicyearService academicyearService;

    private final FeeSubmissionService feeSubmissionService;
    private final FeedateService feedateService;

    @Autowired
    public FeeSubmissionRestController(StudentService studentService, AcademicyearService academicyearService, AcademicStudentService academicStudentService,
                                       FeeSubmissionService feeSubmissionService, FeedateService feedateService) {
        this.studentService = studentService;
        this.academicyearService = academicyearService;
        this.academicStudentService = academicStudentService;
        this.feeSubmissionService = feeSubmissionService;
        this.feedateService = feedateService;
    }

    @GetMapping("/searchStudentForFeePage/{query}")
    public ResponseEntity<?> searchStudentForFeePage(@PathVariable("query") String query){
        //List<Student> students = studentService.searchStudent(query);
        List<AcademicStudent> students = academicStudentService.searchStudents(query, 14L, 4L);
        return ResponseEntity.ok(students);
    }

    @GetMapping("/getStudentDetailForFee/{id}")
    public ResponseEntity<?> getStudentDetailForFee(@PathVariable("id") Long id){
        Map result = new HashMap<>();
        //first fetch student details like fathername/mothername/class/section/contact-no/student type[old/new]/ etc.
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, 14L, 4L);
        try{
            if(academicStudent!=null){
                Student student = academicStudent.getStudent();
                AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
                //TODO - fetch previous pending if any

                //Calculating current month -
                //TODO - Can get date from server, so no dependent on client machine date, for now using local date
                int currentMonth = Calendar.getInstance().get(Calendar.MONTH)+1;
                FeeDate fd = feedateService.getByGivenMonth(14L, 4L, currentMonth);
                if (fd != null) {
                    //if any pending fees left then redirect to pending fee page
                    //NEED TO DISCUSS HOW TO HANDLE THIS:- 1). REDIRECT TO PENDING PAGE 2). OPEN A POPUP MODEL AND SUBMIT
                    //Calculate lastsubmittedfees for previous balance pending if any
                    if(1==2){
                        //to calculate the previous pending fee
                    } else{
                        result.put("student",academicStudent);
                        result.put("feeDate",fd);
                        result.put("todayDate",new Date());
                        result.put("countStu", academicStudentService.countNoOfYearsOfStudent(academicStudent)>1?"OLD":"NEW");
                        result.put("academicYear", academicYear);
                        FeeSubmission feeSubmission = feeSubmissionService.getLastFeeSubmissionOfStudentForBalance(4L, 14L, academicStudent.getId());
                        if(feeSubmission!=null){
                            FeeSubmission feeSub = feeSubmission;
                            result.put("previousBalance",feeSub.getFeeSubmissionBalance().getBalanceAmount());
                        } else{
                            result.put("previousBalance",0.0);
                        }
                    }
                } else{
                    //Fee date is missing.
                    result.put("noFeeDate", "No Fee-Date found, Please add fee-date first.");
                }
                //Calculate Paid months
                Map paidMonths = feeSubmissionService.getPaidMonths(4L, 14L, academicStudent.getId());
                if(paidMonths!=null && !paidMonths.isEmpty()){
                    result.put("PaidMonths", paidMonths.get("paidMonths"));
                } else{
                    result.put("Paid-Month-Error", "Paid mount not found");
                }
            } else{
                result.put("noAcademicStudent", "Student:"+ academicStudent.getStudent().getStudentName() +" not found.");
            }
            System.out.println("Result----"+result);

        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }

}

package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FeeSubmissionRestController {

    private final StudentService studentService;
    private final AcademicStudentService academicStudentService;
    private final AcademicyearService academicyearService;

    @Autowired
    public FeeSubmissionRestController(StudentService studentService, AcademicyearService academicyearService, AcademicStudentService academicStudentService) {
        this.studentService = studentService;
        this.academicyearService = academicyearService;
        this.academicStudentService = academicStudentService;
    }

    @GetMapping("/searchStudentForFeePage/{query}")
    public ResponseEntity<?> searchStudentForFeePage(@PathVariable("query") String query){
        //List<Student> students = studentService.searchStudent(query);
        List<AcademicStudent> students = academicStudentService.searchStudents(query, 14L, 4L);
        return ResponseEntity.ok(students);
    }

    @GetMapping("/getStudentDetailForFee/{id}")
    public ResponseEntity<?> getStudentDetailForFee(@PathVariable("id") Long id){
        //first fetch student details like fathername/mothername/class/section/contact-no/student type[old/new]/ etc.
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, 14L, 4L);
        if(academicStudent!=null){

        }
        //secondly fetch the fee already submitted detail like months
        //third fetch any fees pending already
        return ResponseEntity.ok(null);
    }

}

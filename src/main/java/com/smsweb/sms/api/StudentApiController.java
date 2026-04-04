package com.smsweb.sms.api;

import com.smsweb.sms.api.dto.StudentProfileDto;
import com.smsweb.sms.api.dto.StudentProfileRequest;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;

@RestController
@RequestMapping("/api/V1/Students")
public class StudentApiController {

    private final AcademicStudentRepository academicStudentRepository;

    public StudentApiController(AcademicStudentRepository academicStudentRepository) {
        this.academicStudentRepository = academicStudentRepository;
    }

    @Transactional
    @PostMapping("/profile")
    public ResponseEntity<?> getStudentProfile(@RequestBody StudentProfileRequest request) {

        // validate request
        if (request.getStudentId() == null ||
                request.getBranchId()  == null ||
                request.getSessionId() == null) {
            return ResponseEntity.ok(
                    new StudentProfileDto(0, "student_id, branch_id and session_id are required")
            );
        }

        AcademicStudent academic = academicStudentRepository
                .findStudentProfile(
                        request.getStudentId(),
                        request.getBranchId(),
                        request.getSessionId()
                )
                .orElse(null);

        if (academic == null) {
            return ResponseEntity.ok(
                    new StudentProfileDto(0, "Student not found for given session and branch")
            );
        }

        // personal info from Student
        Student student = academic.getStudent();

        // format dob
        String dob = null;
        if (student.getDob() != null) {
            dob = new SimpleDateFormat("dd-MM-yyyy").format(student.getDob());
        }

        // school info
        Long   customerId = null;   // Customer.id → school_id
        String schoolName = null;
        if (academic.getSchool() != null) {
            schoolName = academic.getSchool().getSchoolName();
            if (academic.getSchool().getCustomer() != null) {
                customerId = academic.getSchool().getCustomer().getId();
            }
        }

        // session info
        Long   sessionId   = null;
        String sessionName = null;
        if (academic.getAcademicYear() != null) {
            sessionId   = academic.getAcademicYear().getId();
            sessionName = academic.getAcademicYear().getSessionFormat();
        }

        // grade, section, medium from AcademicStudent
        String gradeName   = academic.getGrade()   != null ? academic.getGrade().getGradeName()     : null;
        String sectionName = academic.getSection() != null ? academic.getSection().getSectionName() : null;
        String mediumName  = academic.getMedium()  != null ? academic.getMedium().getMediumName()   : null;

        StudentProfileDto dto = new StudentProfileDto(
                student.getId(),
                student.getRegistrationNo(),
                academic.getClassSrNo(),          // ✅ AcademicStudent.classSrNo
                academic.getBoardSrNo(),          // ✅ AcademicStudent.boardSrNo
                academic.getRollNo(),             // ✅ AcademicStudent.rollNo
                student.getStudentName(),
                student.getFatherName(),
                student.getMotherName(),
                student.getGender(),
                dob,
                student.getBloodGroup(),
                student.getNationality(),
                student.getReligion(),
                student.getMobile1(),
                student.getMobile2(),
                student.getAddress(),
                student.getPincode(),
                gradeName,
                sectionName,
                mediumName,
                customerId,                       // Customer.id → school_id
                academic.getSchool() != null ? academic.getSchool().getId() : null,  // School.id → branch_id
                sessionId,
                sessionName,
                schoolName,
                student.getStatus(),
                student.getPic(),
                student.getAadharNo(),
                student.getApaarId(),
                student.getPenNo()
        );

        return ResponseEntity.ok(dto);
    }
}


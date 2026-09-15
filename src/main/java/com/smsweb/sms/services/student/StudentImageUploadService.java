package com.smsweb.sms.services.student;

import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NEW, isolated service backing the "Update Student Images (Group-wise)"
 * page. Does not modify StudentService, StudentBulkUpdateService, or any
 * other existing service - it only reads/writes Student.pic, using the exact
 * same AcademicStudentRepository/StudentRepository/FileHandleHelper every
 * other student-photo path (add-student, edit-student, the mobile app)
 * already uses, so storage location and the /student/images/{filename}
 * serving endpoint stay identical.
 *
 * Row-wise, not batch: saveStudentImage() handles exactly one student's photo
 * per call, matching the page's one-Save-button-per-row design (there is no
 * page-wide "Save Changes" step here, unlike Update Student Details).
 */
@Service
public class StudentImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(StudentImageUploadService.class);

    @Autowired
    private AcademicStudentRepository academicStudentRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FileHandleHelper fileHandleHelper;

    public List<Map<String, Object>> getRowsForImageUpload(Long mediumId, Long gradeId, Long sectionId,
                                                             Long academicYearId, Long schoolId) {
        log.info("Inside getRowsForImageUpload");
        List<AcademicStudent> list = academicStudentRepository
                .findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatusIgnoreCase(
                        schoolId, mediumId, gradeId, sectionId, academicYearId, "Active");

        List<Map<String, Object>> rows = new ArrayList<>();
        int sno = 1;
        for (AcademicStudent as : list) {
            Student s = as.getStudent();
            if (s == null || as.getUuid() == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sno", sno++);
            row.put("uuid", as.getUuid().toString());
            row.put("studentName", nvl(s.getStudentName()));
            row.put("fatherName", nvl(s.getFatherName()));
            row.put("motherName", nvl(s.getMotherName()));
            row.put("psrn", s.getPsrn() != null ? s.getPsrn() : "");
            row.put("pic", nvl(s.getPic()));
            rows.add(row);
        }
        return rows;
    }

    /**
     * Saves a single row's photo. Same anti-IDOR check saveFieldGroup() uses
     * elsewhere in this codebase: the target student is looked up by uuid and
     * MUST belong to the current school/academic year (both session-resolved,
     * never trusted from the request), or nothing is saved.
     *
     * File validation (image type, 2MB cap) is entirely FileHandleHelper.saveImage's
     * existing logic, reused verbatim - the project owner explicitly chose to
     * keep the existing 2MB limit rather than add a new, separate check here.
     * The one thing this method adds beyond what add/edit-student already does:
     * once the NEW photo has saved successfully, the student's PREVIOUS photo
     * file (if any) is deleted from disk, so repeated re-uploads through this
     * bulk page don't leak orphaned files the way add/edit-student's flow still
     * does (left untouched, per the project owner's explicit choice).
     */
    @Transactional
    public Map<String, Object> saveStudentImage(String uuid, MultipartFile file, Long academicYearId, Long schoolId) {
        if (uuid == null || uuid.isBlank()) {
            return result(uuid, false, "Missing student reference.", null);
        }
        AcademicStudent as;
        try {
            as = academicStudentRepository.findByUuid(UUID.fromString(uuid)).orElse(null);
        } catch (IllegalArgumentException e) {
            return result(uuid, false, "Missing student reference.", null);
        }
        if (as == null || as.getStudent() == null
                || as.getSchool() == null || !as.getSchool().getId().equals(schoolId)
                || as.getAcademicYear() == null || !as.getAcademicYear().getId().equals(academicYearId)) {
            return result(uuid, false, "Student not found for the current school/academic year.", null);
        }
        if (file == null || file.isEmpty()) {
            return result(uuid, false, "Please choose an image to upload.", null);
        }

        Student student = as.getStudent();
        String previousPic = student.getPic();
        String imageResponse;
        try {
            imageResponse = fileHandleHelper.saveImage("student", file);
        } catch (IOException e) {
            log.error("Error saving image for student uuid={}", uuid, e);
            return result(uuid, false, "An error occurred while saving the image.", null);
        }

        boolean hasResponse = imageResponse != null && !imageResponse.isEmpty();
        if (!hasResponse || imageResponse.equalsIgnoreCase("Success_no_image")) {
            // MultipartFile wasn't actually empty (checked above), so this
            // shouldn't happen in practice, but treat it as "nothing to save"
            // rather than silently reporting success.
            return result(uuid, false, "Please choose an image to upload.", null);
        }
        if (imageResponse.equalsIgnoreCase("Either image format not supported or size exceeded 2MB.")) {
            return result(uuid, false, imageResponse, null);
        }
        if (imageResponse.startsWith("Failed to save the image: ")) {
            return result(uuid, false, imageResponse, null);
        }
        if (imageResponse.equalsIgnoreCase("Specified category not valid")) {
            return result(uuid, false, "An error occurred while saving the image.", null);
        }

        // imageResponse is the new saved filename.
        student.setPic(imageResponse);
        studentRepository.save(student);

        if (previousPic != null && !previousPic.isBlank() && !previousPic.equals(imageResponse)) {
            fileHandleHelper.deleteStudentImage(previousPic);
        }

        return result(uuid, true, "Photo saved", imageResponse);
    }

    private Map<String, Object> result(String uuid, boolean success, String message, String pic) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("uuid", uuid);
        r.put("success", success);
        r.put("message", message);
        r.put("pic", pic);
        return r;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}

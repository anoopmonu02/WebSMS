package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.controllers.employee.EmployeeController;
import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.repositories.admin.ExamDetailsRepository;
import com.smsweb.sms.repositories.admin.ExaminationRepository;
import com.smsweb.sms.repositories.student.AttendanceRepository;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.SchoolService;
import com.smsweb.sms.services.globalaccess.DropdownService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentService;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class StudentController extends BaseController {
    Logger log = LoggerFactory.getLogger(EmployeeController.class);
    private final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB

    @Value("${student.image.storage.path}")
    private String studentImageDirectory;
    private final String FORMAT_PREFIX = "ddMMyyyyhhmmss";
    private final StudentService studentService;
    private final AcademicStudentService academicStudentService;
    private final DropdownService dropdownService;
    private final AcademicyearService academicyearService;
    private final SchoolService schoolService;
    private final UserService userService;

    private final AttendanceRepository attendanceRepository;

    private final ExaminationRepository examinationRepository;
    private final ExamDetailsRepository examDetailsRepository;
    @Autowired
    public StudentController(StudentService studentService, AcademicStudentService academicStudentService, DropdownService dropdownService, AcademicyearService academicyearService, SchoolService schoolService, UserService userService, UserService userService1, AttendanceRepository attendanceRepository, ExaminationRepository examinationRepository, ExamDetailsRepository examDetailsRepository){
        this.studentService = studentService;
        this.academicStudentService = academicStudentService;
        this.dropdownService = dropdownService;
        this.academicyearService = academicyearService;
        this.schoolService = schoolService;
        this.userService = userService1;
        this.attendanceRepository = attendanceRepository;
        this.examinationRepository = examinationRepository;
        this.examDetailsRepository = examDetailsRepository;
    }

    @CheckAccess(screen = "STUDENT_LIST", type = AccessType.VIEW)
    @GetMapping("/student")
    public String studentData(Model model){
        log.debug("inside student list");
        if(isSuperAdminLoggedIn()){
            model.addAttribute("hasSuperAdmin", true);
        }
        model.addAttribute("hasStudent", true);
        model.addAttribute("page", "datatable");
        return "student/student";
    }

    /** Server-side DataTable AJAX endpoint. */
    @ResponseBody
    @CheckAccess(screen = "STUDENT_LIST", type = AccessType.VIEW)
    @GetMapping("/student/data")
    public Map<String, Object> getStudentData(
            @RequestParam(defaultValue = "0")  int draw,
            @RequestParam(defaultValue = "0")  int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]",    defaultValue = "") String search,
            @RequestParam(name = "order[0][column]", defaultValue = "0") int sortCol,
            @RequestParam(name = "order[0][dir]",    defaultValue = "asc") String sortDir,
            Model model) {
        boolean superAdmin = isSuperAdminLoggedIn();
        Long schoolId = null;
        if (!superAdmin) {
            School school = (School) model.getAttribute("school");
            schoolId = school.getId();
        }
        return studentService.getStudentsPage(superAdmin, schoolId, draw, start, length, search, sortCol, sortDir);
    }

    private boolean isSuperAdminLoggedIn(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName(); // Get logged-in username
            if(authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"))){
                return true;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }

    @CheckAccess(screen = "STUDENT_ADD", type = AccessType.CREATE)
    @GetMapping("/student/add")
    public String addStudentData(Model model){
        Student student = new Student();
        student.setUserEntity(new UserEntity());
        model.addAttribute("student", student);
        model = getAllGlobalModels(model);
        if(isSuperAdminLoggedIn()){
            return "student/student";
        }
        return "student/add-student";
    }

    public Model getAllGlobalModels(Model model){
        model.addAttribute("categories", dropdownService.getCategories());
        model.addAttribute("casts", dropdownService.getCasts());
        model.addAttribute("provinces", dropdownService.getProvinces());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("banks", dropdownService.getBanks());
        model.addAttribute("relationships", dropdownService.getRelationships());
        model.addAttribute("bloodGroups", dropdownService.getBloodGroups());
        model.addAttribute("religions", dropdownService.getReligions());
        model.addAttribute("bodyTypes", dropdownService.getBodyTypes());
        return model;
    }

    @ResponseBody
    @CheckAccess(screen = "STUDENT_LIST", type = AccessType.VIEW)
    @GetMapping("/student/cities")
    public List<City> getCities(@RequestParam Long provinceId) {
        return dropdownService.getCities(provinceId);
    }

    public boolean isStudentExists(Student student){
        try{
            if(student.getId()!=null){
                return true;
            }
        }catch(Exception ex){
        }
        return false;
    }

    @CheckAccess(screen = "STUDENT_ADD", type = AccessType.CREATE)
    @PostMapping("/student")
    public String saveStudent(@Valid @ModelAttribute("student") Student student, BindingResult result, @RequestParam("customerPic") MultipartFile customerPic,
                             Model model, RedirectAttributes redirectAttribute,  HttpSession session) {
        //@RequestParam("customerPic1")MultipartFile customerPic1,
        String returnStr = "/student/add-student";
        /*boolean isStudentFound = isStudentExists(student);
        if(isStudentFound){
            returnStr = "/student/edit-student";
        }*/
        Student existingStudent = null;
        School school = (School)model.getAttribute("school");
        try{
            if(student.getId()!=null){
                existingStudent = studentService.getStudentDetail(student.getId(), school.getId()).orElse(null);
                returnStr = "student/edit-student";
            }

        }catch(Exception e){
            existingStudent = null;
        }
        if(result.hasErrors()){
            model = getAllGlobalModels(model);
            model.addAttribute("error", result.getFieldError().getDefaultMessage());
            return returnStr;
        }
        SimpleDateFormat sf = new SimpleDateFormat(FORMAT_PREFIX);
        String fileNameOrSchoolCode = sf.format(new Date());
        try{
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            student.setAcademicYear(academicYear);
            student.setSchool(school);
            /*if(existingStudent!=null){
                student.setUpdatedBy(userService.getLoggedInUser().getUsername());
            }
            else{
                student.setCreatedBy(userService.getLoggedInUser().getUsername());
            }*/
            Student savedStudent = studentService.saveStudent(student, customerPic, fileNameOrSchoolCode, existingStudent);
            String msg = "Student " + student.getStudentName() + " saved successfully";
            try{
                if(existingStudent!=null){
                    msg = "Student " + student.getStudentName() + " updated successfully";
                }
            }catch(Exception ex){
            }
            redirectAttribute.addFlashAttribute("success", msg);
            return "redirect:/student/student";
        }catch(FileFormatException ffe){
            ffe.printStackTrace();
            model = getAllGlobalModels(model);
            model.addAttribute("error", ffe.getMessage());
            return returnStr;
        } catch(FileSizeLimitExceededException ffle){
            ffle.printStackTrace();
            model.addAttribute("error", ffle.getMessage());
            model = getAllGlobalModels(model);
            return returnStr;
        } catch(UniqueConstraintsException ue){
            model.addAttribute("error", ue.getMessage());
            model = getAllGlobalModels(model);
            ue.printStackTrace();
            return returnStr;
        } catch(Exception ex){
            model = getAllGlobalModels(model);
            model.addAttribute("error", "Error in saving student!"+ex.getMessage());
            ex.printStackTrace();
            return returnStr;
        }
    }

    @CheckAccess(screen = "STUDENT_LIST", type = AccessType.VIEW)
    @GetMapping("/images/{filename}")
    public Resource getImage(@PathVariable("filename") String filename) {
        try {
            String imagePath = studentImageDirectory + "/" + filename;
            Resource resource = new FileSystemResource(imagePath);
            return resource;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not read file: " + filename, e);
        }
    }

    @CheckAccess(screen = "STUDENT_VIEW", type = AccessType.VIEW)
    @GetMapping("/student/show/{uuid}")
    public String showSchoolForm(@PathVariable("uuid")UUID uuid, Model model){
        School school = (School)model.getAttribute("school");
        Optional<Student> student = studentService.getStudentDetail(uuid, school.getId());
        model = getAllGlobalModels(model);
        model.addAttribute("student", student.get());
        model.addAttribute("fromDelete",false);
        return "student/show-student";
    }

    @CheckAccess(screen = "STUDENT_INACTIVE_LIST", type = AccessType.VIEW)
    @GetMapping("/student/showdeleted/{uuid}")
    public String showSchoolDeletedStudents(@PathVariable("uuid")UUID uuid, Model model){
        School school = (School)model.getAttribute("school");
        Optional<Student> student = studentService.getDeletedStudentDetail(uuid, school.getId());
        model = getAllGlobalModels(model);
        model.addAttribute("student", student.get());
        model.addAttribute("fromDelete",true);
        return "student/show-student";
    }

    @CheckAccess(screen = "STUDENT_EDIT", type = AccessType.EDIT)
    @GetMapping("/student/edit/{uuid}")
    public String editStudentForm(@PathVariable("uuid") UUID uuid, Model model, RedirectAttributes redirectAttributes){
        School school = (School)model.getAttribute("school");
        Student student = studentService.getStudentDetail(uuid, school.getId()).orElse(null);;
        if(student==null){
            redirectAttributes.addFlashAttribute("error", "Student not found");
            List<Student> studentList = studentService.getAllActiveStudentsOfSchool(school.getId());
            model.addAttribute("students", studentList);
            model.addAttribute("hasStudent", !studentList.isEmpty());
            model.addAttribute("page", "datatable");
            return "redirect:/student/student";
        }
        model.addAttribute("student", student);
        model = getAllGlobalModels(model);
        return "student/edit-student";
    }

    @CheckAccess(screen = "STUDENT_EDIT", type = AccessType.EDIT)
    @PostMapping("/edit-details")
    public String editStudentDetails(@Valid @ModelAttribute("student") Student student, BindingResult result, @RequestParam("customerPic") MultipartFile customerPic,
                                     Model model, RedirectAttributes redirectAttribute){
        //boolean isStudentFound = isStudentExists(student);
        SimpleDateFormat sf = new SimpleDateFormat(FORMAT_PREFIX);
        String fileNameOrSchoolCode = sf.format(new Date());
        try{
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            //School school = schoolService.getSchoolById(3L).get();
            student.setAcademicYear(academicYear);
            student.setSchool(school);
            Student existingStudent = studentService.editStudentDetails(student, customerPic, fileNameOrSchoolCode);
            String msg = "Student " + student.getStudentName() + " updated successfully";
            redirectAttribute.addFlashAttribute("success", msg);
            return "redirect:/student/student";
        }catch(FileFormatException ffe){
            model = getAllGlobalModels(model);
            model.addAttribute("error", ffe.getMessage());
            ffe.printStackTrace();
            return "student/edit-student";
        } catch(FileSizeLimitExceededException ffle){
            model.addAttribute("error", ffle.getMessage());
            model = getAllGlobalModels(model);
            ffle.printStackTrace();
            return "student/edit-student";
        } catch(UniqueConstraintsException ue){
            model.addAttribute("error", ue.getMessage());
            model = getAllGlobalModels(model);
            ue.printStackTrace();
            return "student/edit-student";
        } catch(Exception ex){
            model.addAttribute("error", ex.getMessage());
            model = getAllGlobalModels(model);
            model.addAttribute("error", "Error in updating student!"+ex.getMessage());
            ex.printStackTrace();
            return "student/edit-student";
        }
    }

    @CheckAccess(screen = "STUDENT_ASSIGN_SR", type = AccessType.CREATE)
    @GetMapping("/assign-sr")
    public String assignSRForm(Model model){
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        return "student/assign-srno";
    }

    @CheckAccess(screen = "STUDENT_INACTIVE_LIST", type = AccessType.VIEW)
    @GetMapping("/stu-deleted-list")
    public String deletedStudentList(Model model){
        School school = (School)model.getAttribute("school");
        List<AcademicStudent> studentList = studentService.getAllInActiveStudentsList(school.getId());
        model.addAttribute("students", studentList);
        model.addAttribute("hasStudent", !studentList.isEmpty());
        model.addAttribute("page", "datatable");

        return "student/inactive-students";
    }
    @CheckAccess(screen = "STUDENT_DELETE", type = AccessType.DELETE)
    @GetMapping("/delete-student/{deleteId}")
    public String deleteStudent(@PathVariable("deleteId")String id, Model model, RedirectAttributes redirectAttributes){
        String msg = studentService.deleteStudent(Long.valueOf(id));
        if(msg.contains("success")){
            redirectAttributes.addFlashAttribute("success",msg.split("#####")[1]);
        } else if(msg.contains("Error")){
            School school = (School)model.getAttribute("school");
            List<Student> studentList = studentService.getAllActiveStudentsOfSchool(school.getId());
            model.addAttribute("students", studentList);
            model.addAttribute("hasStudent", !studentList.isEmpty());
            model.addAttribute("page", "datatable");
            redirectAttributes.addFlashAttribute("error",msg.split("#####")[1]);
        }
        return "redirect:/student/student";
    }

    @CheckAccess(screen = "STUDENT_ACTIVATE", type = AccessType.CREATE)
    @GetMapping("/activate-student/{studentIdForUpdate}")
    public String activateStudent(@PathVariable("studentIdForUpdate")String id, Model model, RedirectAttributes redirectAttributes){
        String msg = studentService.activateStudent(Long.valueOf(id));
        if(msg.contains("success")){
            redirectAttributes.addFlashAttribute("success",msg.split("#####")[1]);
        } else if(msg.contains("Error")){
            School school = (School)model.getAttribute("school");
            List<Student> studentList = studentService.getAllActiveStudentsOfSchool(school.getId());
            model.addAttribute("students", studentList);
            model.addAttribute("hasStudent", !studentList.isEmpty());
            model.addAttribute("page", "datatable");
            redirectAttributes.addFlashAttribute("error",msg.split("#####")[1]);
        }
        return "redirect:/student/student";
    }

    @CheckAccess(screen = "STUDENT_EDIT_GRADE", type = AccessType.EDIT)
    @GetMapping("/edit-grade-section")
    public String modifyGradeAndSectionForm(Model model){
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        return "student/edit-grade-section";
    }

    @CheckAccess(screen = "STUDENT_ATTENDANCE_VIEW", type = AccessType.VIEW)
    @GetMapping("/student-attendance")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
    public String studentAttendanceForm(Model model){
        SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy");
        model.addAttribute("todayDate", sf.format(new Date()));
        try{
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            List studentsList = studentService.getAttendanceDetailsByClass(school.getId(), academicYear.getId());
            model.addAttribute("attendanceSummary", studentsList);
            model.addAttribute("hasAttendance", !studentsList.isEmpty());
        }catch(Exception e){
            e.printStackTrace();
        }
        model.addAttribute("page", "datatable");
        return "student/student-attendance";
    }
    @CheckAccess(screen = "STUDENT_ATTENDANCE_MARK", type = AccessType.CREATE)
    @GetMapping("/student-submit-attendance")
    public String studentAttendanceSave(Model model){
        SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy");
        model.addAttribute("todayDate", sf.format(new Date()));
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        try{

        }catch(Exception e){
            e.printStackTrace();
        }
        return "student/attendance";
    }

    @CheckAccess(screen = "STUDENT_ATTENDANCE_REPORT", type = AccessType.VIEW)
    @GetMapping("/student-show-attendance")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
    public String studentShowAttendance(Model model){
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        model.addAttribute("months", dropdownService.getMonths());
        return "student/show-attendance";
    }

    @CheckAccess(screen = "STUDENT_EDIT_AADHAR", type = AccessType.EDIT)
    @GetMapping("/edit-aadhar-detail")
    public String updateAadharPage(Model model){
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        return "student/update-aadhar";
    }

    @CheckAccess(screen = "STUDENT_REPORT_SESSION", type = AccessType.VIEW)
    @GetMapping("/sessions-total-students-detail")
    public String totalFeeSubmissionDetail(Model model){
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("page", "datatable");
        return "student/all_students_session";
    }

    @CheckAccess(screen = "STUDENT_REPORT_GRADE", type = AccessType.VIEW)
    @GetMapping("/grade-total-students-detail")
    public String totalFeeSubmissionDetailByGrade(Model model){
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        model.addAttribute("page", "datatable");
        return "student/all_students_grade";
    }

    @CheckAccess(screen = "STUDENT_EXAM_RESULT", type = AccessType.VIEW)
    @GetMapping("/stu-exam-result")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
    public String examResultForm(Model model){
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        // Load only ExamDetails configured for this school + academic year
        // so dropdown values match what getExamDetailById expects
        School school = (School) model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        if (school != null && academicYear != null) {
            model.addAttribute("examNames", examDetailsRepository.findAllByAcademicYear_IdAndSchool_Id(academicYear.getId(), school.getId()));
        } else {
            model.addAttribute("examNames", java.util.Collections.emptyList());
        }
        model.addAttribute("page", "datatable");
        return "student/stu_exam";
    }

    @CheckAccess(screen = "STUDENT_SEARCH", type = AccessType.VIEW)
    @GetMapping("/look-up-student")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
    public String searchStudent(Model model){
        School school = (School)model.getAttribute("school");
        List<AcademicYear> academicYears = academicyearService.getAllAcademiyears(school.getId());
        model.addAttribute("academicYears", academicYears);
        return "student/search-student";

    }


}

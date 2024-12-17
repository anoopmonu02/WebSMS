package com.smsweb.sms.controllers.student;

import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.controllers.employee.EmployeeController;
import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.SiblingGroup;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.Section;
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
import org.springframework.http.ResponseEntity;
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

    @Autowired
    public StudentController(StudentService studentService, AcademicStudentService academicStudentService, DropdownService dropdownService, AcademicyearService academicyearService, SchoolService schoolService, UserService userService, UserService userService1){
        this.studentService = studentService;
        this.academicStudentService = academicStudentService;
        this.dropdownService = dropdownService;
        this.academicyearService = academicyearService;
        this.schoolService = schoolService;
        this.userService = userService1;
    }

    @GetMapping("/student")
    public String studentData(Model model){
        log.debug("inside student list");
        School school = (School)model.getAttribute("school");
        List<Student> studentList = studentService.getAllActiveStudents(school.getId());
        model.addAttribute("students", studentList);
        model.addAttribute("hasStudent", !studentList.isEmpty());
        model.addAttribute("page", "datatable");
        return "/student/student";
    }

    @GetMapping("/student/add")
    public String addStudentData(Model model){
        Student student = new Student();
        student.setUserEntity(new UserEntity());
        model.addAttribute("student", student);
        model = getAllGlobalModels(model);
        return "/student/add-student";
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
                returnStr = "/student/edit-student";
            }

        }catch(Exception e){
            existingStudent = null;
        }
        if(result.hasErrors()){
            model = getAllGlobalModels(model);
            model.addAttribute("error", result.getFieldError());
            return returnStr;
        }
        SimpleDateFormat sf = new SimpleDateFormat(FORMAT_PREFIX);
        String fileNameOrSchoolCode = sf.format(new Date());
        try{
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            student.setAcademicYear(academicYear);
            student.setSchool(school);
            if(existingStudent!=null){
                student.setUpdatedBy(userService.getLoggedInUser().getUsername());
            }
            else{
                student.setCreatedBy(userService.getLoggedInUser().getUsername());
            }
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

    @GetMapping("/student/show/{uuid}")
    public String showSchoolForm(@PathVariable("uuid")UUID uuid, Model model){
        School school = (School)model.getAttribute("school");
        Optional<Student> student = studentService.getStudentDetail(uuid, school.getId());
        model = getAllGlobalModels(model);
        model.addAttribute("student", student.get());
        model.addAttribute("fromDelete",false);
        return "student/show-student";
    }

    @GetMapping("/student/showdeleted/{uuid}")
    public String showSchoolDeletedStudents(@PathVariable("uuid")UUID uuid, Model model){
        School school = (School)model.getAttribute("school");
        Optional<Student> student = studentService.getDeletedStudentDetail(uuid, school.getId());
        model = getAllGlobalModels(model);
        model.addAttribute("student", student.get());
        model.addAttribute("fromDelete",true);
        return "student/show-student";
    }

    @GetMapping("/student/edit/{uuid}")
    public String editStudentForm(@PathVariable("uuid") UUID uuid, Model model, RedirectAttributes redirectAttributes){
        School school = (School)model.getAttribute("school");
        Student student = studentService.getStudentDetail(uuid, school.getId()).orElse(null);;
        if(student==null){
            redirectAttributes.addFlashAttribute("error", "Student not found");
            List<Student> studentList = studentService.getAllActiveStudents(school.getId());
            model.addAttribute("students", studentList);
            model.addAttribute("hasStudent", !studentList.isEmpty());
            model.addAttribute("page", "datatable");
            return "redirect:/student/student";
        }
        model.addAttribute("student", student);
        model = getAllGlobalModels(model);
        return "student/edit-student";
    }

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
            return "/student/edit-student";
        } catch(FileSizeLimitExceededException ffle){
            model.addAttribute("error", ffle.getMessage());
            model = getAllGlobalModels(model);
            ffle.printStackTrace();
            return "/student/edit-student";
        } catch(UniqueConstraintsException ue){
            model.addAttribute("error", ue.getMessage());
            model = getAllGlobalModels(model);
            ue.printStackTrace();
            return "/student/edit-student";
        } catch(Exception ex){
            model.addAttribute("error", ex.getMessage());
            model = getAllGlobalModels(model);
            model.addAttribute("error", "Error in updating student!"+ex.getMessage());
            ex.printStackTrace();
            return "/student/edit-student";
        }
    }

    @GetMapping("/assign-sr")
    public String assignSRForm(Model model){
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        return "/student/assign-srno";
    }

    @GetMapping("/stu-deleted-list")
    public String deletedStudentList(Model model){
        School school = (School)model.getAttribute("school");
        List<Student> studentList = studentService.getAllInActiveStudents(school.getId());
        model.addAttribute("students", studentList);
        model.addAttribute("hasStudent", !studentList.isEmpty());
        model.addAttribute("page", "datatable");

        return "/student/inactive-students";
    }
    @GetMapping("/delete-student/{deleteId}")
    public String deleteStudent(@PathVariable("deleteId")String id, Model model, RedirectAttributes redirectAttributes){
        String msg = studentService.deleteStudent(Long.valueOf(id));
        if(msg.contains("success")){
            redirectAttributes.addFlashAttribute("success",msg.split("#####")[1]);
        } else if(msg.contains("Error")){
            School school = (School)model.getAttribute("school");
            List<Student> studentList = studentService.getAllActiveStudents(school.getId());
            model.addAttribute("students", studentList);
            model.addAttribute("hasStudent", !studentList.isEmpty());
            model.addAttribute("page", "datatable");
            redirectAttributes.addFlashAttribute("error",msg.split("#####")[1]);
        }
        return "redirect:/student/student";
    }

    @GetMapping("/edit-grade-section")
    public String modifyGradeAndSectionForm(Model model){
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        return "/student/edit-grade-section";
    }

}

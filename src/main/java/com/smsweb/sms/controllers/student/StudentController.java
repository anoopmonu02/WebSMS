package com.smsweb.sms.controllers.student;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.SchoolService;
import com.smsweb.sms.services.globalaccess.DropdownService;
import com.smsweb.sms.services.student.StudentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
    private final String FORMAT_PREFIX = "ddMMyyyyhhmmss";
    private final StudentService studentService;
    private final DropdownService dropdownService;
    private final AcademicyearService academicyearService;
    private final SchoolService schoolService;

    @Autowired
    public StudentController(StudentService studentService, DropdownService dropdownService, AcademicyearService academicyearService, SchoolService schoolService){
        this.studentService = studentService;
        this.dropdownService = dropdownService;
        this.academicyearService = academicyearService;
        this.schoolService = schoolService;
    }

    @GetMapping("/student")
    public String studentData(Model model){
        List<Student> studentList = studentService.getAllActiveStudents(4L);
        model.addAttribute("students", studentList);
        model.addAttribute("hasStudent", !studentList.isEmpty());
        return "/student/student";
    }

    @GetMapping("/student/add")
    public String addStudentData(Model model){
        model.addAttribute("student", new Student());
        /*model.addAttribute("categories", dropdownService.getCategories());
        model.addAttribute("casts", dropdownService.getCasts());
        model.addAttribute("provinces", dropdownService.getProvinces());
        //model.addAttribute("cities", dropdownService.getCities(-1L));
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("banks", dropdownService.getBanks());
        model.addAttribute("relationships", dropdownService.getRelationships());
        model.addAttribute("bloodGroups", dropdownService.getBloodGroups());
        model.addAttribute("religions", dropdownService.getReligions());
        model.addAttribute("bodyTypes", dropdownService.getBodyTypes());*/
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

    @PostMapping("/student")
    public String saveStudent(@Valid @ModelAttribute("student") Student student, BindingResult result, @RequestParam("customerPic") MultipartFile customerPic,
                             Model model, RedirectAttributes redirectAttribute){
        //@RequestParam("customerPic1")MultipartFile customerPic1,
        if(result.hasErrors()){
            model = getAllGlobalModels(model);
            model.addAttribute("error", result.getFieldError());
            return "/student/add-student";
        }
        SimpleDateFormat sf = new SimpleDateFormat(FORMAT_PREFIX);
        String fileNameOrSchoolCode = sf.format(new Date());
        try{
            AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
            School school = schoolService.getSchoolById(4L).get();
            student.setAcademicYear(academicYear);
            student.setSchool(school);
            studentService.saveStudent(student, customerPic, fileNameOrSchoolCode);
            String msg = "Student " + student.getStudentName() + " saved successfully";
            redirectAttribute.addFlashAttribute("success", msg);
            return "redirect:/student/student";
        }catch(FileFormatException ffe){
            model = getAllGlobalModels(model);
            model.addAttribute("error", ffe.getMessage());
            ffe.printStackTrace();
            return "/student/add-student";
        } catch(FileSizeLimitExceededException ffle){
            model.addAttribute("error", ffle.getMessage());
            model = getAllGlobalModels(model);
            ffle.printStackTrace();
            return "/student/add-student";
        } catch(UniqueConstraintsException ue){
            model.addAttribute("error", ue.getMessage());
            model = getAllGlobalModels(model);
            ue.printStackTrace();
            return "/student/add-student";
        } catch(Exception ex){
            model = getAllGlobalModels(model);
            model.addAttribute("error", "Error in saving student!"+ex.getMessage());
            ex.printStackTrace();
            return "/student/add-student";
        }
    }


}

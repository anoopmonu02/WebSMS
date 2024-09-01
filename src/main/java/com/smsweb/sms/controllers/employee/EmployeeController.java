package com.smsweb.sms.controllers.employee;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.services.Employee.EmployeeService;
import com.smsweb.sms.services.admin.SchoolService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    private final String FORMAT_PREFIX = "ddMMyyyyhhmmss";
    @Value("${employee.image.storage.path}")
    private String employeeImageDirectory;
    private final EmployeeService employeeService;
    private final SchoolService schoolService;


    public EmployeeController(EmployeeService employeeService, SchoolService schoolService) {
        this.employeeService = employeeService;
        this.schoolService = schoolService;
    }

    @GetMapping("/employee-list")
    public String getEmployeeList(Model model){
        List<Employee> employees = employeeService.getAllActiveEmployees(4L);
        model.addAttribute("employees", employees);
        model.addAttribute("hasEmployee", !employees.isEmpty());
        model.addAttribute("page", "datatable");
        return "/employee/employee";
    }

    @GetMapping("/employee-add")
    public String getAddEmployeeForm(Model model){
        model.addAttribute("employee", new Employee());
        return "/employee/add-employee";
    }

    @PostMapping("/employee-save")
    public String saveEmployee(@Valid @ModelAttribute("employee")Employee employee, BindingResult result, Model model, RedirectAttributes redirectAttributes,
                               @RequestParam("customerPic") MultipartFile customerPic){
        String returnStr = "/employee/add-employee";
        if(result.hasErrors()){
            model.addAttribute("error", result.getFieldError());
            return returnStr;
        }
        SimpleDateFormat sf = new SimpleDateFormat(FORMAT_PREFIX);
        String fileNameOrSchoolCode = sf.format(new Date());
        try{
            School school = schoolService.getSchoolById(4L).get();
            employee.setSchool(school);
            Employee emp = employeeService.saveEmployee(employee, customerPic, fileNameOrSchoolCode);
            String msg = "Employee: " + employee.getEmployeeName() + " saved successfully";
            redirectAttributes.addFlashAttribute("success", msg);
            return "redirect:/employee/employee-list";
        } catch(FileFormatException ffe){
            model.addAttribute("error", ffe.getMessage());
            ffe.printStackTrace();
            return returnStr;
        } catch(FileSizeLimitExceededException ffle){
            model.addAttribute("error", ffle.getMessage());
            ffle.printStackTrace();
            return returnStr;
        } catch(UniqueConstraintsException ue){
            model.addAttribute("error", ue.getMessage());
            ue.printStackTrace();
            return returnStr;
        } catch(Exception ex){
            model.addAttribute("error", "Error in saving employee!"+ex.getMessage());
            ex.printStackTrace();
            return returnStr;
        }
    }

    @GetMapping("/employee-edit/{uuid}")
    public String editEmployeeForm(@PathVariable("uuid") UUID uuid, Model model, RedirectAttributes redirectAttributes){
        Employee employee = employeeService.getEmployeeByUUID(uuid).orElse(null);
        if(employee==null){
            redirectAttributes.addFlashAttribute("error", "Employee not found");
            List<Employee> employees = employeeService.getAllActiveEmployees(4L);
            model.addAttribute("employees", employees);
            model.addAttribute("hasEmployee", !employees.isEmpty());
            model.addAttribute("page", "datatable");
            return "redirect:/employee/employee";
        }
        model.addAttribute("employee", employee);
        return "/employee/edit-employee";
    }


    @GetMapping("/student-images/{filename:.+}")
    public Resource getImage(@PathVariable String filename) {
        try {
            Path file = Paths.get(employeeImageDirectory).resolve(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + filename);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not read file: " + filename, e);
        }
    }

}

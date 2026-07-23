package com.smsweb.sms.controllers.employee;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.services.Employee.EmployeeService;
import com.smsweb.sms.services.admin.SchoolService;
import com.smsweb.sms.services.users.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_STAFF')")
@Controller
@RequestMapping("/employee")
public class EmployeeController extends BaseController {
    Logger log = LoggerFactory.getLogger(EmployeeController.class);
    private final String FORMAT_PREFIX = "ddMMyyyyhhmmss";
    private final UserService userService;
    @Value("${employee.image.storage.path}")
    private String employeeImageDirectory;
    private final EmployeeService employeeService;
    private final SchoolService schoolService;


    public EmployeeController(EmployeeService employeeService, SchoolService schoolService, UserService userService) {
        this.employeeService = employeeService;
        this.schoolService = schoolService;
        this.userService = userService;
    }

    @CheckAccess(screen = "EMPLOYEE", type = AccessType.VIEW)
    @GetMapping("/employee-list")
    public String getEmployeeList(Model model){
        log.info("Inside getEmployeeList");
        List<Employee> employees = null;
        School school = (School)model.getAttribute("school");
        if(isSuperAdminLoggedIn()){
            employees = employeeService.getAllEmployees();
        } else{
            employees = employeeService.getAllActiveEmployees(school.getId());
        }
        model.addAttribute("employees", employees);
        model.addAttribute("hasEmployee", !employees.isEmpty());
        model.addAttribute("page", "datatable");
        log.debug("Total employees - "+employees.size());
        return "employee/employee";
    }

    @CheckAccess(screen = "EMPLOYEE", type = AccessType.CREATE)
    @GetMapping("/employee-add")
    public String getAddEmployeeForm(Model model) {
        log.info("Inside getAddEmployeeForm");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Employee employee = new Employee();
        employee.setUserEntity(new UserEntity());
        model.addAttribute("employee", employee);
        if (isSuperAdmin) {
            model.addAttribute("superUserLogin", true);
            model.addAttribute("schools", schoolService.getAllSchools());
        }
        if (isAdmin) {
            School school = (School) model.getAttribute("school");
            model.addAttribute("empschool", school);
            employee.setSchool(school);
            model.addAttribute("adminLogin", true);
        }

        // Create a new Employee object and initialize its UserEntity
        //Employee employee = new Employee();
        //employee.setUserEntity(new UserEntity()); // Assuming UserEntity is now part of Employee

        // Add the Employee object to the model
        //model.addAttribute("employee", employee);
        /*try{
            if(isSuperAdminLoggedIn()){
                model.addAttribute("superUserLogin", true);
                model.addAttribute("schools", schoolService.getAllSchools());
            }
            if(isAdminLogin()){
                School school = (School)model.getAttribute("school");
                model.addAttribute("empschool", school);
                employee.setSchool(school);
                model.addAttribute("adminLogin", true);
            }
            //TODO - if admin logged in then get the value of school
        }catch(Exception e){
            e.printStackTrace();
        }*/

        return "employee/add-employee";
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
    private boolean isAdminLogin(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                // Check if the user has the "ROLE_ADMIN"
                return authentication.getAuthorities().stream()
                        .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));
            }
            return false;
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }

    @CheckAccess(screen = "EMPLOYEE", type = AccessType.CREATE)
    @PostMapping("/employee-save")
    public String saveEmployee(@Valid @ModelAttribute("employee")Employee employee, BindingResult result, Model model, RedirectAttributes redirectAttributes,
                               @RequestParam("customerPic") MultipartFile customerPic){
        log.info("Inside saveEmployee");
        String returnStr = "/employee/add-employee";
        Employee existingEmployee = null;

        try {
            if(employee.getUuid()!=null){
                existingEmployee = employeeService.getEmployeeByUUID(employee.getUuid()).orElse(null);
                returnStr = "employee/edit-employee";
            }

        } catch (Exception e) {
            e.printStackTrace();
            existingEmployee = null;
        }
        if(result.hasErrors()){
            model.addAttribute("error", result.getFieldError());
            return returnStr;
        }
        SimpleDateFormat sf = new SimpleDateFormat(FORMAT_PREFIX);
        String fileNameOrSchoolCode = sf.format(new Date());
        try{
            School school = schoolService.getSchoolById(employee.getSchool().getId()).get();
            employee.setSchool(school);
            /*if(existingEmployee!=null){
                employee.setUpdatedBy(userService.getLoggedInUser().getUsername());
            }
            else{
                employee.setCreatedBy(userService.getLoggedInUser().getUsername());
            }*/
            Employee emp = employeeService.saveEmployee(employee, customerPic, fileNameOrSchoolCode, existingEmployee);
            String msg = "Employee: " + employee.getEmployeeName() + " saved successfully";
            if (existingEmployee != null) {
                msg = "Employee: " + employee.getEmployeeName() + " updated successfully";
            }
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
            redirectAttributes.addFlashAttribute("error", "Error in saving employee!"+ex.getMessage());
            ex.printStackTrace();
            return returnStr;
        }
    }

    @CheckAccess(screen = "EMPLOYEE", type = AccessType.EDIT)
    @GetMapping("/employee-edit/{uuid}")
    public String editEmployeeForm(@PathVariable("uuid") UUID uuid, Model model, RedirectAttributes redirectAttributes) {
        log.info("Inside editEmployeeForm");
        try {
            Optional<Employee> employeeOptional = employeeService.getEmployeeByUUID(uuid);
            if (employeeOptional.isEmpty()) {
                School school = (School)model.getAttribute("school");
                redirectAttributes.addFlashAttribute("error", "Employee not found");
                List<Employee> employees = employeeService.getAllActiveEmployees(school.getId());
                model.addAttribute("employees", employees);
                model.addAttribute("hasEmployee", !employees.isEmpty());
                model.addAttribute("page", "datatable");
                return "redirect:/employee/employee";
            }
            Employee employee = employeeOptional.get();
            model.addAttribute("employee", employee);
            return "employee/edit-employee";
        } catch (Exception e) {
            log.error("Error fetching employee", e);
            redirectAttributes.addFlashAttribute("error", "An error occurred while fetching the employee.");
            return "redirect:/employee/employee";
        }
    }


    // ── Employee List "Reset Password" button ────────────────────────────────
    // ROLE_ADMIN/ROLE_SUPERADMIN only — deliberately narrower than the class-level
    // @PreAuthorize above (which also allows ROLE_STAFF to view this list), and
    // deliberately NOT using the finer-grained @CheckAccess/AppScreen system this
    // controller otherwise uses, matching the precedent set by the Mobile Users
    // admin screen's own reset-password button (same shape of feature).

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
    @GetMapping("/generate-password")
    @ResponseBody
    public ResponseEntity<?> generatePassword() {
        log.info("Inside employee generate temp password");
        return ResponseEntity.ok(Map.of("password", employeeService.generateTempPassword()));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
    @PostMapping("/{uuid}/reset-password")
    @ResponseBody
    public ResponseEntity<?> resetPassword(@PathVariable("uuid") UUID uuid, @RequestBody Map<String, String> payload) {
        log.info("Inside employee reset password - uuid={}", uuid);
        try {
            String newPassword = payload != null ? payload.get("newPassword") : null;
            if (newPassword == null || newPassword.trim().length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters."));
            }
            Employee employee = employeeService.getEmployeeByUUID(uuid).orElse(null);
            if (employee == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Employee not found."));
            }
            // Self-reset is blocked server-side too (the button is already hidden for
            // this row in the UI, but that alone doesn't stop a direct API call) —
            // an admin/superadmin resetting their own password must use
            // /auth/change-password instead, which requires their current password.
            String loggedInUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            if (employee.getUserEntity() != null && loggedInUsername != null
                    && loggedInUsername.equals(employee.getUserEntity().getUsername())) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "You can't reset your own password here — use Change Password from your profile menu instead."));
            }
            employeeService.adminResetPassword(employee, newPassword.trim());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Failed to reset password for employee uuid={}", uuid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Reset failed: " + e.getMessage()));
        }
    }

    @CheckAccess(screen = "EMPLOYEE", type = AccessType.VIEW)
    @GetMapping("/images/{filename}")
    public Resource getImage(@PathVariable("filename") String filename) {
        log.info("Inside getImage");
        try {
            String imagePath = employeeImageDirectory + "/" + filename;
            Resource resource = new FileSystemResource(imagePath);
            return resource;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not read file: " + filename, e);
        }
    }

    @CheckAccess(screen = "EMPLOYEE", type = AccessType.DELETE)
    @PostMapping("/employee-delete")
    public String deleteEmployee(@Valid @ModelAttribute("employee")Employee employee, BindingResult result, Model model, RedirectAttributes redirectAttributes,
                                 @RequestParam("customerPic") MultipartFile customerPic){
        log.info("Inside deleteEmployee");
        return "redirect:/employee/employee";
    }

}

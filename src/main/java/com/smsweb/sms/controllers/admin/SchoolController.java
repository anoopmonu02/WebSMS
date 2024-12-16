package com.smsweb.sms.controllers.admin;


import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.services.admin.SchoolService;
import com.smsweb.sms.services.users.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class SchoolController {

    private final SchoolService schoolService;
    private final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
    private final String FORMAT_PREFIX = "ddMMyyyyhhmmss";
    private final String SCHOOL_IMG_FOLDER_PATH = new ClassPathResource("static/school/").getFile().getAbsolutePath();
    private final UserService userService;

    @Autowired
    public SchoolController(SchoolService schoolService, UserService userService) throws IOException {
        this.schoolService = schoolService;
        this.userService = userService;
    }

    @GetMapping("/school")
    public String getSchools(Model model){
        List<School> schools = schoolService.getAllSchools();
        model.addAttribute("schools",schools);
        model.addAttribute("hasSchoolData", !schools.isEmpty());
        return "/admin/school";
    }

    @GetMapping("/school/add")
    public String addSchoolForm(Model model){
        model.addAttribute("school", new School());
        model.addAttribute("provinces", schoolService.getAllProvinces());
        model.addAttribute("customer", schoolService.getAllCustomers());
        return "/admin/add-school";
    }

    @ResponseBody
    @GetMapping("/school/cities")
    public List<City> getCities(@RequestParam Long provinceId) {
        return schoolService.getAllCitiesByProvince(provinceId);
    }

    @PostMapping("/school")
    public String saveSchool(@Valid @ModelAttribute("school")School school, BindingResult result, @RequestParam("customerPic")MultipartFile customerPic,
                             Model model, RedirectAttributes redirectAttribute){
        //@RequestParam("customerPic1")MultipartFile customerPic1,
        if(result.hasErrors()){
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            return "/admin/add-school";
        }
        SimpleDateFormat sf = new SimpleDateFormat(FORMAT_PREFIX);
        String fileNameOrSchoolCode = sf.format(new Date());
        try{
            school.setCreatedBy(userService.getLoggedInUser().getUsername());
            schoolService.saveSchool(school, customerPic, fileNameOrSchoolCode);
            String msg = "School " + school.getSchoolName() + " saved successfully";
            redirectAttribute.addFlashAttribute("success", msg);
            return "redirect:/admin/school";
        }catch(FileFormatException ffe){
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            model.addAttribute("error", ffe.getMessage());
            ffe.printStackTrace();
            return "/admin/add-school";
        } catch(FileSizeLimitExceededException ffle){
            model.addAttribute("error", ffle.getMessage());
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            ffle.printStackTrace();
            return "/admin/add-school";
        } catch(UniqueConstraintsException ue){
            model.addAttribute("error", ue.getMessage());
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            ue.printStackTrace();
            return "/admin/add-school";
        } catch(Exception ex){
            redirectAttribute.addFlashAttribute("error", "Error in saving school!");
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            model.addAttribute("error", ex.getMessage());
            ex.printStackTrace();
            return "/admin/add-school";
        }
    }

    @GetMapping("/school/edit/{id}")
    public String getEditPage(@PathVariable("id") Long id, Model model){
        School school = schoolService.getSchoolById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid School Id:" + id));
        model.addAttribute("school", school);
        model.addAttribute("provinces", schoolService.getAllProvinces());
        model.addAttribute("customer", schoolService.getAllCustomers());
        return "/admin/edit-school";
    }

    @PostMapping("/school/{id}")
    public String updateSchool(@PathVariable("id") Long id, @Valid @ModelAttribute("school")School school, BindingResult result, @RequestParam("customerPic")MultipartFile customerPic,
                               Model model, RedirectAttributes redirectAttribute){
        System.out.println("inside method");
        if(result.hasErrors()){
            //school.setId(id);
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            return "/admin/edit-school";
        }
        SimpleDateFormat sf = new SimpleDateFormat(FORMAT_PREFIX);
        String fileNameOrSchoolCode = sf.format(new Date());
        try{
            school.setUpdatedBy(userService.getLoggedInUser().getUsername());
            schoolService.saveSchool(school, customerPic, fileNameOrSchoolCode);
            String msg = "School " + school.getSchoolName() + " updated successfully";
            redirectAttribute.addFlashAttribute("success", msg);
            return "redirect:/admin/school";
        }catch(FileFormatException ffe){
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            model.addAttribute("error", ffe.getMessage());
            ffe.printStackTrace();
            return "/admin/edit-school";
        } catch(FileSizeLimitExceededException ffle){
            model.addAttribute("error", ffle.getMessage());
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            ffle.printStackTrace();
            return "/admin/edit-school";
        } catch(UniqueConstraintsException ue){
            model.addAttribute("error", ue.getMessage());
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            ue.printStackTrace();
            return "/admin/edit-school";
        } catch(Exception ex){
            redirectAttribute.addFlashAttribute("error", "Error in updating school!");
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            model.addAttribute("error", ex.getMessage());
            ex.printStackTrace();
            return "/admin/edit-school";
        }
    }
    @GetMapping("/school/show/{id}")
    public String showSchoolForm(@PathVariable("id")Long id, Model model){
        Optional<School> school = schoolService.getSchoolById(id);
        model.addAttribute("provinces", schoolService.getAllProvinces());
        model.addAttribute("customer", schoolService.getAllCustomers());
        model.addAttribute("school", school.get());
        return "admin/show-school";
    }

}

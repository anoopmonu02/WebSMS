package com.smsweb.sms.controllers.admin;


import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.services.admin.SchoolService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class SchoolController {

    private final SchoolService schoolService;
    private final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
    private final String FORMAT_PREFIX = "ddMMyyyyhhmmss";
    private final String SCHOOL_IMG_FOLDER_PATH = new ClassPathResource("static/images/school/").getFile().getAbsolutePath();
    @Autowired
    public SchoolController(SchoolService schoolService) throws IOException {
        this.schoolService = schoolService;
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
            /*if(!customerPic.isEmpty()){
                boolean isGoingForward = checkValidFileType(customerPic, model);
                System.out.println("isGoingForward---- "+isGoingForward);
                if(!isGoingForward){
                    System.out.println("MODEL: "+model.getAttribute("picUploadError"));
                    //updated model found then will add redirect attribute
                    return "/admin/add-customer";
                }
                if(isGoingForward){
                    boolean isLogoSaved = saveLogoFile(fileNameOrSchoolCode, customerPic.getOriginalFilename(), customerPic, "school");
                    if(isLogoSaved){
                        school.setLogo1(customerPic.getOriginalFilename());
                    } else{
                        System.out.println("isLogoSaved -------- "+isLogoSaved);
                        model.addAttribute("provinces", schoolService.getAllProvinces());
                        model.addAttribute("customer", schoolService.getAllCustomers());
                        redirectAttribute.addFlashAttribute("logo-save", "Unable to save logo");
                        return "/admin/add-customer";
                    }
                }
            } else{
                school.setLogo1(null);
            }
            school.setSchoolCode(fileNameOrSchoolCode);
            System.out.println("school: "+school);
            schoolService.saveSchool(school);

            redirectAttribute.addFlashAttribute("save-school", school);

            return "redirect:/admin/school";*/
            schoolService.saveSchool(school, customerPic, fileNameOrSchoolCode);
            String msg = "School" + school.getSchoolName() + " saved successfully";
            redirectAttribute.addFlashAttribute("success-message", msg);

        }catch(Exception ex){
            redirectAttribute.addFlashAttribute("error-message", "Error in saving school!");
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            ex.printStackTrace();
            return "/admin/add-customer";
        }
        return "redirect:/admin/school";
    }

    private boolean checkValidFileType(MultipartFile logo, Model model){
        boolean flag = true;
        try{
            if(!logo.getContentType().startsWith("image/")){
                model.addAttribute("provinces", schoolService.getAllProvinces());
                model.addAttribute("customer", schoolService.getAllCustomers());
                model.addAttribute("picUploadError", "Only image files are allowed.");
                return false;
            }
            // Check file size
            if (logo.getSize() > MAX_FILE_SIZE) {
                model.addAttribute("provinces", schoolService.getAllProvinces());
                model.addAttribute("customer", schoolService.getAllCustomers());
                model.addAttribute("picUploadError", "File size must be less than 2 MB.");
                return false;
            }
        }catch(Exception e){
            e.printStackTrace();
            model.addAttribute("provinces", schoolService.getAllProvinces());
            model.addAttribute("customer", schoolService.getAllCustomers());
            model.addAttribute("picUploadError", "Could not upload logo: "+e.getLocalizedMessage());
            flag = false;
        }
        return flag;
    }

    private boolean saveLogoFile(String fileName, String originalFileName, MultipartFile logo, String directoryName){
        boolean flag = true;
        try{
            Path categoryPath = Paths.get("./images/", directoryName);
            String imageFileName = fileName + "_" + originalFileName;
            // Create directories if they don't exist
            if (!Files.exists(categoryPath)) {
                Files.createDirectories(categoryPath);
            }

            // Construct the file path
            Path filePath = categoryPath.resolve(imageFileName);

            // Save the file
            Files.copy(logo.getInputStream(), filePath,  StandardCopyOption.REPLACE_EXISTING);

        }catch(Exception e){
            e.printStackTrace();
            flag = false;
        }
        return flag;
    }

}

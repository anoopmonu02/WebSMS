package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.Finehead;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.SchoolService;
import jakarta.validation.Valid;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class GlobalController {

    private final AcademicyearService academicyearService;
    private final SchoolService schoolService;

    public GlobalController(AcademicyearService academicyearService, SchoolService schoolService){
        this.academicyearService = academicyearService;
        this.schoolService = schoolService;
    }

    /********************************   Academic year Code starts here   ************************************/

    @GetMapping("/academicyear")
    public String academciyear(Model model){
        List<AcademicYear> academicYears = academicyearService.getAllAcademiyears();
        model.addAttribute("academicYears", academicYears);
        model.addAttribute("hasAcademicyears", !academicYears.isEmpty());
        return "admin/academicyear";
    }

    @GetMapping("/academicyear/add")
    public String addAcademicyearForm(Model model){
        model.addAttribute("academicyear", new AcademicYear());
        model.addAttribute("schools", schoolService.getAllSchools());
        return "/admin/add-academicyear";
    }

    @PostMapping("/academicyear")
    public String saveAcademicYear(@Valid @ModelAttribute("academicyear")AcademicYear academicYear, BindingResult result, Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            model.addAttribute("schools", schoolService.getAllSchools());
            return "/admin/add-academicyear";
        }
        System.out.println(academicYear.getStartDate());
        System.out.println(academicYear.getEndDate());
        System.out.println(academicYear.getSchool());
        try{
            School school = schoolService.getSchoolById(Long.parseLong("4")).get();
            academicYear.setSchool(school);
            academicyearService.save(academicYear);
            ra.addFlashAttribute("success","Academic year - "+academicYear.getSessionFormat()+ " saved successfully.");
        }catch(DataIntegrityViolationException de){
            de.printStackTrace();
            model.addAttribute("error", "Duplicate entry '"+ academicYear.getSessionFormat() +"' for Academcic-Year.");
            model.addAttribute("schools", schoolService.getAllSchools());
            return "/admin/add-academicyear";
        }catch (Exception e){
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
            model.addAttribute("schools", schoolService.getAllSchools());
            return "/admin/add-academicyear";
        }

        return "redirect:/admin/academicyear";
    }

    @GetMapping("/academicyear/edit/{id}")
    public String editAcademicYearPage(@PathVariable("id")Long id, Model model){
        AcademicYear academicYear = academicyearService.getAcademicyearById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid academic-year Id:" + id));
        model.addAttribute("academicyear", academicYear);
        model.addAttribute("schools", schoolService.getAllSchools());
        return "/admin/edit-academicyear";
    }

    @PostMapping("/academicyear/{id}")
    public String updateAcademicYear(@PathVariable("id") Long id, @Valid @ModelAttribute("academicyear") AcademicYear academicyear,
                                 BindingResult result, Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            model.addAttribute("schools", schoolService.getAllSchools());
            return "/admin/edit-academicyear";
        }
        try{
            School school = schoolService.getSchoolById(Long.parseLong("4")).get();
            academicyear.setSchool(school);
            academicyearService.save(academicyear);
            ra.addFlashAttribute("success","Academic year - "+academicyear.getSessionFormat()+ " Updated successfully.");
        }catch(DataIntegrityViolationException de){
            de.printStackTrace();
            model.addAttribute("error", "Duplicate entry '"+ academicyear.getSessionFormat() +"' for Academic-Year.");
            model.addAttribute("schools", schoolService.getAllSchools());
            return "/admin/edit-academicyear";
        }catch (Exception e){
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
            model.addAttribute("schools", schoolService.getAllSchools());
            return "/admin/edit-academicyear";
        }

        return "redirect:/admin/academicyear";
    }

}

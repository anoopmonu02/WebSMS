package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.FeeDate;
import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.FeedateService;
import com.smsweb.sms.services.admin.MonthmappingService;
import com.smsweb.sms.services.admin.SchoolService;
import com.smsweb.sms.services.universal.MonthMasterService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class GlobalController {

    private final AcademicyearService academicyearService;
    private final MonthmappingService monthmappingService;
    private final SchoolService schoolService;
    private final MonthMasterService monthMasterService;
    private final FeedateService feedateService;

    public GlobalController(AcademicyearService academicyearService, SchoolService schoolService, MonthmappingService monthmappingService, MonthMasterService monthMasterService,
                            FeedateService feedateService){
        this.academicyearService = academicyearService;
        this.schoolService = schoolService;
        this.monthmappingService = monthmappingService;
        this.monthMasterService = monthMasterService;
        this.feedateService = feedateService;
    }

    /********************************   Academic year Code starts here   ************************************/

    @GetMapping("/academicyear")
    public String academciyear(Model model){
        //Get data of school when loggedin
        List<AcademicYear> academicYears = academicyearService.getAllAcademiyears(4L);
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

    /********************************   Month-Mapping Code starts here   ************************************/

    @GetMapping("/month-mapping")
    public String getMonthmappings(Model model){
        //Get data of school and academicyear when loggedin
        List<MonthMapping> monthmappings = monthmappingService.getAllMonthMapping(14L, 4L);
        model.addAttribute("monthmappings", monthmappings);
        model.addAttribute("hasMonthMappings", !monthmappings.isEmpty());
        return "/admin/monthmapping";
    }

    @GetMapping("/month-mapping/add")
    public String getAddMonthMappingForm(Model model){
        List<MonthMaster> months = monthMasterService.getAllMonths();
        model.addAttribute("months", months);
        model.addAttribute("monthMapping", new MonthMapping());
        model.addAttribute("hasMonths", !months.isEmpty());
        /*List<Integer> numbers = IntStream.rangeClosed(1, 12).boxed().collect(Collectors.toList());*/
        //model.addAttribute("numbers", numbers);
        return "/admin/add-month-mapping";
    }


    @PostMapping("/month-mapping")
    public String saveMonthMapping(@RequestParam("monthMaster") Long monthMaster, RedirectAttributes redirectAttributes, Model model){
        System.out.println("id----"+monthMaster);
        MonthMaster selectedMonth = monthMasterService.getMonthById(monthMaster).get();
        System.out.println("monthMapping----"+selectedMonth);
        List<MonthMaster> months = monthMasterService.getAllMonths();
        try{
            if(selectedMonth!=null){
                AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
                School school = schoolService.getSchoolById(4L).get();
                String msg = monthmappingService.save(selectedMonth, academicYear, school);
                if(msg.equalsIgnoreCase("success")){
                    redirectAttributes.addFlashAttribute("success","Month mapping generated for this academic year-"+academicYear.getSessionFormat());
                }
                else{
                    model.addAttribute("months", months);
                    model.addAttribute("monthMapping", new MonthMapping());
                    return "/admin/add-month-mapping";
                }
            }
        }catch(RuntimeException re){
            model.addAttribute("months", months);
            model.addAttribute("monthMapping", new MonthMapping());
            model.addAttribute("error","Error in saving: "+re.getMessage());
            re.printStackTrace();
            return "/admin/add-month-mapping";
        }catch(Exception e){
            model.addAttribute("months", months);
            model.addAttribute("monthMapping", new MonthMapping());
            model.addAttribute("error","Error in saving: "+e.getMessage());
            e.printStackTrace();
            return "/admin/add-month-mapping";
        }

        return "redirect:/admin/month-mapping";
    }

    /********************************   Fee Date Code starts here   ************************************/

    @GetMapping("/feedate")
    public String getFeeDate(Model model){
        //Get data of school and academicyear when loggedin
        List<FeeDate> feeDateList = feedateService.getAllFeeDates(14L, 4L);
        model.addAttribute("feedates", feeDateList);
        model.addAttribute("isFeeDates", !feeDateList.isEmpty());
        return "/admin/feedate";
    }

    @GetMapping("/feedate/add")
    public String getAddFeeDateForm(Model model){
        model.addAttribute("feedate", new FeeDate());
        model.addAttribute("months", monthMasterService.getAllMonths());
        return "/admin/add-feedate";
    }

    @PostMapping("/feedate")
    public String save(@Valid @ModelAttribute("feedate")FeeDate feedate, BindingResult result, Model model, RedirectAttributes redirectAttributes){
        if(result.hasErrors()){
            model.addAttribute("months", monthMasterService.getAllMonths());
            model.addAttribute("error", result.getFieldError());
            return "/admin/add-feedate";
        }
        try{
            School school = schoolService.getSchoolById(4L).get();
            AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
            feedate.setAcademicYear(academicYear);
            feedate.setSchool(school);
            feedateService.save(feedate);
            redirectAttributes.addFlashAttribute("success","Fee Date saved successfully for: "+feedate.getMonthMaster().getMonthName());
        }catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for "+feedate.getMonthMaster().getMonthName());
            model.addAttribute("months", monthMasterService.getAllMonths());
            de.printStackTrace();
            return "/admin/add-feedate";
        }catch(Exception e){
            model.addAttribute("error", "Error in saving: "+e.getLocalizedMessage());
            model.addAttribute("months", monthMasterService.getAllMonths());
            e.printStackTrace();
            return "/admin/add-feedate";
        }
        return "redirect:/admin/feedate";
    }

    //@DeleteMapping("/feedate/delete/{id}")
    @RequestMapping(value = "/feedate/delete/{id}", method = {RequestMethod.POST, RequestMethod.DELETE})
    public String deleteFeeDate(@PathVariable("id")Long id, RedirectAttributes redirectAttributes, Model model){
        try{
            String returnMsg = feedateService.delete(id);
            if(returnMsg=="success"){
                model.addAttribute("info","Fee date deleted.");
                redirectAttributes.addFlashAttribute("info","Fee date deleted.");
                return "redirect:/admin/feedate";
            }
        }catch(Exception e){
            e.printStackTrace();
            model.addAttribute("error","Error in deletion "+e.getLocalizedMessage());
        }
        return "/admin/feedate";
    }

}

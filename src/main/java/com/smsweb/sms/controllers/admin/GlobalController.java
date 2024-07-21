package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.models.admin.*;
import com.smsweb.sms.models.universal.Feehead;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.services.admin.*;
import com.smsweb.sms.services.universal.FeeheadService;
import com.smsweb.sms.services.universal.FineheadService;
import com.smsweb.sms.services.universal.GradeService;
import com.smsweb.sms.services.universal.MonthMasterService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin")
public class GlobalController {

    private final AcademicyearService academicyearService;
    private final MonthmappingService monthmappingService;
    private final SchoolService schoolService;
    private final MonthMasterService monthMasterService;
    private final FeedateService feedateService;
    private final FineService fineService;
    private final FineheadService fineheadService;
    private final FeeclassmapService feeclassmapService;

    private final FeeheadService feeheadService;
    private final GradeService gradeService;

    @Autowired
    public GlobalController(AcademicyearService academicyearService, SchoolService schoolService, MonthmappingService monthmappingService, MonthMasterService monthMasterService,
                            FeedateService feedateService, FineService fineService, FineheadService fineheadService, FeeclassmapService feeclassmapService,
                            FeeheadService feeheadService, GradeService gradeService){
        this.academicyearService = academicyearService;
        this.schoolService = schoolService;
        this.monthmappingService = monthmappingService;
        this.monthMasterService = monthMasterService;
        this.feedateService = feedateService;
        this.fineService = fineService;
        this.fineheadService = fineheadService;
        this.feeclassmapService = feeclassmapService;
        this.feeheadService = feeheadService;
        this.gradeService = gradeService;
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
    @PostMapping("/feedate/delete/{id}")
    @ResponseBody
    public Map<String, String> deleteFeeDate(@PathVariable("id")Long id){
        Map<String, String> response = new HashMap<>();
        try{
            String returnMsg = feedateService.delete(id);
            if ("success".equals(returnMsg)) {
                response.put("status", "success");
                response.put("message", "Fee date deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete fee date.");
            }
        }catch(Exception e){
            response.put("status", "error");
            response.put("message", "Error in deletion: " + e.getLocalizedMessage());
        }
        return response;
    }

    /*********************************************  Fine Code Block starts here  *****************************************/

    @GetMapping("/fine")
    public String getFineForm(Model model){
        List<Fine> fineList = fineService.getAllFines(4L, 14L);
        model.addAttribute("fines", fineList);
        model.addAttribute("isFine", !fineList.isEmpty());
        return "/admin/fine";
    }

    @GetMapping("/fine/add")
    public String getFineAddForm(Model model){
        model.addAttribute("fine", new Fine());
        model.addAttribute("fineheads", fineheadService.getAllFineHeads());
        return "/admin/add-fine";
    }

    @PostMapping("/fine")
    public String saveFineData(@Valid @ModelAttribute("fine")Fine fine, BindingResult result, Model model, RedirectAttributes redirectAttributes){
        if(result.hasErrors()){
            model.addAttribute("fineheads", fineheadService.getAllFineHeads());
            model.addAttribute("error", result.getFieldError());
            return "/admin/add-fine";
        }
        try{
            School school = schoolService.getSchoolById(4L).get();
            AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
            fine.setAcademicYear(academicYear);
            fine.setSchool(school);
            String returnMsg = "Fine saved successfully for: "+fine.getFinehead().getFineHeadName();
            if(fine.getId()!=null){
                returnMsg = "Fine updated successfully for: "+fine.getFinehead().getFineHeadName();
            }
            fineService.saveFine(fine);
            redirectAttributes.addFlashAttribute("success",returnMsg);
        }catch(DataIntegrityViolationException de){
            model.addAttribute("error","Duplicate entry for "+fine.getFinehead().getFineHeadName());
            model.addAttribute("fineheads", fineheadService.getAllFineHeads());
            de.printStackTrace();
            return "/admin/add-fine";
        }catch(Exception e){
            model.addAttribute("error", "Error in saving: "+e.getLocalizedMessage());
            model.addAttribute("fineheads", fineheadService.getAllFineHeads());
            e.printStackTrace();
            return "/admin/add-fine";
        }
        return "redirect:/admin/fine";
    }

    @GetMapping("/fine/edit/{id}")
    public String editFineForm(@PathVariable("id")Long id, Model model){
        Fine fine = fineService.getFineById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid fine Id:" + id));
        model.addAttribute("fine",fine);
        model.addAttribute("fineheads", fineheadService.getAllFineHeads());
        return "/admin/edit-fine";
    }

    @PostMapping("/fine/delete/{id}")
    @ResponseBody
    public Map<String, String> deleteFineDate(@PathVariable("id")Long id){
        Map<String, String> response = new HashMap<>();
        try{
            String returnMsg = fineService.deleteFine(id);
            if ("success".equals(returnMsg)) {
                response.put("status", "success");
                response.put("message", "Fine deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete fine.");
            }
        }catch(Exception e){
            response.put("status", "error");
            response.put("message", "Error in deletion: " + e.getLocalizedMessage());
        }
        return response;
    }

    /****************************  Fee Mapping Code Starts Here  ******************************/

    @GetMapping("/fee-class")
    public String getFeeClassDetails(Model model){
        List<FeeClassMap> feeClassMaps = feeclassmapService.getAllFeeClassMapping(4L, 14L);
        model.addAttribute("feeclass", feeClassMaps);
        model.addAttribute("hasFeeClassMap", !feeClassMaps.isEmpty());
        return "/admin/feeclassmap";
    }

    @GetMapping("/fee-class/add")
    public String getAddFeeClassMappingForm(Model model){
        //model.addAttribute("feeheads", feeheadService.getAllFeeheads());
        model.addAttribute("grades", gradeService.getAllGrades());
        FeeClassMapWrapper feeClassMapWrapper = new FeeClassMapWrapper();
        model.addAttribute("feeClassMapWrapper", feeClassMapWrapper);
        return "/admin/add-feeclassmap";
    }

    @PostMapping("/fee-class/getAllFeeData/{classId}")
    @ResponseBody
    public Map<String, Map<String, String>> getAllFeeData(@PathVariable("classId")Long classId){
        Map<String, Map<String, String>> responseMap = new HashMap<>();
        //map - fee - amount
        try{
            Map<String, String> finalMap = new HashMap<>();
            Set<String> processedFeeheads = new HashSet<>();
            List<FeeClassMap> feeClassMapList = feeclassmapService.getAllFeeClassMappingByGrade(classId, 4L, 14L);
            List<Feehead> feeheadList = feeheadService.getAllFeeheads();
            if(feeClassMapList!=null && !feeClassMapList.isEmpty()){
                feeClassMapList.forEach(fcm -> {
                    if(feeheadList.contains(fcm.getFeehead())){
                        String feeheadKey = fcm.getFeehead().getId() + ":" + fcm.getFeehead().getFeeHeadName();
                        String finalMapKey = feeheadKey + ":" + fcm.getId();
                        finalMap.put(finalMapKey, fcm.getAmount().toString());
                        processedFeeheads.add(feeheadKey); // Track processed feeheads
                    }
                });
                // Add remaining feeheads that are not present in feeClassMapList
                feeheadList.forEach(fh -> {
                    String feeheadKey = fh.getId() + ":" + fh.getFeeHeadName();
                    if (!processedFeeheads.contains(feeheadKey)) {
                        finalMap.put(feeheadKey + ":-1", "0");
                    }
                });
            } else{
                // If feeClassMapList is empty, add all feeheads with default values
                feeheadList.forEach(fh -> {
                    System.out.println("fh"+fh.getClass());
                    finalMap.put(fh.getId()+":"+fh.getFeeHeadName()+":-1", "0");
                });
            }
            responseMap.put("success", finalMap);
        }catch(Exception e){
            responseMap.put("error", new HashMap<>());
        }
        System.out.println(responseMap);
        return responseMap;
    }

    @PostMapping("/fee-class")
    public String saveFeeClassMappings(@ModelAttribute FeeClassMapWrapper feeClassMapWrapper, BindingResult result, Model model, RedirectAttributes redirectAttributes){
        List<FeeClassMap> feeClassMaps = feeClassMapWrapper.getFeeClassMaps();
        System.out.println("feeClassMaps: "+feeClassMaps);
        System.out.println("result: "+result);

        try{
            List<FeeClassMap> feeClassMapList = new ArrayList<>();
            School school = schoolService.getSchoolById(4L).get();
            AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
            Grade grade = feeClassMaps.get(0).getGrade();
            for (FeeClassMap fee : feeClassMaps) {
                System.out.println("Fee Head Name: " + fee.getFeehead());
                System.out.println("Amount: " + fee.getAmount());
                fee.setAcademicYear(academicYear);
                fee.setSchool(school);
                fee.setGrade(grade);
                System.out.println("Grade "+fee.getGrade());
                feeClassMapList.add(feeclassmapService.save(fee));
            }
            //Can't use this method because school+academic-year+user details added separately
            //List<FeeClassMap> feeClassMapList = feeclassmapService.saveAllFeeClassMap(feeClassMaps);
            if(feeClassMapList!=null && feeClassMapList.size()>0){
                redirectAttributes.addFlashAttribute("success","Fee-Class Mapping saved for Grade:"+grade.getGradeName());
            } else{
                redirectAttributes.addFlashAttribute("info","Data not saved, re-check the data.");
            }
        }catch(Exception e){
            model.addAttribute("error", "Error: "+e.getLocalizedMessage());
            return "/admin/add-feeclassmap";
        }
        return "redirect:/admin/fee-class";
    }

    @GetMapping("/fee-class/edit/{id}")
    public String editFeeClassForm(@PathVariable("id")Long id, Model model){
        FeeClassMap feeClassMap = feeclassmapService.getFeeClassMapById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid fee-class Id:" + id));
        model.addAttribute("feeclassmap",feeClassMap);
        model.addAttribute("gradename",feeClassMap.getGrade().getGradeName());
        return "/admin/edit-feeclassmap";
    }

    @PostMapping("/edit-fee-class")
    public String updateFeeClassMap(@Valid @ModelAttribute("feeclassmap")FeeClassMap feeClassMap, BindingResult result, Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            return "/admin/edit-feeclassmap";
        }
        try{
            feeclassmapService.save(feeClassMap);
            ra.addFlashAttribute("info", "Fee-Class mapping updated for Grade: "+feeClassMap.getGrade().getGradeName());
        }catch(Exception e){
            e.printStackTrace();
            model.addAttribute("error","Error: "+e.getLocalizedMessage());
            return "/admin/edit-feeclassmap";
        }
        return "redirect:/admin/fee-class";
    }

    @PostMapping("/fee-class/delete/{id}")
    @ResponseBody
    public Map<String, String> deleteFeeClassMap(@PathVariable("id")Long id){
        Map<String, String> response = new HashMap<>();
        try{
            String returnMsg = feeclassmapService.delete(id);
            if ("success".equals(returnMsg)) {
                response.put("status", "success");
                response.put("message", "Fine deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete fine.");
            }
        }catch(ObjectNotDeleteException oe){
            response.put("status", "error");
            response.put("message", "Error in deletion: " + oe.getLocalizedMessage());
        } catch (Exception e){
            response.put("status", "error");
            response.put("message", "Error in deletion: " + e.getLocalizedMessage());
        }
        return response;
    }

}

package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.config.AcademicYearHolder;
import com.smsweb.sms.config.SchoolHolder;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.Roles;
import com.smsweb.sms.models.admin.*;
import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.models.universal.Feehead;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.repositories.users.RoleRepository;
import com.smsweb.sms.services.Employee.EmployeeService;
import com.smsweb.sms.services.admin.*;
import com.smsweb.sms.services.universal.*;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("/admin")
public class GlobalController extends BaseController {

    private final AcademicyearService academicyearService;
    private final MonthmappingService monthmappingService;
    private final SchoolService schoolService;
    private final MonthMasterService monthMasterService;
    private final FeedateService feedateService;
    private final FineService fineService;
    private final FineheadService fineheadService;
    private final FeeclassmapService feeclassmapService;
    private final FeemonthmapService feemonthmapService;
    private final FeeheadService feeheadService;
    private final DiscountService discountService;
    private final GradeService gradeService;
    private final DiscountclassmapService discountclassmapService;
    private final DiscountmonthmapService discountmonthmapService;
    private final FullpaymentService fullpaymentService;
    private final UserService userService;
    private final EmployeeService employeeService;
    private final RoleRepository roleRepository;

    private final AcademicYearHolder academicYearHolder;
    private final SchoolHolder schoolHolder;

    private final HolidayService holidayService;
    private final ExaminationService examinationService;

    @Autowired
    public GlobalController(AcademicyearService academicyearService, SchoolService schoolService, MonthmappingService monthmappingService, MonthMasterService monthMasterService,
                            FeedateService feedateService, FineService fineService, FineheadService fineheadService, FeeclassmapService feeclassmapService,
                            FeeheadService feeheadService, GradeService gradeService, FeemonthmapService feemonthmapService, DiscountclassmapService discountclassmapService,
                            DiscountService discountService, DiscountmonthmapService discountmonthmapService, FullpaymentService fullpaymentService, UserService userService, EmployeeService employeeService, RoleRepository roleRepository, AcademicYearHolder academicYearHolder, SchoolHolder schoolHolder, HolidayService holidayService, ExaminationService examinationService){
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
        this.feemonthmapService = feemonthmapService;
        this.discountclassmapService = discountclassmapService;
        this.discountService = discountService;
        this.discountmonthmapService = discountmonthmapService;
        this.fullpaymentService = fullpaymentService;
        this.userService = userService;
        this.employeeService = employeeService;
        this.roleRepository = roleRepository;
        this.academicYearHolder = academicYearHolder;
        this.schoolHolder = schoolHolder;
        this.holidayService = holidayService;
        this.examinationService = examinationService;
    }

    /********************************   Academic year Code starts here   ************************************/

    @GetMapping("/academicyear")
    public String academciyear(Model model){
        //Get data of school when loggedin
        List<AcademicYear> academicYears;
        School school = (School)model.getAttribute("school");
        System.out.println("school holder "+school.getSchoolName());
        if(isSuperAdminLoggedIn()){
            academicYears  = academicyearService.getAllAcademicYear();
        }
        else{
            academicYears = academicyearService.getAllAcademiyears(school.getId());
        }
        model.addAttribute("academicYears", academicYears);
        model.addAttribute("hasAcademicyears", !academicYears.isEmpty());
        model.addAttribute("page", "datatable");
        return "admin/academicyear";
    }

    @GetMapping("/academicyear/add")
    public String addAcademicyearForm(Model model){
        model.addAttribute("academicyear", new AcademicYear());
        if(isSuperAdminLoggedIn()){
            model.addAttribute("superUserLogin", true);
            model.addAttribute("schools", schoolService.getAllSchools());
        }
        else if(isAdminLogin()){
            model.addAttribute("adminLogin", true);
            model.addAttribute("school", employeeService.getLoggedInEmployeeSchool());
        }
        return "/admin/add-academicyear";
    }

    @PostMapping("/academicyear")
    public String saveAcademicYear(@Valid @ModelAttribute("academicyear") AcademicYear academicYear,
                                   BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("schools", schoolService.getAllSchools());
            return "/admin/add-academicyear";
        }

        try {
            // Ensure school is set
            if (academicYear.getSchool() == null || academicYear.getSchool().getId() == null) {
                throw new IllegalArgumentException("School selection is mandatory.");
            }
            School school = schoolService.getSchoolById(academicYear.getSchool().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid School ID."));

            academicYear.setSchool(school);
            // Save AcademicYear
            academicyearService.save(academicYear);
            ra.addFlashAttribute("success", "Academic year - " + academicYear.getSessionFormat() + " saved successfully.");
        } catch (DataIntegrityViolationException de) {
            de.printStackTrace();
            model.addAttribute("error", "Duplicate entry '" + academicYear.getSessionFormat() + "' for Academic Year.");
            model.addAttribute("schools", schoolService.getAllSchools());
            return "/admin/add-academicyear";
        } catch (Exception e) {
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
        if(isSuperAdminLoggedIn()){
            model.addAttribute("superUserLogin", true);
            model.addAttribute("schools", schoolService.getAllSchools());
        }
        else if(isAdminLogin()){
            model.addAttribute("adminLogin", true);
            model.addAttribute("school", employeeService.getLoggedInEmployeeSchool());
        }
        return "/admin/edit-academicyear";
    }

    @PostMapping("/academicyear/{id}")
    public String updateAcademicYear(@PathVariable("id") Long id, @Valid @ModelAttribute("academicyear") AcademicYear academicYear,
                                 BindingResult result, Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            model.addAttribute("schools", schoolService.getAllSchools());
            return "/admin/edit-academicyear";
        }
        try{
            if (academicYear.getSchool() == null || academicYear.getSchool().getId() == null) {
                throw new IllegalArgumentException("School selection is mandatory.");
            }
            School school = schoolService.getSchoolById(academicYear.getSchool().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid School ID."));

            academicYear.setSchool(school);
            academicYear.setUpdatedBy(userService.getLoggedInUser().getUsername());
            academicyearService.save(academicYear);
            ra.addFlashAttribute("success","Academic year - "+academicYear.getSessionFormat()+ " Updated successfully.");
        }catch(DataIntegrityViolationException de){
            de.printStackTrace();
            model.addAttribute("error", "Duplicate entry '"+ academicYear.getSessionFormat() +"' for Academic-Year.");
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
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<MonthMapping> monthmappings = monthmappingService.getAllMonthMapping(academicYear.getId(), school.getId());
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

                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
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
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<FeeDate> feeDateList = feedateService.getAllFeeDates(academicYear.getId(), school.getId());
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
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            feedate.setAcademicYear(academicYear);
            feedate.setSchool(school);
            feedate.setCreatedBy(userService.getLoggedInUser().getUsername());
            feedateService.save(feedate);
            redirectAttributes.addFlashAttribute("success","Fee Date saved successfully for: "+feedate.getMonthMaster().getMonthName());
        }catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for "+feedate.getMonthMaster().getMonthName());
            model.addAttribute("months", monthMasterService.getAllMonths());
            de.printStackTrace();
            return "/admin/add-feedate";
        }catch(UniqueConstraintsException de){
            model.addAttribute("error", "Duplicate entry for "+feedate.getMonthMaster().getMonthName()+". "+de.getLocalizedMessage());
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
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<Fine> fineList = fineService.getAllFines(school.getId(), academicYear.getId());
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
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            fine.setAcademicYear(academicYear);
            fine.setSchool(school);
            String returnMsg = "Fine saved successfully for: "+fine.getFinehead().getFineHeadName();
            if(fine.getId()!=null){
                fine.setUpdatedBy(userService.getLoggedInUser().getUsername());
                returnMsg = "Fine updated successfully for: "+fine.getFinehead().getFineHeadName();
            }
            else{
                fine.setCreatedBy(userService.getLoggedInUser().getUsername());
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
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<FeeClassMap> feeClassMaps = feeclassmapService.getAllFeeClassMapping(school.getId(), academicYear.getId());
        model.addAttribute("feeclass", feeClassMaps);
        model.addAttribute("hasFeeClassMap", !feeClassMaps.isEmpty());
        model.addAttribute("page", "datatable");
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
    public Map<String, Map<String, String>> getAllFeeData(@PathVariable("classId")Long classId, HttpSession session, Model model){
        Map<String, Map<String, String>> responseMap = new HashMap<>();
        //map - fee - amount
        try{
            Map<String, String> finalMap = new HashMap<>();
            Set<String> processedFeeheads = new HashSet<>();
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            if (academicYear == null) {
                model.addAttribute("errorMessage", "Academic Year not found in session");
                responseMap.put("error", new HashMap<>()); // Redirect to an error page or display an error message
            }
            List<FeeClassMap> feeClassMapList = feeclassmapService.getAllFeeClassMappingByGrade(classId, school.getId(), academicYear.getId());
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
            e.printStackTrace();
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
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            Grade grade = feeClassMaps.get(0).getGrade();
            for (FeeClassMap fee : feeClassMaps) {
                System.out.println("Fee Head Name: " + fee.getFeehead());
                System.out.println("Amount: " + fee.getAmount());
                fee.setAcademicYear(academicYear);
                fee.setSchool(school);
                fee.setGrade(grade);
                System.out.println("Grade "+fee.getGrade());
                fee.setCreatedBy(userService.getLoggedInUser().getUsername());
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
            feeClassMap.setUpdatedBy(userService.getLoggedInUser().getUsername());
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
                response.put("message", "Fee-Class mapping deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete Fee-Class mapping.");
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

    /*****************************  Fee-Month Mapping Code starts here  ********************************/

    @GetMapping("/fee-month")
    public String getFeeMonthDetails(Model model){
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<FeeMonthMap> feeMonthMaps = feemonthmapService.getAllFeeMonthMap(school.getId(), academicYear.getId());
        model.addAttribute("feemonths", feeMonthMaps);
        model.addAttribute("hasFeeMonthMap", !feeMonthMaps.isEmpty());
        model.addAttribute("page", "datatable");
        return "/admin/feemonthmap";
    }

    @GetMapping("/fee-month/add")
    public String getAddFeeMonthMappingForm(Model model){
        model.addAttribute("fees", feeheadService.getAllFeeheads());
        FeeMonthMapWrapper feeMonthMapWrapper = new FeeMonthMapWrapper();
        model.addAttribute("feeMonthMapWrapper", feeMonthMapWrapper);
        return "/admin/add-feemonthmap";
    }

    @PostMapping("/fee-month/getAllFeeMonthData/{feeId}")
    @ResponseBody
    public Map<String, Map<String, Boolean>> getAllFeeMonthData(@PathVariable("feeId")Long feeId, Model model){
        Map<String, Map<String, Boolean>> responseMap = new HashMap<>();
        //map - fee - amount
        try{
            Map<String, Boolean> finalMap = new HashMap<>();
            Set<String> processedMonths = new HashSet<>();
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            List<FeeMonthMap> feeMonthMapList = feemonthmapService.getAllFeeMonthMapByFee(school.getId(), academicYear.getId(), feeId);
            List<MonthMaster> monthMasters = monthMasterService.getAllMonths();
            if(feeMonthMapList!=null && !feeMonthMapList.isEmpty()){
                feeMonthMapList.forEach(fcm -> {
                    if(monthMasters.contains(fcm.getMonthMaster())){
                        String finalMapKey = fcm.getMonthMaster().getId() + ":" + fcm.getMonthMaster().getMonthName() + ":" + fcm.getId();
                        finalMap.put(finalMapKey, fcm.getIsApplicable());
                        processedMonths.add(fcm.getMonthMaster().getId() + ":" + fcm.getMonthMaster().getMonthName()); // Track processed months
                    }
                });
                // Add remaining months that are not present in feeClassMapList
                monthMasters.forEach(fh -> {
                    String feeheadKey = fh.getId() + ":" + fh.getMonthName();
                    if (!processedMonths.contains(feeheadKey)) {
                        finalMap.put(feeheadKey + ":-1", false);
                    }
                });
            } else{
                // If feeMonthMapList is empty, add all months with default values
                monthMasters.forEach(fh -> {
                    finalMap.put(fh.getId()+":"+fh.getMonthName()+":-1", false);
                });
            }
            Map<String, Boolean> sortedSubMap = new TreeMap<>(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    // Extract IDs from the keys and compare them
                    int id1 = Integer.parseInt(o1.split(":")[0]);
                    int id2 = Integer.parseInt(o2.split(":")[0]);
                    return Integer.compare(id1, id2);
                }
            });
            sortedSubMap.putAll(finalMap);
            responseMap.put("success", sortedSubMap);
        }catch(Exception e){
            responseMap.put("error", new HashMap<>());
        }
        System.out.println(responseMap);
        return responseMap;
    }

    @PostMapping("/fee-month")
    public String saveFeeMonthMappings(@ModelAttribute FeeMonthMapWrapper feeMonthMapWrapper, BindingResult result, Model model, RedirectAttributes redirectAttributes){
        List<FeeMonthMap> feeMonthMaps = feeMonthMapWrapper.getFeeMonthMaps();
        System.out.println("feeMonthMaps: "+feeMonthMaps);
        System.out.println("result: "+result);

        try{
            List<FeeMonthMap> feeMonthMapList = new ArrayList<>();
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            Feehead feehead = feeMonthMaps.get(0).getFeehead();
            for (FeeMonthMap fee : feeMonthMaps) {
                System.out.println("Fee Head Name: " + fee.getMonthMaster());
                System.out.println("Amount: " + fee.getIsApplicable());
                fee.setAcademicYear(academicYear);
                fee.setSchool(school);
                fee.setFeehead(feehead);
                System.out.println("Grade "+fee.getFeehead());
                fee.setCreatedBy(userService.getLoggedInUser().getUsername());
                feeMonthMapList.add(feemonthmapService.saveFeeMonth(fee));
            }
            //Can't use this method because school+academic-year+user details added separately
            //List<FeeClassMap> feeClassMapList = feeclassmapService.saveAllFeeClassMap(feeClassMaps);
            if(feeMonthMapList!=null && feeMonthMapList.size()>0){
                redirectAttributes.addFlashAttribute("success","Fee-Class Mapping saved for Fee:"+feehead.getFeeHeadName());
            } else{
                redirectAttributes.addFlashAttribute("info","Data not saved, re-check the data.");
            }
        }catch(Exception e){
            model.addAttribute("error", "Error: "+e.getLocalizedMessage());
            return "/admin/add-feemonthmap";
        }
        return "redirect:/admin/fee-month";
    }

    @GetMapping("/fee-month/edit/{id}")
    public String editFeeMonthForm(@PathVariable("id")Long id, Model model){
        FeeMonthMap feeMonthMap = feemonthmapService.getFeeMonthMapById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid fee-month Id:" + id));
        model.addAttribute("feemonthmap",feeMonthMap);
        model.addAttribute("monthname",feeMonthMap.getMonthMaster().getMonthName());
        return "/admin/edit-feemonthmap";
    }

    @PostMapping("/edit-fee-month")
    public String updateFeeMonthMap(@Valid @ModelAttribute("feemonthmap")FeeMonthMap feeMonthMap, BindingResult result, Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            return "/admin/edit-feemonthmap";
        }
        try{
            feeMonthMap.setUpdatedBy(userService.getLoggedInUser().getUsername());
            feemonthmapService.saveFeeMonth(feeMonthMap);
            ra.addFlashAttribute("info", "Fee-Month mapping updated for Fee: "+feeMonthMap.getFeehead().getFeeHeadName());
        }catch(Exception e){
            e.printStackTrace();
            model.addAttribute("error","Error: "+e.getLocalizedMessage());
            return "/admin/edit-feemonthmap";
        }
        return "redirect:/admin/fee-month";
    }

    @PostMapping("/fee-month/delete/{id}")
    @ResponseBody
    public Map<String, String> deleteFeeMonthMap(@PathVariable("id")Long id){
        Map<String, String> response = new HashMap<>();
        try{
            String returnMsg = feemonthmapService.delete(id);
            if ("success".equals(returnMsg)) {
                response.put("status", "success");
                response.put("message", "Fee-Month mapping deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete Fee-Month mapping.");
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


    /****************************  Discount Mapping Code Starts Here  ******************************/

    @GetMapping("/discount-class")
    public String getDiscountClassDetails(Model model){
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<DiscountClassMap> discountClassMaps = discountclassmapService.getAllDiscountClassMapping(school.getId(), academicYear.getId());
        model.addAttribute("discountclasses", discountClassMaps);
        model.addAttribute("hasDiscountClassMap", !discountClassMaps.isEmpty());
        model.addAttribute("page", "datatable");
        return "/admin/discountclassmap";
    }

    @GetMapping("/discount-class/add")
    public String getAddDiscountClassMappingForm(Model model){
        model.addAttribute("grades", gradeService.getAllGrades());
        DiscountClassMapWrapper discountClassMapWrapper = new DiscountClassMapWrapper();
        model.addAttribute("discountClassMapWrapper", discountClassMapWrapper);
        return "/admin/add-discountclassmap";
    }

    @PostMapping("/discount-class/getAllDiscountData/{classId}")
    @ResponseBody
    public Map<String, Map<String, String>> getAllDiscountData(@PathVariable("classId")Long classId, Model model){
        Map<String, Map<String, String>> responseMap = new HashMap<>();
        //map - fee - amount
        try{
            Map<String, String> finalMap = new HashMap<>();
            Set<String> processedDiscountHeads = new HashSet<>();
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            List<DiscountClassMap> discountClassMapList = discountclassmapService.getAllDiscountClassMappingByGrade(school.getId(), academicYear.getId(), classId);
            List<Discounthead> discountheadList = discountService.getAllDiscountheads();
            if(discountClassMapList!=null && !discountClassMapList.isEmpty()){
                discountClassMapList.forEach(fcm -> {
                    if(discountheadList.contains(fcm.getDiscounthead())){
                        String feeheadKey = fcm.getDiscounthead().getId() + ":" + fcm.getDiscounthead().getDiscountName();
                        String finalMapKey = feeheadKey + ":" + fcm.getId();
                        finalMap.put(finalMapKey, fcm.getAmount().toString());
                        processedDiscountHeads.add(feeheadKey); // Track processed discountheads
                    }
                });
                // Add remaining discountheads that are not present in discountClassMapList
                discountheadList.forEach(fh -> {
                    String feeheadKey = fh.getId() + ":" + fh.getDiscountName();
                    if (!processedDiscountHeads.contains(feeheadKey)) {
                        finalMap.put(feeheadKey + ":-1", "0");
                    }
                });
            } else{
                // If feeClassMapList is empty, add all feeheads with default values
                discountheadList.forEach(fh -> {
                    System.out.println("fh"+fh.getClass());
                    finalMap.put(fh.getId()+":"+fh.getDiscountName()+":-1", "0");
                });
            }
            responseMap.put("success", finalMap);
        }catch(Exception e){
            responseMap.put("error", new HashMap<>());
        }
        System.out.println(responseMap);
        return responseMap;
    }

    @PostMapping("/discount-class")
    public String saveDiscountClassMappings(@ModelAttribute DiscountClassMapWrapper discountClassMapWrapper, BindingResult result, Model model, RedirectAttributes redirectAttributes){
        List<DiscountClassMap> discountClassMaps = discountClassMapWrapper.getDiscountClassMaps();
        System.out.println("discountClassMaps: "+discountClassMaps);
        System.out.println("result: "+result);

        try{
            List<DiscountClassMap> discountClassMapList = new ArrayList<>();
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            Grade grade = discountClassMaps.get(0).getGrade();
            for (DiscountClassMap fee : discountClassMaps) {
                System.out.println("Fee Head Name: " + fee.getDiscounthead());
                System.out.println("Amount: " + fee.getAmount());
                fee.setAcademicYear(academicYear);
                fee.setSchool(school);
                fee.setGrade(grade);
                System.out.println("Grade "+fee.getGrade());
                fee.setCreatedBy(userService.getLoggedInUser().getUsername());
                discountClassMapList.add(discountclassmapService.save(fee));
            }
            //Can't use this method because school+academic-year+user details added separately
            //List<FeeClassMap> feeClassMapList = feeclassmapService.saveAllFeeClassMap(feeClassMaps);
            if(discountClassMapList!=null && discountClassMapList.size()>0){
                redirectAttributes.addFlashAttribute("success","Discount-Class Mapping saved for Grade:"+grade.getGradeName());
            } else{
                redirectAttributes.addFlashAttribute("info","Data not saved, re-check the data.");
            }
        }catch(Exception e){
            model.addAttribute("error", "Error: "+e.getLocalizedMessage());
            return "/admin/add-discountclassmap";
        }
        return "redirect:/admin/discount-class";
    }

    @GetMapping("/discount-class/edit/{id}")
    public String editDiscountClassForm(@PathVariable("id")Long id, Model model){
        DiscountClassMap discountClassMap = discountclassmapService.getDiscountClassMapById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid discount-class Id:" + id));
        model.addAttribute("discountclassmap",discountClassMap);
        model.addAttribute("gradename",discountClassMap.getGrade().getGradeName());
        return "/admin/edit-discountclassmap";
    }

    @PostMapping("/edit-discount-class")
    public String updateDiscountClassMap(@Valid @ModelAttribute("discountclassmap")DiscountClassMap discountClassMap, BindingResult result, Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            return "/admin/edit-discountclassmap";
        }
        try{
            discountClassMap.setUpdatedBy(userService.getLoggedInUser().getUsername());
            discountclassmapService.save(discountClassMap);
            ra.addFlashAttribute("info", "Discount-Class mapping updated for Grade: "+discountClassMap.getGrade().getGradeName());
        }catch(Exception e){
            e.printStackTrace();
            model.addAttribute("error","Error: "+e.getLocalizedMessage());
            return "/admin/edit-discountclassmap";
        }
        return "redirect:/admin/discount-class";
    }

    @PostMapping("/discount-class/delete/{id}")
    @ResponseBody
    public Map<String, String> deleteDiscountClassMap(@PathVariable("id")Long id){
        Map<String, String> response = new HashMap<>();
        try{
            String returnMsg = discountclassmapService.delete(id);
            if ("success".equals(returnMsg)) {
                response.put("status", "success");
                response.put("message", "Discount-Class mapping deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete Discount-Class mapping.");
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

    /*****************************  Discount-Month Mapping Code starts here  ********************************/

    @GetMapping("/discount-month")
    public String getDiscountMonthDetails(Model model){
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<DiscountMonthMap> discountMonthMaps = discountmonthmapService.getAllDiscountMonthMap(school.getId(), academicYear.getId());
        model.addAttribute("discountmonths", discountMonthMaps);
        model.addAttribute("hasDiscountMonthMap", !discountMonthMaps.isEmpty());
        model.addAttribute("page", "datatable");
        return "/admin/discountmonthmap";
    }

    @GetMapping("/discount-month/add")
    public String getAddDiscountMonthMappingForm(Model model){
        model.addAttribute("discounts", discountService.getAllDiscountheads());
        DiscountMonthMapWrapper discountMonthMapWrapper = new DiscountMonthMapWrapper();
        model.addAttribute("discountMonthMapWrapper", discountMonthMapWrapper);
        return "/admin/add-discountmonthmap";
    }

    @PostMapping("/discount-month/getAllDiscountMonthData/{feeId}")
    @ResponseBody
    public Map<String, Map<String, Boolean>> getAllDiscountMonthData(@PathVariable("feeId")Long feeId, Model model){
        Map<String, Map<String, Boolean>> responseMap = new HashMap<>();
        //map - fee - amount
        try{
            Map<String, Boolean> finalMap = new HashMap<>();
            Set<String> processedMonths = new HashSet<>();
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            List<DiscountMonthMap> discountMonthMapList = discountmonthmapService.getAllDiscountMonthMapByDiscount(school.getId(), academicYear.getId(), feeId);
            List<MonthMaster> monthMasters = monthMasterService.getAllMonths();
            if(discountMonthMapList!=null && !discountMonthMapList.isEmpty()){
                discountMonthMapList.forEach(fcm -> {
                    if(monthMasters.contains(fcm.getMonthMaster())){
                        String finalMapKey = fcm.getMonthMaster().getId() + ":" + fcm.getMonthMaster().getMonthName() + ":" + fcm.getId();
                        finalMap.put(finalMapKey, fcm.getIsApplicable());
                        processedMonths.add(fcm.getMonthMaster().getId() + ":" + fcm.getMonthMaster().getMonthName()); // Track processed months
                    }
                });
                // Add remaining months that are not present in feeClassMapList
                monthMasters.forEach(fh -> {
                    String feeheadKey = fh.getId() + ":" + fh.getMonthName();
                    if (!processedMonths.contains(feeheadKey)) {
                        finalMap.put(feeheadKey + ":-1", false);
                    }
                });
            } else{
                // If feeMonthMapList is empty, add all months with default values
                monthMasters.forEach(fh -> {
                    finalMap.put(fh.getId()+":"+fh.getMonthName()+":-1", false);
                });
            }
            Map<String, Boolean> sortedSubMap = new TreeMap<>(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    // Extract IDs from the keys and compare them
                    int id1 = Integer.parseInt(o1.split(":")[0]);
                    int id2 = Integer.parseInt(o2.split(":")[0]);
                    return Integer.compare(id1, id2);
                }
            });
            sortedSubMap.putAll(finalMap);
            responseMap.put("success", sortedSubMap);
        }catch(Exception e){
            responseMap.put("error", new HashMap<>());
        }
        System.out.println(responseMap);
        return responseMap;
    }

    @PostMapping("/discount-month")
    public String saveDiscountMonthMappings(@ModelAttribute DiscountMonthMapWrapper discountMonthMapWrapper, BindingResult result, Model model, RedirectAttributes redirectAttributes){
        List<DiscountMonthMap> discountMonthMaps = discountMonthMapWrapper.getDiscountMonthMaps();
        System.out.println("feeMonthMaps: "+discountMonthMaps);
        System.out.println("result: "+result);

        try{
            List<DiscountMonthMap> discountMonthMapList = new ArrayList<>();
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            Discounthead feehead = discountMonthMaps.get(0).getDiscounthead();
            for (DiscountMonthMap fee : discountMonthMaps) {
                System.out.println("Fee Head Name: " + fee.getMonthMaster());
                System.out.println("Amount: " + fee.getIsApplicable());
                fee.setAcademicYear(academicYear);
                fee.setSchool(school);
                fee.setDiscounthead(feehead);
                System.out.println("Grade "+fee.getDiscounthead());
                fee.setCreatedBy(userService.getLoggedInUser().getUsername());
                discountMonthMapList.add(discountmonthmapService.saveDiscountMonth(fee));
            }
            //Can't use this method because school+academic-year+user details added separately
            //List<FeeClassMap> feeClassMapList = feeclassmapService.saveAllFeeClassMap(feeClassMaps);
            if(discountMonthMapList!=null && discountMonthMapList.size()>0){
                redirectAttributes.addFlashAttribute("success","Discount-Class Mapping saved for Fee:"+feehead.getDiscountName());
            } else{
                redirectAttributes.addFlashAttribute("info","Data not saved, re-check the data.");
            }
        }catch(Exception e){
            model.addAttribute("error", "Error: "+e.getLocalizedMessage());
            return "/admin/add-discountmonthmap";
        }
        return "redirect:/admin/discount-month";
    }

    @GetMapping("/discount-month/edit/{id}")
    public String editDiscountMonthForm(@PathVariable("id")Long id, Model model){
        DiscountMonthMap discountMonthMap = discountmonthmapService.getDiscountMonthMapById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid discount-month Id:" + id));
        model.addAttribute("discountmonthmap",discountMonthMap);
        model.addAttribute("monthname",discountMonthMap.getMonthMaster().getMonthName());
        return "/admin/edit-discountmonthmap";
    }

    @PostMapping("/edit-discount-month")
    public String updateDiscountMonthMap(@Valid @ModelAttribute("discountmonthmap")DiscountMonthMap discountMonthMap, BindingResult result, Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            return "/admin/edit-discountmonthmap";
        }
        try{
            discountMonthMap.setUpdatedBy(userService.getLoggedInUser().getUsername());
            discountmonthmapService.saveDiscountMonth(discountMonthMap);
            ra.addFlashAttribute("info", "Discount-Month mapping updated for Fee: "+discountMonthMap.getDiscounthead().getDiscountName());
        }catch(Exception e){
            e.printStackTrace();
            model.addAttribute("error","Error: "+e.getLocalizedMessage());
            return "/admin/edit-discountmonthmap";
        }
        return "redirect:/admin/discount-month";
    }

    @PostMapping("/discount-month/delete/{id}")
    @ResponseBody
    public Map<String, String> deleteDiscountMonthMap(@PathVariable("id")Long id){
        Map<String, String> response = new HashMap<>();
        try{
            String returnMsg = discountmonthmapService.delete(id);
            if ("success".equals(returnMsg)) {
                response.put("status", "success");
                response.put("message", "Discount-Month mapping deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete Discount-Month mapping.");
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


    /*****************************  Full payment discount Code starts here  ********************************/

    @GetMapping("/full-payment-discount")
    public String getFullPaymentDetails(Model model){
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<FullPayment> fullPaymentList = fullpaymentService.getAllFullPayments(school.getId(), academicYear.getId());
        model.addAttribute("fullpayments", fullPaymentList);
        model.addAttribute("hasFullPayment", !fullPaymentList.isEmpty());
        return "/admin/fullpayment";
    }

    @GetMapping("/full-payment-discount/add")
    public String getAddFullPaymentForm(Model model){
        model.addAttribute("grades", gradeService.getAllGrades());
        model.addAttribute("fullpayment", new FullPayment());
        return "/admin/add-fullpayment";
    }

    @PostMapping("/full-payment-discount")
    public String saveFullPayment(@Valid @ModelAttribute("fullpayment") FullPayment fullPayment, BindingResult result, Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            model.addAttribute("grades", gradeService.getAllGrades());
            return "/admin/add-fullpayment";
        }
        try{
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            fullPayment.setAcademicYear(academicYear);
            fullPayment.setSchool(school);
            String returnMsg = "Full-payment saved successfully for: "+fullPayment.getGrade().getGradeName();
            if(fullPayment.getId()!=null){
                returnMsg = "Full-payment updated successfully for: "+fullPayment.getGrade().getGradeName();
            }
            fullpaymentService.save(fullPayment);
            ra.addFlashAttribute("success", returnMsg);
        }catch(UniqueConstraintsException de){
            model.addAttribute("error", de.getLocalizedMessage());
            model.addAttribute("grades", gradeService.getAllGrades());
            return "/admin/add-fullpayment";
        } catch(ObjectNotSaveException oe){
            model.addAttribute("error", oe.getLocalizedMessage());
            model.addAttribute("grades", gradeService.getAllGrades());
            return "/admin/add-fullpayment";
        } catch(Exception e){
            model.addAttribute("error", e.getLocalizedMessage());
            model.addAttribute("grades", gradeService.getAllGrades());
            return "/admin/add-fullpayment";
        }
        return "redirect:/admin/full-payment-discount";
    }
    @GetMapping("/full-payment-discount/edit/{id}")
    public String editFullPayment(@PathVariable("id")Long id, Model model){
        FullPayment fullPayment = fullpaymentService.getFullPaymentById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid full-payment Id:" + id));
        model.addAttribute("fullpayment",fullPayment);
        return "/admin/edit-fullpayment";
    }

    @PostMapping("/full-payment-discount/delete/{id}")
    @ResponseBody
    public Map<String, String> deleteFullPaymentMap(@PathVariable("id")Long id){
        Map<String, String> response = new HashMap<>();
        try{
            String returnMsg = fullpaymentService.deleteFullPayment(id);
            if ("success".equals(returnMsg)) {
                response.put("status", "success");
                response.put("message", "Full-Payment record deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete Full-Payment.");
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

    /*************************** User-Role *************************/
    @GetMapping("/user-role-list")
    public String getUserRoleList(Model model){
        School school = (School)model.getAttribute("school");
        //List<Employee> employees = employeeService.getAllActiveEmployees(school.getId());
        model.addAttribute("hasUserRoleMapping",false);
        //model.addAttribute("employees",employees);
        //System.out.println("employees:::: "+employees);
        return "/admin/user-role";
    }

    @GetMapping("/add-user-to-role")
    public String addUserRole(Model model){
        School school = (School)model.getAttribute("school");
        List<Employee> employees = employeeService.getAllActiveEmployees(school.getId());
        List<Roles> roles = roleRepository.findAll();
        model.addAttribute("employees", employees);
        model.addAttribute("hasEmployee", !employees.isEmpty());
        model.addAttribute("roles",roles);
        model.addAttribute("hasRoles",!roles.isEmpty());
        return "/admin/add-user-role-map";
    }

    @PostMapping("/api/user-role/save")
    public ResponseEntity<?> saveRoleUserMapping(@RequestBody Map<String, Long> payload){
        try {
            System.out.println("payload "+payload);
            if(payload!=null){
                Long employeeId = payload.get("employeeId");
                Long roleId = payload.get("roleId");
                boolean b = employeeService.saveRoleUserMapping(employeeId, roleId);
                if(!b){
                    return ResponseEntity.ok("Either unable to assign the Role to User or Role already assigned");
                } else{
                    return ResponseEntity.ok("Role assigned successfully");
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error assigning role: " + e.getMessage());
        }
        return ResponseEntity.status(400).body("Unexpected error occurred");
    }

    private boolean isSuperAdminLoggedIn(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName(); // Get logged-in username
            if(username.equalsIgnoreCase("super_admin")){
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

    /********************************   Holiday Code starts here   ************************************/

    @GetMapping("/holidays")
    public String getHoliday(Model model){
        //Get data of school and academicyear when loggedin
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<Holiday> holidayList = holidayService.getAllHoliday(academicYear.getId(), school.getId());
        model.addAttribute("holidays", holidayList);
        model.addAttribute("isHoliDays", !holidayList.isEmpty());
        return "/admin/holiday";
    }

    @GetMapping("/holiday/add")
    public String getAddHolidayForm(Model model){
        model.addAttribute("holiday", new Holiday());
        return "/admin/add-holiday";
    }

    @PostMapping("/holiday")
    public String save(@Valid @ModelAttribute("holiday")Holiday holiday, BindingResult result, Model model, RedirectAttributes redirectAttributes){
        if(result.hasErrors()){
            model.addAttribute("error", result.getFieldError());
            return "/admin/add-holiday";
        }
        try{
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            holiday.setAcademicYear(academicYear);
            holiday.setSchool(school);
            holiday = holidayService.save(holiday);
            System.out.println("holiday: "+holiday);
            redirectAttributes.addFlashAttribute("success","Holiday saved successfully for: "+holiday.getHolidayName());
        }catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for "+holiday.getHolidayName());
            de.printStackTrace();
            return "/admin/add-holiday";
        }catch(UniqueConstraintsException de){
            model.addAttribute("error", "Duplicate entry for "+holiday.getHolidayName()+". "+de.getLocalizedMessage());
            de.printStackTrace();
            return "/admin/add-holiday";
        }catch(Exception e){
            model.addAttribute("error", "Error in saving: "+e.getLocalizedMessage());
            System.out.println("ERRORRRR");
            e.printStackTrace();
            return "/admin/add-holiday";
        }
        return "redirect:/admin/holidays";
    }
    @PostMapping("/holiday/delete/{id}")
    @ResponseBody
    public Map<String, String> deleteHoliday(@PathVariable("id")Long id){
        Map<String, String> response = new HashMap<>();
        try{
            String returnMsg = holidayService.delete(id);
            if ("success".equals(returnMsg)) {
                response.put("status", "success");
                response.put("message", "Holiday deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete holiday.");
            }
        }catch(Exception e){
            response.put("status", "error");
            response.put("message", "Error in deletion: " + e.getLocalizedMessage());
        }
        return response;
    }

    /******************************* Examination Code Starts Here *******************************/
    @GetMapping("/examinations")
    public String getExaminations(Model model){
        List<Examination> examinationList = examinationService.getAllExamination();
        model.addAttribute("examinations", examinationList);
        model.addAttribute("isExamination", !examinationList.isEmpty());
        return "/admin/examination";
    }

    @GetMapping("/examination/add")
    public String getAddExaminationForm(Model model){
        model.addAttribute("examination", new Examination());
        return "/admin/add-examination";
    }

    @PostMapping("/examination/delete/{id}")
    @ResponseBody
    public Map<String, String> deleteExamination(@PathVariable("id")String uuid){
        Map<String, String> response = new HashMap<>();
        try{
            String returnMsg = examinationService.deleteExamination(uuid);
            if ("success".equals(returnMsg)) {
                response.put("status", "success");
                response.put("message", "Examination deleted.");
            } else {
                response.put("status", "error");
                response.put("message", "Failed to delete examination.");
            }
        }catch(Exception e){
            response.put("status", "error");
            response.put("message", "Error in deletion: " + e.getLocalizedMessage());
        }
        return response;
    }

    @PostMapping("/examination")
    public String save(@Valid @ModelAttribute("examination")Examination examination, BindingResult result, Model model, RedirectAttributes redirectAttributes){
        if(result.hasErrors()){
            model.addAttribute("error", result.getFieldError());
            return "/admin/add-examination";
        }
        try{
            examination = examinationService.save(examination);
            System.out.println("examination: "+examination);
            redirectAttributes.addFlashAttribute("success","Examination: "+examination.getExaminationName()+" saved successfully.");
        }catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for "+examination.getExaminationName());
            de.printStackTrace();
            return "/admin/add-examination";
        }catch(UniqueConstraintsException de){
            model.addAttribute("error", "Duplicate entry for "+examination.getExaminationName()+". "+de.getLocalizedMessage());
            de.printStackTrace();
            return "/admin/add-examination";
        }catch(Exception e){
            model.addAttribute("error", "Error in saving: "+e.getLocalizedMessage());
            System.out.println("ERRORRRR");
            e.printStackTrace();
            return "/admin/add-examination";
        }
        return "redirect:/admin/examinations";
    }

    @GetMapping("/examinations-date")
    public String getExaminationsDate(Model model){
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<ExamDetails> examinationList = examinationService.getAllExaminationDates(academicYear.getId(), school.getId());
        model.addAttribute("examinations", examinationList);
        model.addAttribute("isExamination", !examinationList.isEmpty());
        return "/admin/examination_date";
    }

    @GetMapping("/examination-details/add")
    public String getAddExaminationDateForm(Model model){
        model.addAttribute("examDetails", new ExamDetails());
        model.addAttribute("examinations", examinationService.getAllExamination());
        return "/admin/add-examination-details";
    }

    @PostMapping("/examination-details")
    public String save(@Valid @ModelAttribute("examDetails")ExamDetails examDetails, BindingResult result, Model model, RedirectAttributes redirectAttributes){
        model.addAttribute("examinations", examinationService.getAllExamination());
        if(result.hasErrors()){
            model.addAttribute("error", result.getFieldError());
            //model.addAttribute("examinations", examinationService.getAllExamination());
            return "/admin/add-examination-details";
        }
        try{
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            examDetails.setAcademicYear(academicYear);
            examDetails.setSchool(school);
            examDetails = examinationService.saveExamDetails(examDetails);
            System.out.println("examination: "+examDetails);
            SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy");
            redirectAttributes.addFlashAttribute("success","Examination: "+examDetails.getExamination().getExaminationName()+" scheduled on: "+sf.format(examDetails.getExamDeclaredDate())+" successfully.");
        }catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for "+examDetails.getExamination().getExaminationName());
            de.printStackTrace();
            return "/admin/add-examination-details";
        }catch(UniqueConstraintsException de){
            model.addAttribute("error", "Duplicate entry for "+examDetails.getExamination().getExaminationName()+". "+de.getLocalizedMessage());
            de.printStackTrace();
            return "/admin/add-examination-details";
        }catch(Exception e){
            model.addAttribute("error", "Error in saving: "+e.getLocalizedMessage());
            System.out.println("ERRORRRR");
            e.printStackTrace();
            return "/admin/add-examination-details";
        }
        return "redirect:/admin/examinations-date";
    }

}

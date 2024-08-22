package com.smsweb.sms.controllers.student;

import com.smsweb.sms.models.admin.DiscountClassMap;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.SiblingGroup;
import com.smsweb.sms.models.student.SiblingGroupStudent;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.services.admin.DiscountclassmapService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.SiblingDiscountService;
import com.smsweb.sms.services.student.SiblingGroupService;
import com.smsweb.sms.services.student.StudentDiscountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/sibling")
public class SiblingDiscountController {

    private SiblingDiscountService siblingDiscountService;
    private DiscountclassmapService discountclassmapService;
    private SiblingGroupService siblingGroupService;
    private AcademicStudentService academicStudentService;
    private StudentDiscountService studentDiscountService;

    @Autowired
    public SiblingDiscountController(SiblingDiscountService siblingDiscountService, DiscountclassmapService discountclassmapService, SiblingGroupService siblingGroupService, AcademicStudentService academicStudentService,
                                     StudentDiscountService studentDiscountService){
        this.siblingDiscountService = siblingDiscountService;
        this.discountclassmapService = discountclassmapService;
        this.siblingGroupService = siblingGroupService;
        this.academicStudentService = academicStudentService;
        this.studentDiscountService = studentDiscountService;
    }

    @GetMapping("/assign-sibling-discount")
    public String getSiblingDiscount(Model model){
        List<SiblingGroup> siblingGroupList = siblingGroupService.getAllSiblingGroups(4L, 14L);
        model.addAttribute("siblingGroups", siblingGroupList);
        model.addAttribute("hasSiblingGroup", !siblingGroupList.isEmpty());
        return "/student/siblingdiscountassign";
    }

    @ResponseBody
    @GetMapping("/groups/by-group/{groupId}")
    public List<SiblingGroupStudent> getStudentsByGroup(@PathVariable Long groupId) {
        System.out.println("INside controller "+groupId);
        SiblingGroup group = siblingGroupService.getSiblingGroupDetail(groupId).orElse(null);
        List<SiblingGroupStudent> students = group.getSiblingGroupStudents();
        System.out.println("students: "+students);
        return students;
    }

    /*@GetMapping("/validate-student/{academic_student_id}")
    public Map<String, String> getStudentDetail(@PathVariable Long academic_student_id){
        String msg = "";
        //Check Student is Valid or not - Should be active/Should be belonged to logged in school?
        Map<String, String> responseMap = new HashMap<>();
        AcademicStudent student = academicStudentService.getAcademicStudent(academic_student_id).orElse(null);
        if(student!=null){
            if(student.getSchool().getId()!=4L){
                msg = "Student: " + student.getStudent().getStudentName() + " not belongs to this school.";
                responseMap.put("error", msg);
            }
            //Check - Any other discount not associated to the selected student
            StudentDiscount studentDiscount = studentDiscountService.getStudentDiscountForStudent(4L, 14L, academic_student_id).orElse(null);
            if(studentDiscount!=null){
                msg = "Student: " + student.getStudent().getStudentName() + " already attached with Discount: " + studentDiscount.getDiscounthead().getDiscountName();
                responseMap.put("error", msg);
            } else{
                //Check - Sibling discount mapped to selected student class
                DiscountClassMap discountClassMap = discountclassmapService.getDiscountClassMapByDiscountName("Sibling Discount", 14L, 4L, student.getGrade().getId()).orElse(null);
                if(discountClassMap==null){
                    msg = "Sibling Discount not mapped to Grade: " + student.getGrade().getGradeName() + ". Please make mapping first.";
                    responseMap.put("error", msg);
                }
            }
        } else{
            msg = "Student not found";
            responseMap.put("error", msg);
        }

        return responseMap;
    }*/
    @GetMapping("/validate-student/{academicStudentId}")
    @ResponseBody
    public Map<String, String> getStudentDetail(@PathVariable Long academicStudentId) {
        Map<String, String> responseMap = new HashMap<>();

        // Validate student existence and belonging to the correct school
        Optional<AcademicStudent> optionalStudent = academicStudentService.getAcademicStudent(academicStudentId);
        if (optionalStudent.isEmpty()) {
            responseMap.put("error", "Student not found.");
            return responseMap;
        }

        AcademicStudent student = optionalStudent.get();
        Long loggedInSchoolId = 4L; // This should be dynamically fetched based on the logged-in user
        if (!student.getSchool().getId().equals(loggedInSchoolId)) {
            responseMap.put("error", "Student: " + student.getStudent().getStudentName() + " does not belong to this school.");
            return responseMap;
        }

        // Validate if any other discount is already associated with the student
        Long discountYearId = 14L; // This should also be dynamically fetched
        Optional<StudentDiscount> optionalStudentDiscount = studentDiscountService.getStudentDiscountForStudent(loggedInSchoolId, discountYearId, academicStudentId);
        if (optionalStudentDiscount.isPresent()) {
            StudentDiscount studentDiscount = optionalStudentDiscount.get();
            responseMap.put("error", "Student: " + student.getStudent().getStudentName() + " is already attached to Discount: " + studentDiscount.getDiscounthead().getDiscountName());
            return responseMap;
        }

        // Validate if the Sibling Discount is mapped to the student's grade
        Optional<DiscountClassMap> optionalDiscountClassMap = discountclassmapService.getDiscountClassMapByDiscountName("Sibling Discount", discountYearId, loggedInSchoolId, student.getGrade().getId());
        if (optionalDiscountClassMap.isEmpty()) {
            responseMap.put("error", "Sibling Discount is not mapped to Grade: " + student.getGrade().getGradeName() + ". Please configure the mapping first.");
            return responseMap;
        }

        //If any student of group saved already
        Optional<StudentDiscount> studentDiscount = studentDiscountService.getStudentDiscountForStudent(loggedInSchoolId, discountYearId, student.getId());
        if(optionalDiscountClassMap.isPresent()){

        }
        responseMap.put("success","proceed");
        return responseMap;
    }

    @PostMapping("/savesiblinggroupdiscount")
    public String saveStudentSiblingDiscount(HttpServletRequest request, RedirectAttributes redirectAttributes){
        try{
            Map paramMap = request.getParameterMap();
            System.out.println("==== "+paramMap.keySet());
            Map responseMap = siblingDiscountService.save(paramMap);
            if(responseMap.containsKey("error")){
                redirectAttributes.addFlashAttribute("error", responseMap.get("error"));
                return "redirect:/sibling/assign-sibling-discount";
            }
            if(responseMap.containsKey("NO_DISCOUNT_FOUND")){
                redirectAttributes.addFlashAttribute("error", responseMap.get("NO_DISCOUNT_FOUND"));
                return "redirect:/sibling/assign-sibling-discount";
            }
            if(responseMap.containsKey("NO_STU_FOUND")){
                redirectAttributes.addFlashAttribute("error", responseMap.get("NO_STU_FOUND"));
                return "redirect:/sibling/assign-sibling-discount";
            }
            if(responseMap.containsKey("NO_DISCOUNT_FOUND")){
                redirectAttributes.addFlashAttribute("error", responseMap.get("NO_DISCOUNT_FOUND"));
                return "redirect:/sibling/assign-sibling-discount";
            }
            if(responseMap.containsKey("NO_STU_OR_GRP_FOUND")){
                redirectAttributes.addFlashAttribute("error", responseMap.get("NO_STU_OR_GRP_FOUND"));
                return "redirect:/sibling/assign-sibling-discount";
            }
            redirectAttributes.addFlashAttribute("success", responseMap.get("DISCOUNT_SAVED"));
        }catch(Exception e){
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error: "+e.getLocalizedMessage());
        }
        return "redirect:/sibling/assign-sibling-discount";
    }


}

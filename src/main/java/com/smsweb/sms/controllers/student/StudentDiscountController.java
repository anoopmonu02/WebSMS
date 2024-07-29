package com.smsweb.sms.controllers.student;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.MonthmappingService;
import com.smsweb.sms.services.admin.SchoolService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentDiscountService;
import com.smsweb.sms.services.universal.DiscountService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentDiscountController {

    private final StudentDiscountService studentDiscountService;
    private final DiscountService discountService;
    private final AcademicyearService academicyearService;
    private final SchoolService schoolService;
    private final AcademicStudentService academicStudentService;

    @Autowired
    public StudentDiscountController(StudentDiscountService studentDiscountService, DiscountService discountService, AcademicyearService academicyearService, SchoolService schoolService,
                                     AcademicStudentService academicStudentService){
        this.studentDiscountService = studentDiscountService;
        this.discountService = discountService;
        this.academicyearService = academicyearService;
        this.schoolService = schoolService;
        this.academicStudentService = academicStudentService;
    }

    @GetMapping("/stu-discount-list")
    public String discountpage(Model model){
        List<StudentDiscount> studentDiscountList = studentDiscountService.getAllStudentDiscounts(4L, 14L);
        model.addAttribute("studentDiscounts", studentDiscountList);
        model.addAttribute("hasDiscounts", !studentDiscountList.isEmpty());
        return "/student/assigneddiscount";
    }

    @GetMapping("/assign-discount/add")
    public String addDiscountPage(Model model){
        List<Discounthead> discountheads = discountService.getAllDiscountheads();
        model.addAttribute("discounts",discountheads);
        model.addAttribute("studentDiscount",new StudentDiscount());
        return "/student/discountassign";
    }

    @PostMapping("/assign-discount")
    public String saveStudentDiscount(@Valid @ModelAttribute("studentDiscount")StudentDiscount studentDiscount, BindingResult result, Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            List<Discounthead> discountheads = discountService.getAllDiscountheads();
            model.addAttribute("discounts",discountheads);
            return "/student/discountassign";
        }
        try{
            System.out.println("studentDiscount: "+studentDiscount);
            School school = schoolService.getSchoolById(4L).get();
            AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
            AcademicStudent student = academicStudentService.searchStudentById(studentDiscount.getAcademicStudent().getId(), 14L, 4L);
            studentDiscount.setAcademicYear(academicYear);
            studentDiscount.setSchool(school);
            studentDiscount.setAcademicStudent(student);
            System.out.println("studentDiscount 1: "+studentDiscount);
            String returnMsg = "Discount assigned successfully for: "+studentDiscount.getAcademicStudent().getStudent().getStudentName();
            if(studentDiscount.getId()!=null){
                returnMsg = "Discount assigning updated successfully for: "+studentDiscount.getAcademicStudent().getStudent().getStudentName();
            }
            studentDiscountService.save(studentDiscount);
            ra.addFlashAttribute("success", returnMsg);
        }catch(UniqueConstraintsException de){
            model.addAttribute("error", de.getLocalizedMessage());
            List<Discounthead> discountheads = discountService.getAllDiscountheads();
            model.addAttribute("discounts",discountheads);
            return "/student/discountassign";
        } catch(ObjectNotSaveException oe){
            model.addAttribute("error", oe.getLocalizedMessage());
            List<Discounthead> discountheads = discountService.getAllDiscountheads();
            model.addAttribute("discounts",discountheads);
            return "/student/discountassign";
        } catch(Exception e){
            model.addAttribute("error", e.getLocalizedMessage());
            List<Discounthead> discountheads = discountService.getAllDiscountheads();
            model.addAttribute("discounts",discountheads);
            return "/student/discountassign";
        }
        return "redirect:/student/stu-discount-list";
    }
}

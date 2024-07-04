package com.smsweb.sms.controllers;

import com.smsweb.sms.models.universal.*;
import com.smsweb.sms.services.universal.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/universal")
public class UniversalController {

    private final MediumService mediumService;
    private GradeService gradeService;
    private SectionService sectionService;
    private final BankService bankService;
    private final CategoryService categoryService;
    private final CastService castService;
    private final FeeheadService feeheadService;
    private final DiscountService discountService;


    @Autowired
    public UniversalController(MediumService mediumService, GradeService gradeService, SectionService sectionService, BankService bankService,
                               CategoryService categoryService, CastService castService, FeeheadService feeheadService, DiscountService discountService) {
        this.mediumService = mediumService;
        this.gradeService = gradeService;
        this.sectionService = sectionService;
        this.bankService = bankService;
        this.categoryService = categoryService;
        this.castService = castService;
        this.feeheadService = feeheadService;
        this.discountService = discountService;
    }

    @GetMapping("/medium")
    public String medium(Model model){
        List<Medium> mediums = mediumService.getAllMediums();
        model.addAttribute("mediums", mediums);
        model.addAttribute("hasMediums", !mediums.isEmpty());
        return "universal/medium";
    }

    //We can a separate for add
    @GetMapping("/medium/add")
    public String addMediumForm(Model model) {
        model.addAttribute("medium", new Medium());
        return "universal/add-medium";
    }

    @PostMapping("/medium")
    public String saveMedium(@Valid @ModelAttribute("medium") Medium medium, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "universal/add-medium";
        }
        System.out.println("medium "+medium);
        System.out.println("result "+result);
        System.out.println("model "+model);
        mediumService.saveMedium(medium);
        return "redirect:/universal/medium";
    }

    @GetMapping("/medium/edit/{id}")
    public String editMediumForm(@PathVariable("id") Long id, Model model) {
        Medium medium = mediumService.getMediumById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid medium Id:" + id));
        model.addAttribute("medium", medium);
        return "universal/edit-medium";
    }

    @PostMapping("/medium/{id}")
    public String updateMedium(@PathVariable("id") Long id, @Valid @ModelAttribute("medium") Medium medium, BindingResult result, Model model) {
        if (result.hasErrors()) {
            medium.setId(id);
            return "universal/edit-medium";
        }
        mediumService.saveMedium(medium);
        return "redirect:/universal/medium";
    }

    /*****************   Grade Starts Here  *****************/
    @GetMapping("/grade")
    public String grade(Model model){
        List<Grade> grades = gradeService.getAllGrades();
        model.addAttribute("grades", grades);
        model.addAttribute("hasGrades", !grades.isEmpty());
        return "universal/grade";
    }

    @GetMapping("/grade/add")
    public String addGradeForm(Model model) {
        model.addAttribute("grade", new Grade());
        return "universal/add-grade";
    }

    @PostMapping("/grade")
    public String saveGrade(@Valid @ModelAttribute("grade") Grade grade, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "universal/add-grade";
        }
        gradeService.saveGrade(grade);
        return "redirect:/universal/grade";
    }

    @GetMapping("/grade/edit/{id}")
    public String editGradeForm(@PathVariable("id") Long id, Model model) {
        Grade grade = gradeService.getGradeById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid grade Id:" + id));
        model.addAttribute("grade", grade);
        return "universal/edit-grade";
    }

    @PostMapping("/grade/{id}")
    public String updateGrade(@PathVariable("id") Long id, @Valid @ModelAttribute("grade") Grade grade, BindingResult result, Model model) {
        if (result.hasErrors()) {
            grade.setId(id);
            return "universal/edit-grade";
        }
        gradeService.saveGrade(grade);
        return "redirect:/universal/grade";
    }

    /*****************   Grade Ends Here  *****************/

    /*****************   Section Starts Here  *****************/
    @GetMapping("/section")
    public String section(Model model){
        List<Section> sections = sectionService.getAllSections();
        model.addAttribute("sections", sections);
        model.addAttribute("hasSections", !sections.isEmpty());
        return "universal/section";
    }

    @GetMapping("/section/add")
    public String addSectionForm(Model model) {
        model.addAttribute("section", new Section());
        return "universal/add-section";
    }

    @PostMapping("/section")
    public String saveSection(@Valid @ModelAttribute("section") Section section, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "universal/add-section";
        }
        sectionService.saveSection(section);
        return "redirect:/universal/section";
    }

    @GetMapping("/section/edit/{id}")
    public String editSectionForm(@PathVariable("id") Long id, Model model) {
        Section section = sectionService.getSectionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid section Id:" + id));
        model.addAttribute("section", section);
        return "universal/edit-section";
    }

    @PostMapping("/section/{id}")
    public String updateSection(@PathVariable("id") Long id, @Valid @ModelAttribute("section") Section section, BindingResult result, Model model) {
        if (result.hasErrors()) {
            section.setId(id);
            return "universal/edit-section";
        }
        sectionService.saveSection(section);
        return "redirect:/universal/section";
    }

    /*****************   Section Ends Here  *****************/

    /*****************   category Starts Here  *****************/
    @GetMapping("/category")
    public String category(Model model){
        List<Category> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);
        model.addAttribute("hasCategories", !categories.isEmpty());
        return "universal/category";
    }

    @GetMapping("/category/add")
    public String addCategoryForm(Model model) {
        model.addAttribute("category", new Category());
        return "universal/add-category";
    }

    @PostMapping("/category")
    public String saveCategory(@Valid @ModelAttribute("category") Category category, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "universal/add-category";
        }
        categoryService.saveCategory(category);
        return "redirect:/universal/category";
    }

    @GetMapping("/category/edit/{id}")
    public String editCategoryForm(@PathVariable("id") Long id, Model model) {
        Category category = categoryService.getCategoryById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid category Id:" + id));
        model.addAttribute("category", category);
        return "universal/edit-category";
    }

    @PostMapping("/category/{id}")
    public String updateCategory(@PathVariable("id") Long id, @Valid @ModelAttribute("category") Category category, BindingResult result, Model model) {
        if (result.hasErrors()) {
            category.setId(id);
            return "universal/edit-category";
        }
        categoryService.saveCategory(category);
        return "redirect:/universal/category";
    }

    /*****************   category Ends Here  *****************/

    /*****************   Bank Starts Here  *****************/
    @GetMapping("/bank")
    public String bank(Model model){
        List<Bank> banks = bankService.getAllBanks();
        model.addAttribute("banks", banks);
        model.addAttribute("hasBanks", !banks.isEmpty());
        return "universal/bank";
    }

    @GetMapping("/bank/add")
    public String addBankForm(Model model) {
        model.addAttribute("bank", new Bank());
        return "universal/add-bank";
    }

    @PostMapping("/bank")
    public String saveBank(@Valid @ModelAttribute("bank") Bank bank, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "universal/add-bank";
        }
        bankService.saveBank(bank);
        return "redirect:/universal/bank";
    }

    @GetMapping("/bank/edit/{id}")
    public String editBankForm(@PathVariable("id") Long id, Model model) {
        Bank bank = bankService.getBankById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid bank Id:" + id));
        model.addAttribute("bank", bank);
        return "universal/edit-bank";
    }

    @PostMapping("/bank/{id}")
    public String updateBank(@PathVariable("id") Long id, @Valid @ModelAttribute("bank") Bank bank, BindingResult result, Model model) {
        if (result.hasErrors()) {
            bank.setId(id);
            return "universal/edit-bank";
        }
        bankService.saveBank(bank);
        return "redirect:/universal/bank";
    }

    /*****************   Bank Ends Here  *****************/

    /*****************   Cast Starts Here  *****************/
    @GetMapping("/cast")
    public String cast(Model model){
        List<Cast> casts = castService.getAllCasts();
        model.addAttribute("casts", casts);
        model.addAttribute("hasCasts", !casts.isEmpty());
        return "universal/cast";
    }

    @GetMapping("/cast/add")
    public String addCastForm(Model model) {
        model.addAttribute("cast", new Cast());
        return "universal/add-cast";
    }

    @PostMapping("/cast")
    public String saveCast(@Valid @ModelAttribute("cast") Cast cast, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "universal/add-cast";
        }
        castService.saveCast(cast);
        return "redirect:/universal/cast";
    }

    @GetMapping("/cast/edit/{id}")
    public String editCastForm(@PathVariable("id") Long id, Model model) {
        Cast cast = castService.getCastById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid cast Id:" + id));
        model.addAttribute("cast", cast);
        return "universal/edit-cast";
    }

    @PostMapping("/cast/{id}")
    public String updateCast(@PathVariable("id") Long id, @Valid @ModelAttribute("cast") Cast cast, BindingResult result, Model model) {
        if (result.hasErrors()) {
            cast.setId(id);
            return "universal/edit-cast";
        }
        castService.saveCast(cast);
        return "redirect:/universal/cast";
    }

    /*****************   Cast Ends Here  *****************/

    /*****************   Fee Head Starts Here  *****************/
    @GetMapping("/feehead")
    public String feehead(Model model){
        List<Feehead> feeheads = feeheadService.getAllFeeheads();
        model.addAttribute("feeheads", feeheads);
        model.addAttribute("hasFeeheads", !feeheads.isEmpty());
        return "universal/feehead";
    }

    @GetMapping("/feehead/add")
    public String addFeeheadForm(Model model) {
        model.addAttribute("feehead", new Feehead());
        return "universal/add-feehead";
    }

    @PostMapping("/feehead")
    public String saveFeehead(@Valid @ModelAttribute("feehead") Feehead feehead, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "universal/add-feehead";
        }
        feeheadService.saveFeehead(feehead);
        return "redirect:/universal/feehead";
    }

    @GetMapping("/feehead/edit/{id}")
    public String editFeeheadForm(@PathVariable("id") Long id, Model model) {
        Feehead feehead = feeheadService.getFeeheadById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid fee head Id:" + id));
        model.addAttribute("feehead", feehead);
        return "universal/edit-feehead";
    }

    @PostMapping("/feehead/{id}")
    public String updateFeehead(@PathVariable("id") Long id, @Valid @ModelAttribute("feehead") Feehead feehead, BindingResult result, Model model) {
        if (result.hasErrors()) {
            feehead.setId(id);
            return "universal/edit-feehead";
        }
        feeheadService.saveFeehead(feehead);
        return "redirect:/universal/feehead";
    }

    /*****************   Fee Head Ends Here  *****************/

    /*****************   Discount Head Starts Here  *****************/
    @GetMapping("/discounthead")
    public String discounthead(Model model){
        List<Discounthead> discountheads = discountService.getAllDiscountheads();
        model.addAttribute("discountheads", discountheads);
        model.addAttribute("hasDiscountheads", !discountheads.isEmpty());
        return "universal/discounthead";
    }

    @GetMapping("/discounthead/add")
    public String addDiscountheadForm(Model model) {
        model.addAttribute("discounthead", new Discounthead());
        return "universal/add-discounthead";
    }

    @PostMapping("/discounthead")
    public String saveDiscounthead(@Valid @ModelAttribute("discounthead") Discounthead discounthead, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "universal/add-discounthead";
        }
        discountService.saveDiscounthead(discounthead);
        return "redirect:/universal/discounthead";
    }

    @GetMapping("/discounthead/edit/{id}")
    public String editDiscountheadForm(@PathVariable("id") Long id, Model model) {
        Discounthead discounthead = discountService.getDiscountheadById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid discount head Id:" + id));
        model.addAttribute("discounthead", discounthead);
        return "universal/edit-discounthead";
    }

    @PostMapping("/discounthead/{id}")
    public String updateDiscounthead(@PathVariable("id") Long id, @Valid @ModelAttribute("discounthead") Discounthead discounthead, BindingResult result, Model model) {
        if (result.hasErrors()) {
            discounthead.setId(id);
            return "universal/edit-discounthead";
        }
        discountService.saveDiscounthead(discounthead);
        return "redirect:/universal/discounthead";
    }

    /*****************   Discount Head Ends Here  *****************/
}

package com.smsweb.sms.controllers;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.models.universal.*;
import com.smsweb.sms.services.universal.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/universal")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class UniversalController {

    private final MediumService mediumService;
    private GradeService gradeService;
    private SectionService sectionService;
    private final BankService bankService;
    private final CategoryService categoryService;
    private final CastService castService;
    private final FeeheadService feeheadService;
    private final DiscountService discountService;
    private final FineheadService fineheadService;


    @Autowired
    public UniversalController(MediumService mediumService, GradeService gradeService, SectionService sectionService, BankService bankService,
                               CategoryService categoryService, CastService castService, FeeheadService feeheadService, DiscountService discountService,
                               FineheadService fineheadService) {
        this.mediumService = mediumService;
        this.gradeService = gradeService;
        this.sectionService = sectionService;
        this.bankService = bankService;
        this.categoryService = categoryService;
        this.castService = castService;
        this.feeheadService = feeheadService;
        this.discountService = discountService;
        this.fineheadService = fineheadService;
    }

    @CheckAccess(screen = "GLOBAL_MEDIUM", type = AccessType.VIEW)
    @GetMapping("/medium")
    public String medium(Model model){
        List<Medium> mediums = mediumService.getAllMediums();
        model.addAttribute("mediums", mediums);
        model.addAttribute("hasMediums", !mediums.isEmpty());
        return "universal/medium";
    }

    //We can a separate for add
    @CheckAccess(screen = "GLOBAL_MEDIUM", type = AccessType.CREATE)
    @GetMapping("/medium/add")
    public String addMediumForm(Model model) {
        model.addAttribute("medium", new Medium());
        return "universal/add-medium";
    }

    @CheckAccess(screen = "GLOBAL_MEDIUM", type = AccessType.CREATE)
    @PostMapping("/medium")
    public String saveMedium(@Valid @ModelAttribute("medium") Medium medium, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "universal/add-medium";
        }
        try{
            mediumService.saveMedium(medium);
            ra.addFlashAttribute("success","Medium: "+medium.getMediumName()+" saved successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+medium.getMediumName()+". "+de.getMessage());
            return "universal/add-medium";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/add-medium";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/add-medium";
        }
        return "redirect:/universal/medium";
    }

    @CheckAccess(screen = "GLOBAL_MEDIUM", type = AccessType.EDIT)
    @GetMapping("/medium/edit/{id}")
    public String editMediumForm(@PathVariable("id") Long id, Model model) {
        Medium medium = mediumService.getMediumById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid medium Id:" + id));
        model.addAttribute("medium", medium);
        return "universal/edit-medium";
    }

    @CheckAccess(screen = "GLOBAL_MEDIUM", type = AccessType.EDIT)
    @PostMapping("/medium/{id}")
    public String updateMedium(@PathVariable("id") Long id, @Valid @ModelAttribute("medium") Medium medium, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            medium.setId(id);
            return "universal/edit-medium";
        }
        try{
            mediumService.saveMedium(medium);
            ra.addFlashAttribute("success","Medium: "+medium.getMediumName()+" updated successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+medium.getMediumName()+". "+de.getMessage());
            return "universal/edit-medium";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/edit-medium";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/edit-medium";
        }
        return "redirect:/universal/medium";
    }

    @CheckAccess(screen = "GLOBAL_MEDIUM", type = AccessType.DELETE)
    @PostMapping("/medium/delete/{id}")
    public String deleteMedium(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            mediumService.deleteMedium(id);
            redirectAttributes.addFlashAttribute("success", "Medium deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete medium: " + e.getMessage());
        }
        return "redirect:/universal/medium";
    }

    /*****************   Grade Starts Here  *****************/
    @CheckAccess(screen = "GLOBAL_GRADE", type = AccessType.VIEW)
    @GetMapping("/grade")
    public String grade(Model model){
        List<Grade> grades = gradeService.getAllGrades();
        model.addAttribute("grades", grades);
        model.addAttribute("hasGrades", !grades.isEmpty());
        return "universal/grade";
    }

    @CheckAccess(screen = "GLOBAL_GRADE", type = AccessType.CREATE)
    @GetMapping("/grade/add")
    public String addGradeForm(Model model) {
        model.addAttribute("grade", new Grade());
        return "universal/add-grade";
    }

    @CheckAccess(screen = "GLOBAL_GRADE", type = AccessType.CREATE)
    @PostMapping("/grade")
    public String saveGrade(@Valid @ModelAttribute("grade") Grade grade, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "universal/add-grade";
        }
        try{
            gradeService.saveGrade(grade);
            ra.addFlashAttribute("success","Grade: "+grade.getGradeName()+" saved successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+grade.getGradeName()+". "+de.getMessage());
            return "universal/add-grade";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/add-grade";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/add-grade";
        }
        return "redirect:/universal/grade";
    }

    @CheckAccess(screen = "GLOBAL_GRADE", type = AccessType.EDIT)
    @GetMapping("/grade/edit/{id}")
    public String editGradeForm(@PathVariable("id") Long id, Model model) {
        Grade grade = gradeService.getGradeById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid grade Id:" + id));
        model.addAttribute("grade", grade);
        return "universal/edit-grade";
    }

    @CheckAccess(screen = "GLOBAL_GRADE", type = AccessType.EDIT)
    @PostMapping("/grade/{id}")
    public String updateGrade(@PathVariable("id") Long id, @Valid @ModelAttribute("grade") Grade grade, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            grade.setId(id);
            return "universal/edit-grade";
        }
        try{
            gradeService.saveGrade(grade);
            ra.addFlashAttribute("success","Grade: "+grade.getGradeName()+" updated successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+grade.getGradeName()+". "+de.getMessage());
            return "universal/edit-grade";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/edit-grade";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/edit-grade";
        }
        return "redirect:/universal/grade";
    }

    @CheckAccess(screen = "GLOBAL_GRADE", type = AccessType.DELETE)
    @DeleteMapping("/grade/delete/{id}")
    public String deleteObject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            gradeService.deleteGrade(id);
            redirectAttributes.addFlashAttribute("success", "Object deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete object: " + e.getMessage());
        }
        return "redirect:/universal/grade";
    }
    @CheckAccess(screen = "GLOBAL_GRADE", type = AccessType.DELETE)
    @PostMapping("/grade/delete/{id}")
    public String deleteGrade(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            gradeService.deleteGrade(id);
            redirectAttributes.addFlashAttribute("success", "Grade deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete grade: " + e.getMessage());
        }
        return "redirect:/universal/grade";
    }

    /*****************   Grade Ends Here  *****************/

    /*****************   Section Starts Here  *****************/
    @CheckAccess(screen = "GLOBAL_SECTION", type = AccessType.VIEW)
    @GetMapping("/section")
    public String section(Model model){
        List<Section> sections = sectionService.getAllSections();
        model.addAttribute("sections", sections);
        model.addAttribute("hasSections", !sections.isEmpty());
        return "universal/section";
    }

    @CheckAccess(screen = "GLOBAL_SECTION", type = AccessType.CREATE)
    @GetMapping("/section/add")
    public String addSectionForm(Model model) {
        model.addAttribute("section", new Section());
        return "universal/add-section";
    }

    @CheckAccess(screen = "GLOBAL_SECTION", type = AccessType.CREATE)
    @PostMapping("/section")
    public String saveSection(@Valid @ModelAttribute("section") Section section, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "universal/add-section";
        }
        try{
            sectionService.saveSection(section);
            ra.addFlashAttribute("success","Section: "+section.getSectionName()+" saved successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+section.getSectionName()+". "+de.getMessage());
            return "universal/add-section";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/add-section";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/add-section";
        }
        return "redirect:/universal/section";
    }

    @CheckAccess(screen = "GLOBAL_SECTION", type = AccessType.EDIT)
    @GetMapping("/section/edit/{id}")
    public String editSectionForm(@PathVariable("id") Long id, Model model) {
        Section section = sectionService.getSectionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid section Id:" + id));
        model.addAttribute("section", section);
        return "universal/edit-section";
    }

    @CheckAccess(screen = "GLOBAL_SECTION", type = AccessType.EDIT)
    @PostMapping("/section/{id}")
    public String updateSection(@PathVariable("id") Long id, @Valid @ModelAttribute("section") Section section, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            section.setId(id);
            return "universal/edit-section";
        }
        try{
            sectionService.saveSection(section);
            ra.addFlashAttribute("success","Section: "+section.getSectionName()+" updated successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+section.getSectionName()+". "+de.getMessage());
            return "universal/edit-section";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/edit-section";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/edit-section";
        }
        return "redirect:/universal/section";
    }
    @CheckAccess(screen = "GLOBAL_SECTION", type = AccessType.DELETE)
    @PostMapping("/section/delete/{id}")
    public String deleteSection(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            sectionService.deleteSection(id);
            redirectAttributes.addFlashAttribute("success", "Section deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete section: " + e.getMessage());
        }
        return "redirect:/universal/section";
    }

    /*****************   Section Ends Here  *****************/

    /*****************   category Starts Here  *****************/
    @CheckAccess(screen = "GLOBAL_CATEGORY", type = AccessType.VIEW)
    @GetMapping("/category")
    public String category(Model model){
        List<Category> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);
        model.addAttribute("hasCategories", !categories.isEmpty());
        return "universal/category";
    }

    @CheckAccess(screen = "GLOBAL_CATEGORY", type = AccessType.CREATE)
    @GetMapping("/category/add")
    public String addCategoryForm(Model model) {
        model.addAttribute("category", new Category());
        return "universal/add-category";
    }

    @CheckAccess(screen = "GLOBAL_CATEGORY", type = AccessType.CREATE)
    @PostMapping("/category")
    public String saveCategory(@Valid @ModelAttribute("category") Category category, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "universal/add-category";
        }
        try{
            categoryService.saveCategory(category);
            ra.addFlashAttribute("success","Category: "+category.getCategoryName()+" saved successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+category.getCategoryName()+". "+de.getMessage());
            return "universal/add-category";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/add-category";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/add-category";
        }
        return "redirect:/universal/category";
    }

    @CheckAccess(screen = "GLOBAL_CATEGORY", type = AccessType.EDIT)
    @GetMapping("/category/edit/{id}")
    public String editCategoryForm(@PathVariable("id") Long id, Model model) {
        Category category = categoryService.getCategoryById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid category Id:" + id));
        model.addAttribute("category", category);
        return "universal/edit-category";
    }

    @CheckAccess(screen = "GLOBAL_CATEGORY", type = AccessType.EDIT)
    @PostMapping("/category/{id}")
    public String updateCategory(@PathVariable("id") Long id, @Valid @ModelAttribute("category") Category category, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            category.setId(id);
            return "universal/edit-category";
        }
        try{
            categoryService.saveCategory(category);
            ra.addFlashAttribute("success","Category: "+category.getCategoryName()+" updated successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+category.getCategoryName()+". "+de.getMessage());
            return "universal/edit-category";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/edit-category";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/edit-category";
        }
        return "redirect:/universal/category";
    }
    @CheckAccess(screen = "GLOBAL_CATEGORY", type = AccessType.DELETE)
    @PostMapping("/category/delete/{id}")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("success", "Category deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete category: " + e.getMessage());
        }
        return "redirect:/universal/category";
    }

    /*****************   category Ends Here  *****************/

    /*****************   Bank Starts Here  *****************/
    @CheckAccess(screen = "GLOBAL_BANK", type = AccessType.VIEW)
    @GetMapping("/bank")
    public String bank(Model model){
        List<Bank> banks = bankService.getAllBanks();
        model.addAttribute("banks", banks);
        model.addAttribute("hasBanks", !banks.isEmpty());
        return "universal/bank";
    }

    @CheckAccess(screen = "GLOBAL_BANK", type = AccessType.CREATE)
    @GetMapping("/bank/add")
    public String addBankForm(Model model) {
        model.addAttribute("bank", new Bank());
        return "universal/add-bank";
    }

    @CheckAccess(screen = "GLOBAL_BANK", type = AccessType.CREATE)
    @PostMapping("/bank")
    public String saveBank(@Valid @ModelAttribute("bank") Bank bank, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "universal/add-bank";
        }
        try{
            bankService.saveBank(bank);
            ra.addFlashAttribute("success","Bank: "+bank.getBankName()+" saved successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+bank.getBankName()+". "+de.getMessage());
            return "universal/add-bank";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/add-bank";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/add-bank";
        }
        return "redirect:/universal/bank";
    }

    @CheckAccess(screen = "GLOBAL_BANK", type = AccessType.EDIT)
    @GetMapping("/bank/edit/{id}")
    public String editBankForm(@PathVariable("id") Long id, Model model) {
        Bank bank = bankService.getBankById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid bank Id:" + id));
        model.addAttribute("bank", bank);
        return "universal/edit-bank";
    }

    @CheckAccess(screen = "GLOBAL_BANK", type = AccessType.EDIT)
    @PostMapping("/bank/{id}")
    public String updateBank(@PathVariable("id") Long id, @Valid @ModelAttribute("bank") Bank bank, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            bank.setId(id);
            return "universal/edit-bank";
        }
        try{
            bankService.saveBank(bank);
            ra.addFlashAttribute("success","Bank: "+bank.getBankName()+" updated successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+bank.getBankName()+". "+de.getMessage());
            return "universal/edit-bank";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/edit-bank";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/edit-bank";
        }
        return "redirect:/universal/bank";
    }
    @CheckAccess(screen = "GLOBAL_BANK", type = AccessType.DELETE)
    @PostMapping("/bank/delete/{id}")
    public String deleteBank(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bankService.deleteBank(id);
            redirectAttributes.addFlashAttribute("success", "Bank deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete bank: " + e.getMessage());
        }
        return "redirect:/universal/bank";
    }

    /*****************   Bank Ends Here  *****************/

    /*****************   Cast Starts Here  *****************/
    @CheckAccess(screen = "GLOBAL_CAST", type = AccessType.VIEW)
    @GetMapping("/cast")
    public String cast(Model model){
        List<Cast> casts = castService.getAllCasts();
        model.addAttribute("casts", casts);
        model.addAttribute("hasCasts", !casts.isEmpty());
        return "universal/cast";
    }

    @CheckAccess(screen = "GLOBAL_CAST", type = AccessType.CREATE)
    @GetMapping("/cast/add")
    public String addCastForm(Model model) {
        model.addAttribute("cast", new Cast());
        return "universal/add-cast";
    }

    @CheckAccess(screen = "GLOBAL_CAST", type = AccessType.CREATE)
    @PostMapping("/cast")
    public String saveCast(@Valid @ModelAttribute("cast") Cast cast, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "universal/add-cast";
        }
        try{
            castService.saveCast(cast);
            ra.addFlashAttribute("success","Cast: "+cast.getCastName()+" saved successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+cast.getCastName()+". "+de.getMessage());
            return "universal/add-cast";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/add-cast";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/add-cast";
        }
        return "redirect:/universal/cast";
    }

    @CheckAccess(screen = "GLOBAL_CAST", type = AccessType.EDIT)
    @GetMapping("/cast/edit/{id}")
    public String editCastForm(@PathVariable("id") Long id, Model model) {
        Cast cast = castService.getCastById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid cast Id:" + id));
        model.addAttribute("cast", cast);
        return "universal/edit-cast";
    }

    @CheckAccess(screen = "GLOBAL_CAST", type = AccessType.EDIT)
    @PostMapping("/cast/{id}")
    public String updateCast(@PathVariable("id") Long id, @Valid @ModelAttribute("cast") Cast cast, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            cast.setId(id);
            return "universal/edit-cast";
        }
        try{
            castService.saveCast(cast);
            ra.addFlashAttribute("success","Cast: "+cast.getCastName()+" updated successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+cast.getCastName()+". "+de.getMessage());
            return "universal/edit-cast";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/edit-cast";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/edit-cast";
        }
        return "redirect:/universal/cast";
    }
    @CheckAccess(screen = "GLOBAL_CAST", type = AccessType.DELETE)
    @PostMapping("/cast/delete/{id}")
    public String deleteCast(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            castService.deleteCast(id);
            redirectAttributes.addFlashAttribute("success", "Cast deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete cast: " + e.getMessage());
        }
        return "redirect:/universal/cast";
    }

    /*****************   Cast Ends Here  *****************/

    /*****************   Fee Head Starts Here  *****************/
    @CheckAccess(screen = "GLOBAL_FEEHEAD", type = AccessType.VIEW)
    @GetMapping("/feehead")
    public String feehead(Model model){
        List<Feehead> feeheads = feeheadService.getAllFeeheads();
        model.addAttribute("feeheads", feeheads);
        model.addAttribute("hasFeeheads", !feeheads.isEmpty());
        return "universal/feehead";
    }

    @CheckAccess(screen = "GLOBAL_FEEHEAD", type = AccessType.CREATE)
    @GetMapping("/feehead/add")
    public String addFeeheadForm(Model model) {
        model.addAttribute("feehead", new Feehead());
        return "universal/add-feehead";
    }

    @CheckAccess(screen = "GLOBAL_FEEHEAD", type = AccessType.CREATE)
    @PostMapping("/feehead")
    public String saveFeehead(@Valid @ModelAttribute("feehead") Feehead feehead, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "universal/add-feehead";
        }
        try{
            feeheadService.saveFeehead(feehead);
            ra.addFlashAttribute("success","Fee-Head: "+feehead.getFeeHeadName()+" saved successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+feehead.getFeeHeadName()+". "+de.getMessage());
            return "universal/add-feehead";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/add-feehead";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/add-feehead";
        }
        return "redirect:/universal/feehead";
    }

    @CheckAccess(screen = "GLOBAL_FEEHEAD", type = AccessType.EDIT)
    @GetMapping("/feehead/edit/{id}")
    public String editFeeheadForm(@PathVariable("id") Long id, Model model) {
        Feehead feehead = feeheadService.getFeeheadById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid fee head Id:" + id));
        model.addAttribute("feehead", feehead);
        return "universal/edit-feehead";
    }

    @CheckAccess(screen = "GLOBAL_FEEHEAD", type = AccessType.EDIT)
    @PostMapping("/feehead/{id}")
    public String updateFeehead(@PathVariable("id") Long id, @Valid @ModelAttribute("feehead") Feehead feehead, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            feehead.setId(id);
            return "universal/edit-feehead";
        }
        try{
            feeheadService.saveFeehead(feehead);
            ra.addFlashAttribute("success","Fee-Head: "+feehead.getFeeHeadName()+" updated successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+feehead.getFeeHeadName()+". "+de.getMessage());
            return "universal/edit-feehead";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/edit-feehead";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/edit-feehead";
        }
        return "redirect:/universal/feehead";
    }
    @CheckAccess(screen = "GLOBAL_FEEHEAD", type = AccessType.DELETE)
    @PostMapping("/feehead/delete/{id}")
    public String deleteFeehead(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            feeheadService.deleteFeehead(id);
            redirectAttributes.addFlashAttribute("success", "Fee Head deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete fee head: " + e.getMessage());
        }
        return "redirect:/universal/feehead";
    }

    /*****************   Fee Head Ends Here  *****************/

    /*****************   Discount Head Starts Here  *****************/
    @CheckAccess(screen = "GLOBAL_DISCOUNTHEAD", type = AccessType.VIEW)
    @GetMapping("/discounthead")
    public String discounthead(Model model){
        List<Discounthead> discountheads = discountService.getAllDiscountheads();
        model.addAttribute("discountheads", discountheads);
        model.addAttribute("hasDiscountheads", !discountheads.isEmpty());
        return "universal/discounthead";
    }

    @CheckAccess(screen = "GLOBAL_DISCOUNTHEAD", type = AccessType.CREATE)
    @GetMapping("/discounthead/add")
    public String addDiscountheadForm(Model model) {
        model.addAttribute("discounthead", new Discounthead());
        return "universal/add-discounthead";
    }

    @CheckAccess(screen = "GLOBAL_DISCOUNTHEAD", type = AccessType.CREATE)
    @PostMapping("/discounthead")
    public String saveDiscounthead(@Valid @ModelAttribute("discounthead") Discounthead discounthead, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            System.out.println(">>>>>>>>>>>"+result);
            return "universal/add-discounthead";
        }
        try{
            discountService.saveDiscounthead(discounthead);
            ra.addFlashAttribute("success","Discount-Head: "+discounthead.getDiscountName()+" saved successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+discounthead.getDiscountName()+". "+de.getMessage());
            return "universal/add-discounthead";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/add-discounthead";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/add-discounthead";
        }
        return "redirect:/universal/discounthead";
    }

    @CheckAccess(screen = "GLOBAL_DISCOUNTHEAD", type = AccessType.EDIT)
    @GetMapping("/discounthead/edit/{id}")
    public String editDiscountheadForm(@PathVariable("id") Long id, Model model) {
        Discounthead discounthead = discountService.getDiscountheadById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid discount head Id:" + id));
        model.addAttribute("discounthead", discounthead);
        return "universal/edit-discounthead";
    }

    @CheckAccess(screen = "GLOBAL_DISCOUNTHEAD", type = AccessType.EDIT)
    @PostMapping("/discounthead/{id}")
    public String updateDiscounthead(@PathVariable("id") Long id, @Valid @ModelAttribute("discounthead") Discounthead discounthead, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            discounthead.setId(id);
            return "universal/edit-discounthead";
        }
        try{
            discountService.saveDiscounthead(discounthead);
            ra.addFlashAttribute("success","Discount-Head: "+discounthead.getDiscountName()+" updated successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+discounthead.getDiscountName()+". "+de.getMessage());
            return "universal/edit-discounthead";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/edit-discounthead";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/edit-discounthead";
        }
        return "redirect:/universal/discounthead";
    }
    @CheckAccess(screen = "GLOBAL_DISCOUNTHEAD", type = AccessType.DELETE)
    @PostMapping("/discounthead/delete/{id}")
    public String deleteDiscounthead(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            discountService.deleteDiscounthead(id);
            redirectAttributes.addFlashAttribute("success", "Discount Head deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete discount head: " + e.getMessage());
        }
        return "redirect:/universal/discounthead";
    }

    /*****************   Discount Head Ends Here  *****************/

    /*****************   Fine Head Starts Here  *****************/
    @CheckAccess(screen = "GLOBAL_FINEHEAD", type = AccessType.VIEW)
    @GetMapping("/finehead")
    public String finehead(Model model){
        List<Finehead> fineheads = fineheadService.getAllFineHeads();
        model.addAttribute("fineheads", fineheads);
        model.addAttribute("hasFineheads", !fineheads.isEmpty());
        return "universal/finehead";
    }

    @CheckAccess(screen = "GLOBAL_FINEHEAD", type = AccessType.CREATE)
    @GetMapping("/finehead/add")
    public String addFineheadForm(Model model) {
        model.addAttribute("finehead", new Finehead());
        return "universal/add-finehead";
    }

    @CheckAccess(screen = "GLOBAL_FINEHEAD", type = AccessType.CREATE)
    @PostMapping("/finehead")
    public String saveFinehead(@Valid @ModelAttribute("finehead") Finehead finehead, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            System.out.println(">>>>>>>>>>>"+result);
            return "universal/add-finehead";
        }
        try{
            fineheadService.saveFinehead(finehead);
            ra.addFlashAttribute("success","Fine-Head: "+finehead.getFineHeadName()+" saved successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+finehead.getFineHeadName()+". "+de.getMessage());
            return "universal/add-finehead";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/add-finehead";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/add-finehead";
        }
        return "redirect:/universal/finehead";
    }

    @CheckAccess(screen = "GLOBAL_FINEHEAD", type = AccessType.EDIT)
    @GetMapping("/finehead/edit/{id}")
    public String editFineheadForm(@PathVariable("id") Long id, Model model) {
        Finehead finehead = fineheadService.getFineheadById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid fine head Id:" + id));
        model.addAttribute("finehead", finehead);
        return "universal/edit-finehead";
    }

    @CheckAccess(screen = "GLOBAL_FINEHEAD", type = AccessType.EDIT)
    @PostMapping("/finehead/{id}")
    public String updateFinehead(@PathVariable("id") Long id, @Valid @ModelAttribute("finehead") Finehead finehead, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            finehead.setId(id);
            return "universal/edit-finehead";
        }
        try{
            fineheadService.saveFinehead(finehead);
            ra.addFlashAttribute("success","Fine-Head: "+finehead.getFineHeadName()+" updated successfully.");
        } catch(DataIntegrityViolationException de){
            model.addAttribute("error", "Duplicate entry for: "+finehead.getFineHeadName()+". "+de.getMessage());
            return "universal/edit-finehead";
        } catch (ObjectNotSaveException ee) { // Catch your custom exception
            model.addAttribute("error", "Error in saving. "+ee.getMessage());
            return "universal/edit-finehead";
        } catch (Exception e) { // Catch any unexpected exceptions
            model.addAttribute("error", "An unexpected error occurred: " + e.getLocalizedMessage());
            return "universal/edit-finehead";
        }
        return "redirect:/universal/finehead";
    }
    @CheckAccess(screen = "GLOBAL_FINEHEAD", type = AccessType.DELETE)
    @PostMapping("/finehead/delete/{id}")
    public String deleteFinehead(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            fineheadService.deleteFinehead(id);
            redirectAttributes.addFlashAttribute("success", "Fine Head deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete fine head: " + e.getMessage());
        }
        return "redirect:/universal/finehead";
    }

    /*****************   Fine Head Ends Here  *****************/
}

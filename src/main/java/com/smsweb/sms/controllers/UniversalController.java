package com.smsweb.sms.controllers;

import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.services.universal.MediumService;
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

    private MediumService mediumService;


    @Autowired
    public UniversalController(MediumService mediumService) {
        this.mediumService = mediumService;
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
}

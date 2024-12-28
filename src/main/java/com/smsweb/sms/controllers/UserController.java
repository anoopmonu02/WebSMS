package com.smsweb.sms.controllers;


import com.smsweb.sms.models.Users.Roles;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.repositories.users.RoleRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;

@Controller
public class UserController {


    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new UserEntity());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(UserEntity user, RedirectAttributes redirectAttributes) {
        try {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            Roles userRole = roleRepository.findByName("ROLE_USER");
            user.setRoles(Collections.singletonList(userRole));
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("successMessage", "User registered successfully! Please login.");
            return "redirect:/login";
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Username or email already exists");
            return "redirect:/register";
        }
    }

    @GetMapping("/login")
    public String login() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() &&
                !authentication.getPrincipal().equals("anonymousUser")) {
            // If already authenticated, redirect to dashboard
            return "redirect:/dashboard";
        }

        // If not authenticated, proceed to login page
        return "login";  // Make sure you have a login.html template
    }

}

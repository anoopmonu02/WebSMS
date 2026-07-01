package com.smsweb.sms.controllers.student;

import com.smsweb.sms.models.Users.PasswordResetToken;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.services.globalaccess.EmailService;
import com.smsweb.sms.services.users.PasswordResetTokenService;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordResetTokenService passwordResetTokenService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        log.info("Inside showForgotPasswordPage");
        return "forgot-password";  // This should be the name of your template
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam("email") String email, Model model, HttpServletRequest request) {
        log.info("Inside forgotPassword");
        UserEntity user = userService.findByEmail(email);
        if (user == null) {
            model.addAttribute("error", "No user associated with this email address.");
            return "forgot-password";
        }

        // Check if a token already exists for this user and delete it if necessary
        PasswordResetToken existingToken = passwordResetTokenService.findByUser(user);
        if (existingToken != null) {
            passwordResetTokenService.delete(existingToken);
        }

        // Create a new token for the user
        PasswordResetToken newToken = passwordResetTokenService.createToken(user);
        int port = request.getServerPort();
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (port == 80 || port == 443 ? "" : ":" + port)
                + request.getContextPath();
        String resetLink = baseUrl + "/auth/reset-password?token=" + newToken.getToken();
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            model.addAttribute("message", "Password reset link has been sent to your email address.");
        } catch (Exception e) {
            // Token was created — delete it so the user can try again cleanly
            passwordResetTokenService.delete(newToken);
            log.error("Password reset email failed for {}: {}", email, e.getMessage(), e);
            model.addAttribute("error", "Could not send reset email: " + e.getMessage());
        }
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(@RequestParam("token") String token, Model model) {
        log.info("Inside showResetPasswordPage");
        PasswordResetToken resetToken = passwordResetTokenService.findByToken(token).orElse(null);
        if (resetToken == null || resetToken.isExpired()) {
            model.addAttribute("error", "Invalid or expired password reset token.");
            return "forgot-password";
        }

        model.addAttribute("token", token);
        return "reset-password";  // This should be the name of your template
    }

    // AuthController.java

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("token") String token,
                                @RequestParam("password") String password,
                                Model model) {
        log.info("Inside resetPassword");
        Optional<PasswordResetToken> resetTokenOptional = passwordResetTokenService.findByToken(token);

        if (!resetTokenOptional.isPresent()) {
            model.addAttribute("error", "Invalid password reset token.");
            return "forgot-password"; // Redirect or show the error page
        }

        PasswordResetToken resetToken = resetTokenOptional.get();

        if (!passwordResetTokenService.isTokenValid(resetToken)) {
            model.addAttribute("error", "Invalid or expired password reset token.");
            return "forgot-password"; // Redirect or show the error page
        }

        UserEntity user = resetToken.getUser();
        userService.updatePassword(user, password);

        // Invalidate the token after successful password reset
        passwordResetTokenService.invalidateToken(resetToken);

        model.addAttribute("message", "Password has been reset successfully.");
        return "successResetPassword"; // Adjust as needed for your success page
    }

    // ── Change Password (for logged-in users) ─────────────────────────────────

    @GetMapping("/change-password")
    public String showChangePasswordPage() {
        log.info("Inside showChangePasswordPage");
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {
        log.info("Inside changePassword");

        UserEntity user = userService.getLoggedInUser();
        if (user == null) {
            return "redirect:/login";
        }

        // Validate current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            model.addAttribute("error", "Current password is incorrect.");
            return "change-password";
        }

        // Validate new and confirm passwords match
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "New password and confirm password do not match.");
            return "change-password";
        }

        // Validate minimum length
        if (newPassword.length() < 6) {
            model.addAttribute("error", "New password must be at least 6 characters.");
            return "change-password";
        }

        userService.updatePassword(user, newPassword);
        model.addAttribute("success", "Password changed successfully.");
        return "change-password";
    }

}

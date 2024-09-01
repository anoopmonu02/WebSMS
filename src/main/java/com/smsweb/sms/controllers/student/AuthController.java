package com.smsweb.sms.controllers.student;

import com.smsweb.sms.models.Users.PasswordResetToken;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.services.globalaccess.EmailService;
import com.smsweb.sms.services.users.PasswordResetTokenService;
import com.smsweb.sms.services.users.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordResetTokenService passwordResetTokenService;

    @Autowired
    private EmailService emailService;

    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";  // This should be the name of your template
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam("email") String email, Model model) {
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
        String resetLink = "http://localhost:9090/auth/reset-password?token=" + newToken.getToken();
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

        model.addAttribute("message", "Password reset link has been sent to your email address.");
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(@RequestParam("token") String token, Model model) {
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


}

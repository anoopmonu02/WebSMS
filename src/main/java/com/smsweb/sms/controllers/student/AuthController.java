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

        PasswordResetToken token = passwordResetTokenService.createToken(user);
        String resetLink = "http://localhost:9090/auth/reset-password?token=" + token.getToken();
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

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("token") String token, @RequestParam("password") String password, Model model) {
        PasswordResetToken resetToken = passwordResetTokenService.findByToken(token).orElse(null);
        if (resetToken == null || resetToken.isExpired()) {
            model.addAttribute("error", "Invalid or expired password reset token.");
            return "forgot-password";
        }

        UserEntity user = resetToken.getUser();
        userService.updatePassword(user, password);

        model.addAttribute("message", "Your password has been successfully reset.");
        return "login";
    }
}

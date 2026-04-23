package com.bookstore.controller;

import com.bookstore.model.User;
import com.bookstore.service.AuthService;
import com.bookstore.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Random;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final AuthService authService;
    private final UserService userService;

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String error,
            HttpSession session,
            Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user != null) {
            // Sửa ở đây: Nếu là Admin hoặc Staff thì vào /admin
            return authService.canAccessAdminPanel(user) ? "redirect:/admin" : "redirect:/";
        }

        if (error != null) {
            model.addAttribute("error", error);
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        String normalizedEmail = email == null ? "" : email.trim();
        User user = authService.authenticate(normalizedEmail, password);
        if (user == null) {
            redirectAttributes.addAttribute("error", "Invalid email or password");
            return "redirect:/login";
        }

        session.setAttribute("loggedInUser", user);
        // Sửa ở đây: Nếu là Admin hoặc Staff thì vào /admin
        return authService.canAccessAdminPanel(user) ? "redirect:/admin" : "redirect:/";
    }

    @GetMapping("/register")
    public String registerForm(@RequestParam(required = false) String error, HttpSession session, Model model) {
        if (error != null) model.addAttribute("error", error);
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        if (authService.findByEmail(email).isPresent()) {
            redirectAttributes.addAttribute("error", "Email already exists");
            return "redirect:/register";
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        
        session.setAttribute("regFullName", fullName);
        session.setAttribute("regEmail", email);
        session.setAttribute("regPassword", password);
        session.setAttribute("regPhone", phone);
        session.setAttribute("regAddress", address);
        session.setAttribute("otp", otp);
        session.setAttribute("otpExpiry", System.currentTimeMillis() + 180000); 

        userService.sendOtp(email, otp);

        return "redirect:/otp";
    }

    @GetMapping("/forgot-password")
    public String showConfirmMailPage() {
        return "confirmmail";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam String email, HttpSession session, RedirectAttributes redirectAttributes) {
        if (authService.findByEmail(email).isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Email không tồn tại trong hệ thống!");
            return "redirect:/forgot-password";
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        
        session.setAttribute("forgotEmail", email);
        session.setAttribute("otp", otp);
        session.setAttribute("otpExpiry", System.currentTimeMillis() + 180000);

        userService.sendOtp(email, otp);

        return "redirect:/otp";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(HttpSession session) {
        if (session.getAttribute("forgotEmail") == null || session.getAttribute("otpVerified") == null) {
            return "redirect:/forgot-password";
        }
        return "forgotpass";
    }

    @PostMapping("/reset-password")
    public String handleResetPassword(@RequestParam String newPassword, 
                                    @RequestParam String confirmPassword, 
                                    HttpSession session, 
                                    RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute("forgotEmail");
        
        if (email == null || session.getAttribute("otpVerified") == null) {
            return "redirect:/forgot-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu xác nhận không khớp!");
            return "redirect:/reset-password";
        }

        authService.resetPassword(email, newPassword);
        
        // Clear session
        session.removeAttribute("forgotEmail");
        session.removeAttribute("otpVerified");
        
        redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công! Vui lòng đăng nhập.");
        return "redirect:/login";
    }

    @GetMapping("/otp")
    public String showOtpPage(HttpSession session) {
        if (session.getAttribute("otp") == null) return "redirect:/login";
        return "otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam String userOtp, HttpSession session, RedirectAttributes redirectAttributes) {
        String serverOtp = (String) session.getAttribute("otp");
        Long expiry = (Long) session.getAttribute("otpExpiry");

        if (serverOtp == null || expiry == null || System.currentTimeMillis() > expiry) {
            redirectAttributes.addFlashAttribute("error", "Mã OTP đã hết hạn!");
            return "redirect:/otp";
        }

        if (serverOtp.equals(userOtp)) {
            if (session.getAttribute("regEmail") != null) {
                authService.register(
                    (String) session.getAttribute("regFullName"),
                    (String) session.getAttribute("regEmail"),
                    (String) session.getAttribute("regPassword"),
                    (String) session.getAttribute("regPhone"),
                    (String) session.getAttribute("regAddress")
                );
                session.removeAttribute("otp");
                session.removeAttribute("regEmail");
                return "redirect:/login?verified=true";
            } else if (session.getAttribute("forgotEmail") != null) {
                session.setAttribute("otpVerified", true);
                session.removeAttribute("otp");
                return "redirect:/reset-password";
            }
            return "redirect:/login";
        } else {
            redirectAttributes.addFlashAttribute("error", "Mã OTP không chính xác!");
            return "redirect:/otp";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @PostMapping("/logout")
    public String logoutPost(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}

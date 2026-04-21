package com.bookstore.controller;

import com.bookstore.model.User;
import com.bookstore.service.AuthService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final AuthService authService;

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String error,
            HttpSession session,
            Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user != null) {
            return authService.isAdmin(user) ? "redirect:/admin" : "redirect:/";
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
        String normalizedPassword = password == null ? "" : password;

        if (normalizedEmail.length() > 150) {
            redirectAttributes.addAttribute("error", "Email too long");
            return "redirect:/login";
        }
        if (normalizedPassword.length() > 72) {
            redirectAttributes.addAttribute("error", "Password too long");
            return "redirect:/login";
        }
        if (normalizedEmail.isEmpty() || normalizedPassword.isBlank()) {
            redirectAttributes.addAttribute("error", "Please enter email and password");
            return "redirect:/login";
        }
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            redirectAttributes.addAttribute("error", "Invalid email");
            return "redirect:/login";
        }

        User user = authService.authenticate(normalizedEmail, normalizedPassword);
        if (user == null) {
            redirectAttributes.addAttribute("error", "Invalid email or password");
            return "redirect:/login";
        }

        session.setAttribute("loggedInUser", user);
        return authService.isAdmin(user) ? "redirect:/admin" : "redirect:/";
    }

    @GetMapping("/register")
    public String registerForm(@RequestParam(required = false) String error,
            HttpSession session,
            Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user != null) {
            return authService.isAdmin(user) ? "redirect:/admin" : "redirect:/";
        }

        if (error != null) {
            model.addAttribute("error", error);
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            RedirectAttributes redirectAttributes) {
        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        String normalizedPassword = password == null ? "" : password;
        String normalizedPhone = phone == null ? "" : phone.trim();
        String normalizedAddress = address == null ? "" : address.trim();

        if (normalizedName.length() > 100) {
            redirectAttributes.addAttribute("error", "Full name too long");
            return "redirect:/register";
        }
        if (normalizedEmail.length() > 150) {
            redirectAttributes.addAttribute("error", "Email too long");
            return "redirect:/register";
        }
        if (normalizedPassword.length() > 72) {
            redirectAttributes.addAttribute("error", "Password too long");
            return "redirect:/register";
        }
        if (!normalizedAddress.isEmpty() && normalizedAddress.length() > 255) {
            redirectAttributes.addAttribute("error", "Address too long");
            return "redirect:/register";
        }

        if (normalizedName.isEmpty()) {
            redirectAttributes.addAttribute("error", "Full name is required");
            return "redirect:/register";
        }
        if (normalizedEmail.isEmpty() || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            redirectAttributes.addAttribute("error", "Invalid email");
            return "redirect:/register";
        }
        if (normalizedPassword.length() < 6) {
            redirectAttributes.addAttribute("error", "Password must be at least 6 characters");
            return "redirect:/register";
        }
        if (!normalizedPhone.isEmpty() && !normalizedPhone.matches("\\d{8,15}")) {
            redirectAttributes.addAttribute("error", "Phone number must be 10-11 digits");
            return "redirect:/register";
        }
        if (authService.findByEmail(normalizedEmail).isPresent()) {
            redirectAttributes.addAttribute("error", "Email already exists");
            return "redirect:/register";
        }

        authService.register(
                normalizedName,
                normalizedEmail,
                normalizedPassword,
                normalizedPhone.isEmpty() ? null : normalizedPhone,
                normalizedAddress.isEmpty() ? null : normalizedAddress);
        return "redirect:/login";
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

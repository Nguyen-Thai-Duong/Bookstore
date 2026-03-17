package org.example.bookstore.controller;

import jakarta.servlet.http.HttpSession;
import org.example.bookstore.model.User;
import org.example.bookstore.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String error, Model model) {
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
        User user = authService.authenticate(email, password);
        if (user == null) {
            redirectAttributes.addAttribute("error", "Invalid email or password");
            return "redirect:/login";
        }
        session.setAttribute("loggedInUser", user);
        return "redirect:/";
    }

    @GetMapping("/register")
    public String registerForm(@RequestParam(required = false) String error, Model model) {
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
        if (authService.findByEmail(email).isPresent()) {
            redirectAttributes.addAttribute("error", "Email already exists");
            return "redirect:/register";
        }
        authService.register(fullName, email, password, phone, address);
        return "redirect:/login";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}

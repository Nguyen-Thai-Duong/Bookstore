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

@Controller
@RequiredArgsConstructor
public class AccountController {

    private final AuthService authService;

    @GetMapping("/account")
    public String account(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        return "account";
    }

    @PostMapping("/account/profile")
    public String updateProfile(@RequestParam String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedPhone = phone == null ? "" : phone.trim();
        String normalizedAddress = address == null ? "" : address.trim();

        if (normalizedName.length() > 100) {
            redirectAttributes.addFlashAttribute("profileError", "Họ và tên quá dài");
            return "redirect:/account";
        }
        if (!normalizedAddress.isEmpty() && normalizedAddress.length() > 255) {
            redirectAttributes.addFlashAttribute("profileError", "Địa chỉ quá dài");
            return "redirect:/account";
        }
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("profileError", "Họ và tên không được để trống");
            return "redirect:/account";
        }
        if (!normalizedPhone.isEmpty() && !normalizedPhone.matches("\\d{8,15}")) {
            redirectAttributes.addFlashAttribute("profileError", "Số điện thoại phải từ 10-11 chữ số");
            return "redirect:/account";
        }

        User updated = authService.updateProfile(
                user.getId(),
                normalizedName,
                normalizedPhone.isEmpty() ? null : normalizedPhone,
                normalizedAddress.isEmpty() ? null : normalizedAddress);

        if (updated == null) {
            redirectAttributes.addFlashAttribute("profileError", "Không thể cập nhật thông tin");
            return "redirect:/account";
        }

        session.setAttribute("loggedInUser", updated);
        redirectAttributes.addFlashAttribute("profileSuccess", "Cập nhật thông tin thành công");
        return "redirect:/account";
    }

    @PostMapping("/account/password")
    public String changePassword(@RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        if (currentPassword == null || currentPassword.isBlank()) {
            redirectAttributes.addFlashAttribute("passwordError", "Vui lòng nhập mật khẩu hiện tại");
            return "redirect:/account";
        }
        if (newPassword == null || newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("passwordError", "Mật khẩu mới phải có ít nhất 6 ký tự");
            return "redirect:/account";
        }
        if (newPassword.length() > 72) {
            redirectAttributes.addFlashAttribute("passwordError", "Mật khẩu quá dài");
            return "redirect:/account";
        }
        if (newPassword.equals(currentPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "Mật khẩu mới phải khác mật khẩu hiện tại");
            return "redirect:/account";
        }
        if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "Xác nhận mật khẩu không khớp");
            return "redirect:/account";
        }

        boolean changed = authService.changePassword(user.getId(), currentPassword, newPassword);
        if (!changed) {
            redirectAttributes.addFlashAttribute("passwordError", "Mật khẩu hiện tại không đúng");
            return "redirect:/account";
        }

        redirectAttributes.addFlashAttribute("passwordSuccess", "Đổi mật khẩu thành công");
        return "redirect:/account";
    }
}

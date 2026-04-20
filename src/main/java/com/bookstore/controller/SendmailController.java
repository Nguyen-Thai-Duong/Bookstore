package com.bookstore.controller;

import com.bookstore.model.User;
import com.bookstore.model.Voucher;
import com.bookstore.service.UserService;
import com.bookstore.service.VoucherService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/admin/sendmail")
@RequiredArgsConstructor
public class SendmailController {

    private final UserService userService;
    private final VoucherService voucherService;

    @GetMapping("/select-user")
    public String listUsersForVoucher(@RequestParam Long voucherId, Model model) {
        Voucher voucher = voucherService.getVoucherById(voucherId).orElse(null);
        
        if (voucher == null || !"Active".equalsIgnoreCase(voucher.getStatus())) {
            return "redirect:/admin/vouchers";
        }

        List<User> customers = userService.getCustomersOnly();
        model.addAttribute("users", customers);
        model.addAttribute("voucher", voucher);
        return "admin/vouchers/listuser";
    }

    @PostMapping("/send-voucher")
    public String sendVoucher(@RequestParam Long userId, @RequestParam Long voucherId, RedirectAttributes redirectAttributes) {
        User user = userService.getUserById(userId).orElse(null);
        Voucher voucher = voucherService.getVoucherById(voucherId).orElse(null);

        if (user == null || voucher == null) {
            redirectAttributes.addFlashAttribute("error", "Dữ liệu không hợp lệ.");
            return "redirect:/admin/vouchers";
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String startDate = voucher.getStartDate() != null ? voucher.getStartDate().format(formatter) : "N/A";
            String endDate = voucher.getEndDate() != null ? voucher.getEndDate().format(formatter) : "N/A";

            voucherService.sendVoucherEmail(
                user.getEmail(),
                voucher.getCode(),
                voucher.getDiscountPercent() + "%",
                startDate,
                endDate
            );

            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi voucher " + voucher.getCode() + " cho " + user.getFullName());
        } catch (MessagingException e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi gửi mail: " + e.getMessage());
        }

        return "redirect:/admin/vouchers";
    }
}

package com.bookstore.controller;

import com.bookstore.model.User;
import com.bookstore.model.Voucher;
import com.bookstore.repository.OrderRepository;
import com.bookstore.service.UserService;
import com.bookstore.service.VoucherService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/sendmail")
@RequiredArgsConstructor
public class SendmailController {

    private final UserService userService;
    private final VoucherService voucherService;
    private final OrderRepository orderRepository;

    @GetMapping("/select-user")
    public String listUsersForVoucher(@RequestParam Long voucherId, Model model) {
        Voucher voucher = voucherService.getVoucherById(voucherId).orElse(null);

        if (voucher == null || !"Active".equalsIgnoreCase(voucher.getStatus())) {
            return "redirect:/admin/vouchers";
        }

        List<User> customers = userService.getCustomersOnly();
        List<Long> customerIds = customers.stream().map(User::getId).toList();
        Map<Long, Long> completedOrderCounts = orderRepository
                .countOrdersByUserIdsAndStatusIgnoreCase(customerIds, "Completed")
                .stream()
                .filter(row -> row != null && row.length >= 2 && row[0] != null && row[1] != null)
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1],
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<User> sortedCustomers = customers.stream()
                .sorted(Comparator
                        .comparingLong((User user) -> completedOrderCounts.getOrDefault(user.getId(), 0L))
                        .reversed()
                        .thenComparing(user -> user.getFullName() == null ? "" : user.getFullName(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();

        model.addAttribute("users", sortedCustomers);
        model.addAttribute("completedOrderCounts", completedOrderCounts);
        model.addAttribute("voucher", voucher);
        return "admin/vouchers/listuser";
    }

    @PostMapping("/send-voucher")
    public String sendVoucher(@RequestParam Long userId, @RequestParam Long voucherId,
            RedirectAttributes redirectAttributes) {
        User user = userService.getUserById(userId).orElse(null);
        Voucher voucher = voucherService.getVoucherById(voucherId).orElse(null);

        if (user == null || voucher == null) {
            redirectAttributes.addFlashAttribute("error", "User or voucher not found (invalid data).");
            return "redirect:/admin/sendmail/select-user?voucherId=" + voucherId;
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
                    endDate);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Voucher email sent successfully. Code: " + voucher.getCode() + " • Recipient: "
                            + user.getFullName() + " (" + user.getEmail() + ")");
        } catch (MessagingException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to send voucher email: " + e.getMessage());
        }

        return "redirect:/admin/sendmail/select-user?voucherId=" + voucherId;
    }
}

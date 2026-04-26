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

import java.time.LocalDateTime;
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
    public String listUsersForVoucher(
            @RequestParam Long voucherId,
            @RequestParam(required = false, defaultValue = "0") Integer minOrders,
            @RequestParam(required = false, defaultValue = "0") int days,
            Model model) {
        Voucher voucher = voucherService.getVoucherById(voucherId).orElse(null);

        if (voucher == null || !"Active".equalsIgnoreCase(voucher.getStatus())) {
            return "redirect:/admin/vouchers";
        }

        List<User> customers = userService.getCustomersOnly();
        List<Long> customerIds = customers.stream().map(User::getId).toList();
        
        List<Object[]> countRows;
        if (days > 0) {
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            countRows = orderRepository.countOrdersByUserIdsAndStatusIgnoreCaseAndDateAfter(customerIds, "Completed", startDate);
        } else {
            countRows = orderRepository.countOrdersByUserIdsAndStatusIgnoreCase(customerIds, "Completed");
        }

        Map<Long, Long> completedOrderCounts = countRows.stream()
                .filter(row -> row != null && row.length >= 2 && row[0] != null && row[1] != null)
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1],
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<User> filteredCustomers = customers.stream()
                .filter(user -> {
                    long count = completedOrderCounts.getOrDefault(user.getId(), 0L);
                    return count >= minOrders;
                })
                .sorted(Comparator
                        .comparingLong((User user) -> completedOrderCounts.getOrDefault(user.getId(), 0L))
                        .reversed()
                        .thenComparing(user -> user.getFullName() == null ? "" : user.getFullName(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();

        model.addAttribute("users", filteredCustomers);
        model.addAttribute("completedOrderCounts", completedOrderCounts);
        model.addAttribute("voucher", voucher);
        model.addAttribute("minOrders", minOrders);
        model.addAttribute("currentDays", days);
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

    @PostMapping("/send-voucher-bulk")
    public String sendVoucherBulk(@RequestParam List<Long> userIds, @RequestParam Long voucherId,
            RedirectAttributes redirectAttributes) {
        Voucher voucher = voucherService.getVoucherById(voucherId).orElse(null);
        if (voucher == null || userIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Invalid data for bulk send.");
            return "redirect:/admin/vouchers";
        }

        int successCount = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String startDate = voucher.getStartDate() != null ? voucher.getStartDate().format(formatter) : "N/A";
        String endDate = voucher.getEndDate() != null ? voucher.getEndDate().format(formatter) : "N/A";

        for (Long userId : userIds) {
            userService.getUserById(userId).ifPresent(user -> {
                try {
                    voucherService.sendVoucherEmail(
                            user.getEmail(),
                            voucher.getCode(),
                            voucher.getDiscountPercent() + "%",
                            startDate,
                            endDate);
                } catch (MessagingException ignored) {
                }
            });
            successCount++;
        }

        redirectAttributes.addFlashAttribute("successMessage", "Successfully sent voucher to " + successCount + " customers.");
        return "redirect:/admin/sendmail/select-user?voucherId=" + voucherId;
    }
}

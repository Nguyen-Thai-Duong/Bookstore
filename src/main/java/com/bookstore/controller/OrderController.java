package com.bookstore.controller;

import com.bookstore.dto.OrderDTO;
import com.bookstore.model.Order;
import com.bookstore.model.User;
import com.bookstore.repository.OrderRepository;
import com.bookstore.service.AuthService;
import com.bookstore.service.OrderService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final AuthService authService;

    @GetMapping
    public String listOrders(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        List<OrderDTO> orders;
        if (authService.isAdmin(user)) {
            orders = orderService.getAllOrders().stream()
                    .map(OrderDTO::fromEntity)
                    .toList();
        } else {
            orders = orderRepository.findByUser_IdOrderByOrderDateDesc(user.getId()).stream()
                    .map(OrderDTO::fromEntity)
                    .toList();
        }

        model.addAttribute("orders", orders);
        return "orders";
    }

    @GetMapping("/{id}")
    public String viewOrder(@PathVariable Long id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        var orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return "redirect:/orders";
        }

        Order order = orderOpt.get();
        // Only allow viewing your own orders or admin access
        if (!authService.isAdmin(user) && !order.getUser().getId().equals(user.getId())) {
            return "redirect:/orders";
        }

        model.addAttribute("order", OrderDTO.fromEntity(order));
        return "orders/view";
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        var orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("orderError", "Order not found");
            return "redirect:/orders";
        }

        Order order = orderOpt.get();
        if (!order.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("orderError", "You do not have permission to modify this order");
            return "redirect:/orders";
        }

        if (!isPendingStatus(order.getStatus())) {
            redirectAttributes.addFlashAttribute("orderError", "You can only cancel orders with pending status");
            return "redirect:/orders";
        }

        order.setStatus("Cancelled");
        orderService.saveOrder(order);
        redirectAttributes.addFlashAttribute("orderSuccess", "Order cancelled successfully");
        return "redirect:/orders";
    }

    private boolean isPendingStatus(String status) {
        if (status == null) {
            return false;
        }

        String normalized = Normalizer.normalize(status, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized.equals("pending") || normalized.equals("cho xu ly");
    }

}

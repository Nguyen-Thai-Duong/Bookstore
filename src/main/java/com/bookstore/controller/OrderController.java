package com.bookstore.controller;

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

import java.util.List;
import java.text.Normalizer;
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

        List<Order> orders = authService.isAdmin(user)
                ? orderService.getAllOrders()
                : orderRepository.findByUser_IdOrderByOrderDateDesc(user.getId());

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
        // Chỉ cho phép xem đơn hàng của mình hoặc admin
        if (!authService.isAdmin(user) && !order.getUser().getId().equals(user.getId())) {
            return "redirect:/orders";
        }

        model.addAttribute("order", order);
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
            redirectAttributes.addFlashAttribute("orderError", "Không tìm thấy đơn hàng");
            return "redirect:/orders";
        }

        Order order = orderOpt.get();
        if (!order.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("orderError", "Bạn không có quyền thao tác đơn hàng này");
            return "redirect:/orders";
        }

        if (!isPendingStatus(order.getStatus())) {
            redirectAttributes.addFlashAttribute("orderError", "Chỉ có thể hủy đơn ở trạng thái chờ xử lý");
            return "redirect:/orders";
        }

        order.setStatus("Đã hủy");
        orderService.saveOrder(order);
        redirectAttributes.addFlashAttribute("orderSuccess", "Hủy đơn hàng thành công");
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

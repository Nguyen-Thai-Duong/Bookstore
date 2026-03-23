package org.example.bookstore.controller;

import jakarta.servlet.http.HttpSession;
import org.example.bookstore.model.Order;
import org.example.bookstore.model.OrderItem;
import org.example.bookstore.model.User;
import org.example.bookstore.repository.OrderItemRepository;
import org.example.bookstore.repository.OrderRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderController(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @GetMapping("/orders")
    public String orderHistory(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        List<Order> orders = orderRepository.findByUserUserIDOrderByOrderDateDesc(user.getUserID());
        model.addAttribute("orders", orders);
        return "orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable int id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        if (id <= 0) {
            return "redirect:/orders";
        }
        Optional<Order> orderOpt = user.getRoleID() == 1
                ? orderRepository.findByOrderID(id)
                : orderRepository.findByOrderIDAndUserUserID(id, user.getUserID());
        if (orderOpt.isEmpty()) {
            return "redirect:/orders";
        }
        Order order = orderOpt.get();
        List<OrderItem> items = orderItemRepository.findByOrderOrderID(order.getOrderID());
        model.addAttribute("order", order);
        model.addAttribute("items", items);
        return "order-detail";
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(@PathVariable int id,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        if (id <= 0) {
            return "redirect:/orders";
        }
        Optional<Order> orderOpt = orderRepository.findByOrderID(id);
        if (orderOpt.isEmpty()) {
            return "redirect:/orders";
        }
        Order order = orderOpt.get();
        if (order.getUser().getUserID() != user.getUserID() && user.getRoleID() != 1) {
            return "redirect:/orders";
        }
        if ("Pending".equalsIgnoreCase(order.getOrderStatus())) {
            order.setOrderStatus("Canceled");
            orderRepository.save(order);
            redirectAttributes.addFlashAttribute("orderMessage", "Đã hủy đơn hàng");
        } else {
            redirectAttributes.addFlashAttribute("orderMessage", "Không thể hủy đơn hàng này");
        }
        return "redirect:/orders/" + id;
    }
}

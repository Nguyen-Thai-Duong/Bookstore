package com.bookstore.controller;

import com.bookstore.model.*;
import com.bookstore.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/purchase-orders")
@RequiredArgsConstructor
public class AdminPurchaseOrderController {

    private final PurchaseOrderService poService;
    private final SupplierService supplierService;
    private final BookService bookService;

    @GetMapping
    public String listForStaff(Model model) {
        model.addAttribute("orders", poService.getAllOrders());
        model.addAttribute("activePage", "purchase-orders");
        return "admin/purchase-order/staff-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("suppliers", supplierService.getAllSuppliers());
        model.addAttribute("products", bookService.getAllBooks());
        model.addAttribute("activePage", "purchase-orders");
        return "admin/purchase-order/form";
    }

    @PostMapping("/save")
    public String saveOrder(@RequestParam Integer supplierId,
                            @RequestParam List<Long> productIds,
                            @RequestParam List<Integer> quantities,
                            @RequestParam List<BigDecimal> prices,
                            HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        Supplier supplier = supplierService.getSupplierById(supplierId).orElseThrow();

        PurchaseOrder po = new PurchaseOrder();
        po.setSupplier(supplier);
        po.setUser(user);
        po.setStatus("Requested");
        
        List<PurchaseOrderDetail> details = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < productIds.size(); i++) {
            Book book = bookService.getBookById(productIds.get(i)).orElseThrow();
            PurchaseOrderDetail detail = new PurchaseOrderDetail();
            detail.setPurchaseOrder(po);
            detail.setProduct(book);
            detail.setOrderedQuantity(quantities.get(i));
            detail.setUnitPrice(prices.get(i));
            details.add(detail);
            total = total.add(prices.get(i).multiply(BigDecimal.valueOf(quantities.get(i))));
        }
        
        po.setDetails(details);
        po.setTotalAmount(total);
        poService.saveOrder(po);
        return "redirect:/admin/purchase-orders";
    }

    @GetMapping("/view/{id}")
    public String viewDetail(@PathVariable Integer id, Model model, HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        poService.getOrderById(id).ifPresent(order -> model.addAttribute("order", order));
        
        String role = user.getRole().getRoleName();
        model.addAttribute("activePage", role.equalsIgnoreCase("Admin") ? "confirm-purchase-orders" : "purchase-orders");
        return "admin/purchase-order/detail";
    }

    @GetMapping("/receive/{id}")
    public String showReceiveForm(@PathVariable Integer id, Model model) {
        poService.getOrderById(id).ifPresent(order -> model.addAttribute("order", order));
        model.addAttribute("activePage", "purchase-orders");
        return "admin/purchase-order/receive-form";
    }

    @PostMapping("/receive/save")
    public String saveReceivedQuantity(@RequestParam Integer poId, 
                                      @RequestParam List<Integer> detailIds,
                                      @RequestParam List<Integer> receivedQtys) {
        List<PurchaseOrderDetail> details = new ArrayList<>();
        for (int i = 0; i < detailIds.size(); i++) {
            PurchaseOrderDetail d = new PurchaseOrderDetail();
            d.setId(detailIds.get(i));
            d.setReceivedQuantity(receivedQtys.get(i));
            details.add(d);
        }
        poService.receiveOrder(poId, details);
        return "redirect:/admin/purchase-orders";
    }

    @GetMapping("/finalize/{id}")
    public String showFinalizeForm(@PathVariable Integer id, Model model) {
        poService.getOrderById(id).ifPresent(order -> model.addAttribute("order", order));
        model.addAttribute("activePage", "confirm-purchase-orders");
        return "admin/purchase-order/finalize";
    }

    @PostMapping("/finalize/save")
    public String finalizeOrder(@RequestParam Integer poId, @RequestParam(required = false) String note) {
        poService.finalizeOrder(poId, note);
        return "redirect:/admin/purchase-orders/confirm";
    }

    @PostMapping("/stock-in/{id}")
    public String stockIn(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            poService.confirmAndStockIn(id);
            ra.addFlashAttribute("successMessage", "Stock updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/purchase-orders";
    }

    @GetMapping("/confirm")
    public String listForAdmin(Model model) {
        model.addAttribute("orders", poService.getAllOrders());
        model.addAttribute("activePage", "confirm-purchase-orders");
        return "admin/purchase-order/admin-list";
    }

    @PostMapping("/approve/{id}")
    public String approve(@PathVariable Integer id) {
        poService.approveOrder(id);
        return "redirect:/admin/purchase-orders/confirm";
    }

    @PostMapping("/cancel/{id}")
    public String cancel(@PathVariable Integer id) {
        poService.cancelOrder(id);
        return "redirect:/admin/purchase-orders/confirm";
    }
}

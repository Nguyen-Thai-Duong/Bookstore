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
@RequestMapping("/admin/imports")
@RequiredArgsConstructor
public class AdminImportController {

    private final ImportService importService;
    private final SupplierService supplierService;
    private final BookService bookService;

    @GetMapping
    public String listForStaff(Model model) {
        model.addAttribute("imports", importService.getAllImportsSorted());
        model.addAttribute("activePage", "imports");
        return "admin/import/staff-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("suppliers", supplierService.getAllActiveSuppliers());
        model.addAttribute("products", bookService.getAllActiveBooks());
        model.addAttribute("activePage", "imports");
        return "admin/import/form";
    }

    @PostMapping("/save")
    public String saveImport(@RequestParam Integer supplierId,
                            @RequestParam List<Long> productIds,
                            @RequestParam List<Integer> quantities,
                            @RequestParam List<BigDecimal> prices,
                            HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        Supplier supplier = supplierService.getSupplierById(supplierId).orElseThrow();

        Import importOrder = new Import();
        importOrder.setSupplier(supplier);
        importOrder.setUser(user);
        importOrder.setStatus("Requested");
        
        List<ImportDetail> details = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < productIds.size(); i++) {
            Book book = bookService.getBookById(productIds.get(i)).orElseThrow();
            ImportDetail detail = new ImportDetail();
            detail.setImportOrder(importOrder);
            detail.setProduct(book);
            detail.setOrderedQuantity(quantities.get(i));
            detail.setUnitPrice(prices.get(i));
            details.add(detail);
            total = total.add(prices.get(i).multiply(BigDecimal.valueOf(quantities.get(i))));
        }
        
        importOrder.setDetails(details);
        importOrder.setTotalAmount(total);
        importService.saveImport(importOrder);
        return "redirect:/admin/imports";
    }

    @GetMapping("/view/{id}")
    public String viewDetail(@PathVariable Integer id, Model model, HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        importService.getImportById(id).ifPresent(importOrder -> model.addAttribute("importOrder", importOrder));
        
        String role = user.getRole().getRoleName();
        model.addAttribute("activePage", role.equalsIgnoreCase("Admin") ? "confirm-imports" : "imports");
        return "admin/import/detail";
    }

    @GetMapping("/receive/{id}")
    public String showReceiveForm(@PathVariable Integer id, Model model) {
        importService.getImportById(id).ifPresent(importOrder -> model.addAttribute("importOrder", importOrder));
        model.addAttribute("activePage", "imports");
        return "admin/import/receive-form";
    }

    @PostMapping("/receive/save")
    public String saveReceivedQuantity(@RequestParam Integer importId, 
                                      @RequestParam List<Integer> detailIds,
                                      @RequestParam List<Integer> receivedQtys) {
        List<ImportDetail> details = new ArrayList<>();
        for (int i = 0; i < detailIds.size(); i++) {
            ImportDetail d = new ImportDetail();
            d.setId(detailIds.get(i));
            d.setReceivedQuantity(receivedQtys.get(i));
            details.add(d);
        }
        importService.receiveImport(importId, details);
        return "redirect:/admin/imports";
    }

    @GetMapping("/finalize/{id}")
    public String showFinalizeForm(@PathVariable Integer id, Model model) {
        importService.getImportById(id).ifPresent(importOrder -> model.addAttribute("importOrder", importOrder));
        model.addAttribute("activePage", "confirm-imports");
        return "admin/import/finalize";
    }

    @PostMapping("/finalize/save")
    public String finalizeImport(@RequestParam Integer importId, @RequestParam(required = false) String note) {
        importService.finalizeImport(importId, note);
        return "redirect:/admin/imports/confirm";
    }

    @PostMapping("/stock-in/{id}")
    public String stockIn(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            importService.confirmAndStockIn(id);
            ra.addFlashAttribute("successMessage", "Stock updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/imports";
    }

    @GetMapping("/confirm")
    public String listForAdmin(Model model) {
        model.addAttribute("imports", importService.getAllImportsSorted());
        model.addAttribute("activePage", "confirm-imports");
        return "admin/import/admin-list";
    }

    @PostMapping("/approve/{id}")
    public String approve(@PathVariable Integer id) {
        importService.approveImport(id);
        return "redirect:/admin/imports/confirm";
    }

    @GetMapping("/cancel/{id}")
    public String showCancelForm(@PathVariable Integer id, Model model) {
        importService.getImportById(id).ifPresent(importOrder -> model.addAttribute("importOrder", importOrder));
        model.addAttribute("activePage", "confirm-imports");
        return "admin/import/cancel-form";
    }

    @PostMapping("/cancel/{id}")
    public String cancel(@PathVariable Integer id, @RequestParam(required = false) String note) {
        importService.cancelImport(id, note);
        return "redirect:/admin/imports/confirm";
    }
}

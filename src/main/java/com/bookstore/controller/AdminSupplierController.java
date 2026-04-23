package com.bookstore.controller;

import com.bookstore.model.Supplier;
import com.bookstore.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/suppliers")
@RequiredArgsConstructor
public class AdminSupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public String listSuppliers(Model model) {
        // Chỉ hiện những Supplier đang Active
        model.addAttribute("suppliers", supplierService.getAllActiveSuppliers());
        model.addAttribute("activePage", "suppliers");
        return "admin/supplier/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("supplier", new Supplier());
        model.addAttribute("activePage", "suppliers");
        return "admin/supplier/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Integer id, Model model) {
        supplierService.getSupplierById(id).ifPresent(s -> model.addAttribute("supplier", s));
        model.addAttribute("activePage", "suppliers");
        return "admin/supplier/form";
    }

    @PostMapping("/save")
    public String saveSupplier(@ModelAttribute Supplier supplier, RedirectAttributes ra) {
        supplierService.saveSupplier(supplier);
        ra.addFlashAttribute("successMessage", "Supplier saved successfully!");
        return "redirect:/admin/suppliers";
    }

    @GetMapping("/delete/{id}")
    public String deleteSupplier(@PathVariable Integer id, RedirectAttributes ra) {
        // Thực hiện xóa mềm (chuyển sang Inactive)
        supplierService.deleteSoft(id);
        ra.addFlashAttribute("successMessage", "Supplier status changed to Inactive.");
        return "redirect:/admin/suppliers";
    }
}

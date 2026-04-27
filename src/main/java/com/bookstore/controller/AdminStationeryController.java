package com.bookstore.controller;

import com.bookstore.dto.BookDTO;
import com.bookstore.dto.CategoryDTO;
import com.bookstore.model.Book;
import com.bookstore.model.Category;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.ImportDetailRepository;
import com.bookstore.repository.OrderDetailRepository;
import com.bookstore.service.BookService;
import com.bookstore.service.CategoryService;
import com.bookstore.service.ImportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/stationery")
public class AdminStationeryController {

    @Autowired
    private BookService bookService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ImportDetailRepository importDetailRepository;

    @Autowired
    private ImportService importService;

    @GetMapping
    public String listStationery(@RequestParam(required = false) String name,
                                @RequestParam(required = false) String brand,
                                Model model) {
        List<Book> stationeryItems = bookRepository.findByProductType(2L).stream()
                .filter(item -> !"Deleted".equals(item.getStatus()) && !item.isDiscontinued())
                .collect(Collectors.toList());

        if (name != null && !name.isEmpty()) {
            stationeryItems = stationeryItems.stream()
                    .filter(item -> item.getTitle().toLowerCase().contains(name.toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (brand != null && !brand.isEmpty()) {
            stationeryItems = stationeryItems.stream()
                    .filter(item -> item.getAuthor().toLowerCase().contains(brand.toLowerCase()))
                    .collect(Collectors.toList());
        }

        List<BookDTO> stationeryList = stationeryItems.stream()
                .map(BookDTO::fromEntity)
                .toList();
                
        model.addAttribute("stationeryList", stationeryList);
        model.addAttribute("activePage", "stationery");
        return "admin/stationery/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        BookDTO dto = new BookDTO();
        List<Category> stationeryCategories = categoryService.getCategoriesByProductType(2L);
        
        if (!stationeryCategories.isEmpty()) {
            dto.setCategory(CategoryDTO.fromEntity(stationeryCategories.get(0)));
        }

        model.addAttribute("item", dto);
        model.addAttribute("categories", stationeryCategories.stream().map(CategoryDTO::fromEntity).toList());
        model.addAttribute("activePage", "stationery");
        return "admin/stationery/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        bookService.getBookById(id).ifPresent(item -> {
            model.addAttribute("item", BookDTO.fromEntity(item));
        });
        model.addAttribute("categories", 
            categoryService.getCategoriesByProductType(2L).stream()
                .map(CategoryDTO::fromEntity)
                .toList());
        model.addAttribute("activePage", "stationery");
        return "admin/stationery/form";
    }

    @PostMapping("/save")
    public String saveStationery(@Valid @ModelAttribute("item") BookDTO itemDto,
                                BindingResult bindingResult,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                Model model) {
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", 
                categoryService.getCategoriesByProductType(2L).stream()
                    .map(CategoryDTO::fromEntity)
                    .toList());
            model.addAttribute("activePage", "stationery");
            return "admin/stationery/form";
        }

        Book item = itemDto.toEntity();
        final String[] existingImageUrl = { null };

        if (item.getId() != null) {
            bookService.getBookById(item.getId()).ifPresent(existingItem -> {
                existingImageUrl[0] = existingItem.getImageUrl();
                item.setCreatedAt(existingItem.getCreatedAt());
                if (item.getStatus() == null || item.getStatus().isEmpty()) {
                    item.setStatus(existingItem.getStatus());
                }
                if (item.getImageUrl() == null || item.getImageUrl().isBlank()) {
                    item.setImageUrl(existingItem.getImageUrl());
                }
            });
        } else {
            item.setCreatedAt(LocalDateTime.now());
            item.setStatus("Active");
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String uploadedImageUrl = storeImage(imageFile, existingImageUrl[0]);
                item.setImageUrl(uploadedImageUrl);
            } catch (IOException e) {
                throw new RuntimeException("Unable to save stationery image", e);
            }
        }

        bookService.saveBook(item);
        return "redirect:/admin/stationery";
    }

    @GetMapping("/delete/{id}")
    public String deleteStationery(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var itemOptional = bookService.getBookById(id);
        if (itemOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("itemError", "Item not found");
            return "redirect:/admin/stationery";
        }

        Book item = itemOptional.get();

        if (importService.hasOpenImportForProduct(id)) {
            redirectAttributes.addFlashAttribute("itemError",
                    "Cannot delete this product because it exists in an import that is not completed yet. Please complete or cancel that import first.");
            return "redirect:/admin/stationery";
        }

        boolean hasBlockingOrder = orderDetailRepository.findByBookId(id).stream()
                .anyMatch(detail -> detail.getOrder() != null && isBlockingDeleteOrderStatus(detail.getOrder().getStatus()));
        if (hasBlockingOrder) {
            redirectAttributes.addFlashAttribute("itemError",
                    "Cannot delete this product because it exists in active orders (Pending/Confirmed/Shipping).");
            return "redirect:/admin/stationery";
        }

        boolean inAnyCart = cartItemRepository.existsByBook_Id(id);

        if (!item.isDiscontinued()) {
            item.markDiscontinued();
            bookService.saveBook(item);
            
            if (inAnyCart) {
                redirectAttributes.addFlashAttribute("itemSuccess",
                        "Product status changed to Discontinued. It is hidden from users now. After 2 minutes, click Delete again to remove only this product from all carts.");
            } else {
                redirectAttributes.addFlashAttribute("itemSuccess",
                        "Product status changed to Discontinued. It is hidden from users and can only be restored by setting Status=Active in database.");
            }
            return "redirect:/admin/stationery";
        }

        if (item.isCartCleanupWindowActive()) {
            redirectAttributes.addFlashAttribute("itemError",
                    "Product is in carts. Wait until the 2-minute window ends, then click Delete again to remove only this product from carts.");
            return "redirect:/admin/stationery";
        }

        if (inAnyCart) {
            long removedRows = cartItemRepository.deleteByBook_Id(id);
            redirectAttributes.addFlashAttribute("itemSuccess",
                    "Product remains Discontinued. Removed from " + removedRows + " cart line(s).");
            return "redirect:/admin/stationery";
        }

        item.setStatus("Deleted");
        bookService.saveBook(item);
        redirectAttributes.addFlashAttribute("itemSuccess", "Item has been hidden from admin list successfully.");
        return "redirect:/admin/stationery";
    }

    private boolean isBlockingDeleteOrderStatus(String status) {
        String normalized = normalizeOrderStatus(status);
        return "pending".equals(normalized) || "confirmed".equals(normalized) || "shipping".equals(normalized);
    }

    private String normalizeOrderStatus(String status) {
        if (status == null) return "";
        String normalized = Normalizer.normalize(status, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        if (normalized.contains("pending") || normalized.contains("cho xu ly")) return "pending";
        if (normalized.contains("confirmed") || normalized.contains("xac nhan")) return "confirmed";
        if (normalized.contains("shipping") || normalized.contains("dang giao")) return "shipping";
        return normalized;
    }

    @GetMapping("/{id}")
    public String viewStationery(@PathVariable Long id, Model model) {
        bookService.getBookById(id).ifPresent(item -> model.addAttribute("item", BookDTO.fromEntity(item)));
        model.addAttribute("activePage", "stationery");
        return "admin/stationery/detail-stationery";
    }

    private String storeImage(MultipartFile imageFile, String oldImageUrl) throws IOException {
        Path uploadDir = Paths.get("images", "books").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String originalName = imageFile.getOriginalFilename() != null ? imageFile.getOriginalFilename() : "item-image";
        String fileName = UUID.randomUUID() + extractExtension(originalName);

        Path targetPath = uploadDir.resolve(fileName).normalize();
        Files.copy(imageFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        if (oldImageUrl != null && oldImageUrl.startsWith("/images/books/")) {
            Path oldPath = Paths.get("images", "books", oldImageUrl.substring("/images/books/".length())).toAbsolutePath().normalize();
            Files.deleteIfExists(oldPath);
        }
        return "/images/books/" + fileName;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex < 0) ? ".jpg" : fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
    }
}

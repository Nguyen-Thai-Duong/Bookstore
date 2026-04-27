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

    @GetMapping
    public String listStationery(@RequestParam(required = false) String name,
                                @RequestParam(required = false) String brand,
                                Model model) {
        // Chỉ lấy những item chưa bị xóa (status khác 'Deleted')
        List<Book> stationeryItems = bookRepository.findByProductType(2L).stream()
                .filter(item -> !"Deleted".equals(item.getStatus()))
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

        // Kiểm tra lịch sử đơn hàng (OrderDetail)
        if (orderDetailRepository.existsByBook_Id(id)) {
            redirectAttributes.addFlashAttribute("itemError",
                    "Cannot delete this item because it exists in order history.");
            return "redirect:/admin/stationery";
        }

        // Kiểm tra lịch sử nhập hàng (ImportDetail)
        if (importDetailRepository.existsByProduct_Id(id)) {
            redirectAttributes.addFlashAttribute("itemError",
                    "Cannot delete this item because it exists in import history.");
            return "redirect:/admin/stationery";
        }

        // Bước 1: Nếu chưa Discontinued, thực hiện đánh dấu Discontinued (ẩn đi với khách hàng)
        if (!item.isDiscontinued()) {
            item.markDiscontinued();
            bookService.saveBook(item);
            redirectAttributes.addFlashAttribute("itemSuccess",
                    "Item hidden from store successfully. This item will be removed from all carts after 2 minutes.");
            return "redirect:/admin/stationery";
        }

        // Bước 2: Kiểm tra cửa sổ 2 phút để dọn dẹp giỏ hàng
        if (item.isCartCleanupWindowActive()) {
            redirectAttributes.addFlashAttribute("itemError",
                    "This item is still in the 2-minute cart cleanup window. Please try again after the timer ends.");
            return "redirect:/admin/stationery";
        }

        // Bước 3: Kiểm tra giỏ hàng (Cart Items)
        if (cartItemRepository.existsByBook_Id(id)) {
            redirectAttributes.addFlashAttribute("itemError",
                    "Item is still present in some carts. Please wait for cleanup to finish and try again.");
            return "redirect:/admin/stationery";
        }

        // Bước 4: Xóa mềm (Đổi trạng thái thành 'Deleted' thay vì xóa khỏi database)
        item.setStatus("Deleted");
        bookService.saveBook(item);
        redirectAttributes.addFlashAttribute("itemSuccess", "Item has been hidden from admin list successfully (Soft Deleted).");
        return "redirect:/admin/stationery";
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

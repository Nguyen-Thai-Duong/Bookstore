package com.bookstore.controller;

import com.bookstore.dto.BookDTO;
import com.bookstore.dto.CategoryDTO;
import com.bookstore.model.Book;
import com.bookstore.repository.BookRepository;
import com.bookstore.service.BookService;
import com.bookstore.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    @GetMapping
    public String listStationery(@RequestParam(required = false) String name,
                                @RequestParam(required = false) String brand,
                                Model model) {
        List<Book> stationeryItems = bookService.getProductsByProductType(2L);

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
        return "admin/stationery/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        BookDTO dto = new BookDTO();
        model.addAttribute("item", dto);
        model.addAttribute("categories", 
            categoryService.getCategoriesByProductType(2L).stream()
                .map(CategoryDTO::fromEntity)
                .toList());
        return "admin/stationery/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        bookService.getBookById(id).ifPresent(item -> {
            model.addAttribute("item", BookDTO.fromEntity(item));
            model.addAttribute("categories", 
                categoryService.getCategoriesByProductType(2L).stream()
                    .map(CategoryDTO::fromEntity)
                    .toList());
        });
        return "admin/stationery/form";
    }

    @PostMapping("/save")
    public String saveStationery(@ModelAttribute("item") BookDTO itemDto,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        Book item = itemDto.toEntity();
        final String[] existingImageUrl = { null };

        if (item.getId() != null) {
            bookService.getBookById(item.getId()).ifPresent(existingItem -> {
                existingImageUrl[0] = existingItem.getImageUrl();
                if (item.getCreatedAt() == null) {
                    item.setCreatedAt(existingItem.getCreatedAt());
                }
                if (item.getImageUrl() == null || item.getImageUrl().isBlank()) {
                    item.setImageUrl(existingItem.getImageUrl());
                }
            });
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
    public String deleteStationery(@PathVariable Long id) {
        bookService.deleteBook(id);
        return "redirect:/admin/stationery";
    }

    @GetMapping("/{id}")
    public String viewStationery(@PathVariable Long id, Model model) {
        bookService.getBookById(id).ifPresent(item -> model.addAttribute("item", BookDTO.fromEntity(item)));
        return "admin/stationery/view";
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
